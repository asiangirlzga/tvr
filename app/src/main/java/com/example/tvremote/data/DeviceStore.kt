package com.example.tvremote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class TvType { ANDROID_TV, CLOUD_TV }

/**
 * A saved TV. [fingerprint] is the pinned certificate (Android TV / Google TV only).
 * [port] is the web-API port a Cloud TV advertised (Cloud TV only).
 */
data class PairedTv(
    val host: String,
    val name: String,
    val fingerprint: String = "",
    val type: TvType = TvType.ANDROID_TV,
    val port: Int = 0,
) {
    val subtitle: String get() = if (type == TvType.CLOUD_TV) "Cloud TV · $host" else host
}

/** Remembers paired TVs and small settings. */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv_devices", Context.MODE_PRIVATE)

    fun all(): List<PairedTv> = try {
        val arr = JSONArray(prefs.getString(KEY_DEVICES, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PairedTv(
                host = o.getString("host"),
                name = o.getString("name"),
                fingerprint = o.optString("fp", ""),
                type = runCatching { TvType.valueOf(o.optString("type", TvType.ANDROID_TV.name)) }
                    .getOrDefault(TvType.ANDROID_TV),
                port = o.optInt("port", 0),
            )
        }
    } catch (e: JSONException) {
        emptyList()
    }

    /** Adds or replaces a TV. Matching by name lets a TV keep its pairing when its IP changes. */
    fun save(tv: PairedTv) {
        write(all().filterNot { it.host == tv.host || it.name == tv.name } + tv)
    }

    fun remove(host: String) {
        write(all().filterNot { it.host == host })
        if (lastHost == host) lastHost = null
    }

    var lastHost: String?
        get() = prefs.getString(KEY_LAST_HOST, null)
        set(value) = prefs.edit().putString(KEY_LAST_HOST, value).apply()

    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, "en-IN") ?: "en-IN"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Cloud TV: send button names (DPAD_UP) instead of Android key numbers (19). */
    var cloudKeyNames: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_KEY_NAMES, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_KEY_NAMES, value).apply()

    private fun write(list: List<PairedTv>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("host", it.host).put("name", it.name).put("fp", it.fingerprint)
                    .put("type", it.type.name).put("port", it.port),
            )
        }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_LANGUAGE = "language"
        const val KEY_CLOUD_KEY_NAMES = "cloud_key_names"
    }
}
