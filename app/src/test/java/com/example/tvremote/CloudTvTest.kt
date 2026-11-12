package com.example.tvremote

import com.example.tvremote.protocol.CloudTvClient
import com.example.tvremote.protocol.ConnectionState
import com.example.tvremote.protocol.MiniHttp
import com.example.tvremote.protocol.TvAppInfo
import com.example.tvremote.protocol.TvAppList
import com.example.tvremote.protocol.TvKey
import com.example.tvremote.voice.TvAction
import com.example.tvremote.voice.VoiceCommandParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** A pretend Cloud TV on localhost: answers each request line with whatever [respond] returns. */
private class FakeTv(private val respond: (String) -> Pair<Int, String>) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    val requests = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) {
                    s.use {
                        val reader = it.getInputStream().bufferedReader()
                        val line = reader.readLine() ?: return@use
                        while (reader.readLine()?.isNotEmpty() == true) { /* skip headers */ }
                        val path = line.split(' ')[1]
                        requests += path
                        val (status, body) = respond(path)
                        it.getOutputStream().write(
                            "HTTP/1.1 $status X\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                                .toByteArray(),
                        )
                    }
                }
            }
        }
    }

    override fun close() = server.close()
}

class CloudTvTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @After fun cleanUp() = closeables.forEach { runCatching { it.close() } }

    private fun fake(respond: (String) -> Pair<Int, String>) = FakeTv(respond).also { closeables += it }

    private fun client(tv: FakeTv, forceNames: Boolean = false) =
        CloudTvClient("127.0.0.1", tv.port) { forceNames }.also { c -> closeables += AutoCloseable { c.close() } }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting")
            Thread.sleep(20)
        }
    }

    @Test fun miniHttpReadsBody() {
        val tv = fake { 200 to """{"ok":true}""" }
        val r = MiniHttp.get("127.0.0.1", tv.port, "/tvInfo")
        assertEquals(200, r.status)
        assertEquals("""{"ok":true}""", r.body)
    }

    @Test fun connectsAndSendsKeyNumbers() {
        val tv = fake { 200 to "ok" }
        val c = client(tv)
        c.connect()
        waitFor { c.state.value == ConnectionState.Ready }
        c.sendKey(TvKey.DPAD_UP)
        waitFor { "/input/?keycode=19" in tv.requests }
        assertEquals("/tvInfo", tv.requests.first())
    }

    @Test fun fallsBackToKeyNamesWhenNumbersRejected() {
        val tv = fake { path -> if (path.startsWith("/input/?keycode=DPAD")) 200 to "ok" else if (path.startsWith("/input")) 400 to "bad" else 200 to "ok" }
        val c = client(tv)
        c.connect()
        waitFor { c.state.value == ConnectionState.Ready }
        c.sendKey(TvKey.DPAD_UP)
        waitFor { "/input/?keycode=DPAD_UP" in tv.requests }
        assertTrue("/input/?keycode=19" in tv.requests)
        // Once names work, numbers aren't tried again.
        val before = tv.requests.count { it == "/input/?keycode=19" }
        c.sendKey(TvKey.DPAD_DOWN)
        waitFor { "/input/?keycode=DPAD_DOWN" in tv.requests }
        assertEquals(before, tv.requests.count { it.endsWith("=19") })
        assertTrue("/input/?keycode=20" !in tv.requests)
    }

    @Test fun forcedKeyNames() {
        val tv = fake { 200 to "ok" }
        val c = client(tv, forceNames = true)
        c.connect()
        waitFor { c.state.value == ConnectionState.Ready }
        c.sendKey(TvKey.VOLUME_UP)
        waitFor { "/input/?keycode=VOLUME_UP" in tv.requests }
    }

    @Test fun launchesAppFoundByNameInTvAppList() {
        val apps = """[{"appName":"Netflix","packageName":"com.netflix.ninja"},{"appName":"YouTube","packageName":"com.google.android.youtube.tv"}]"""
        val tv = fake { path -> if (path == "/apps") 200 to apps else 200 to "ok" }
        val c = client(tv)
        c.connect()
        waitFor { c.state.value == ConnectionState.Ready }
        c.launchApp("market://launch?id=other.package", "Netflix")
        waitFor { "/play?packageName=com.netflix.ninja" in tv.requests }
    }

    @Test fun launchesAppFromLinkWhenTvHasNoAppList() {
        val tv = fake { path -> if (path == "/apps") 404 to "" else 200 to "ok" }
        val c = client(tv)
        c.connect()
        waitFor { c.state.value == ConnectionState.Ready }
        c.launchApp("market://launch?id=com.spotify.tv.android", "Spotify")
        waitFor { "/play?packageName=com.spotify.tv.android" in tv.requests }
    }

    @Test fun failsWhenTvReportsAnError() {
        val tv = fake { 500 to "boom" }
        val c = client(tv)
        c.connect()
        waitFor { c.state.value is ConnectionState.Failed }
        assertTrue((c.state.value as ConnectionState.Failed).message.contains("500"))
    }

    @Test fun failsWhenNothingIsListening() {
        val port = ServerSocket(0).use { it.localPort }
        val c = CloudTvClient("127.0.0.1", port) { false }.also { closeables += AutoCloseable { it.close() } }
        c.connect()
        waitFor { c.state.value is ConnectionState.Failed }
    }

    @Test fun keyNames() {
        assertEquals("DPAD_UP", TvKey.nameOf(TvKey.DPAD_UP))
        assertEquals("VOLUME_MUTE", TvKey.nameOf(TvKey.VOLUME_MUTE))
        assertEquals("KEYCODE_5", TvKey.nameOf(TvKey.digit(5)))
        assertEquals("999", TvKey.nameOf(999))
    }

    @Test fun parsesAppListShapes() {
        val array = TvAppList.parse("""[{"appName":"YouTube","packageName":"com.google.android.youtube.tv"}]""")
        assertEquals(listOf(TvAppInfo("YouTube", "com.google.android.youtube.tv")), array)

        val wrapped = TvAppList.parse("""{"status":"ok","data":{"apps":[{"name":"Prime Video","packageName":"com.amazon.amazonvideo.livingroom"}]}}""")
        assertEquals(listOf(TvAppInfo("Prime Video", "com.amazon.amazonvideo.livingroom")), wrapped)

        assertEquals(emptyList<TvAppInfo>(), TvAppList.parse("not json"))
        assertEquals(emptyList<TvAppInfo>(), TvAppList.parse(""))
    }

    @Test fun findsAppByLooseName() {
        val apps = listOf(
            TvAppInfo("Netflix", "com.netflix.ninja"),
            TvAppInfo("Prime Video", "com.amazon.amazonvideo.livingroom"),
            TvAppInfo("YouTube", "com.google.android.youtube.tv"),
        )
        assertEquals("com.netflix.ninja", TvAppList.findPackage("netflix", apps))
        assertEquals("com.amazon.amazonvideo.livingroom", TvAppList.findPackage("prime video", apps))
        assertEquals("com.google.android.youtube.tv", TvAppList.findPackage("YouTube", apps))
        assertNull(TvAppList.findPackage("hotstar", apps))
    }

    @Test fun voiceOpenAnyAppByName() {
        val action = VoiceCommandParser.parse("open chrome") as TvAction.OpenByName
        assertEquals("chrome", action.name)
        assertEquals("hot star", (VoiceCommandParser.parse("launch the hot star app") as TvAction.OpenByName).name)
        // Built-in apps still resolve to their shortcut, and button words still win.
        assertTrue(VoiceCommandParser.parse("open netflix") is TvAction.OpenLink)
        assertTrue(VoiceCommandParser.parse("open menu") is TvAction.Key)
        assertNull(VoiceCommandParser.parse("open"))
    }
}
