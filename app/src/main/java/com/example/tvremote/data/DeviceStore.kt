package com.example.tvremote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class PairedTv(val host: String, val name: String, val fingerprint: String)

/** Remembers paired TVs (name, address, pinned certificate fingerprint) and small settings. */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv_devices", Context.MODE_PRIVATE)

    fun all(): List<PairedTv> = try {
        val arr = JSONArray(prefs.getString(KEY_DEVICES, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PairedTv(o.getString("host"), o.getString("name"), o.getString("fp"))
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

    private fun write(list: List<PairedTv>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("host", it.host).put("name", it.name).put("fp", it.fingerprint)) }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_LANGUAGE = "language"
    }
}
