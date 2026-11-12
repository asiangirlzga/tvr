package com.example.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.tvremote.data.TvType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredTv(val name: String, val host: String, val type: TvType, val port: Int) {
    val subtitle: String get() = if (type == TvType.CLOUD_TV) "Cloud TV · $host" else host
}

/**
 * Finds TVs on the local network via mDNS:
 *  - Android TV / Google TV: `_androidtvremote2._tcp`
 *  - CloudWalker Cloud TV:   `_cloudwalker._tcp` (the announcement includes the port to talk to)
 */
@Suppress("DEPRECATION")
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val lock = Any()
    private val pending = ArrayDeque<Pair<NsdServiceInfo, TvType>>()
    private var resolving = false
    private val listeners = mutableMapOf<TvType, NsdManager.DiscoveryListener>()

    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs.asStateFlow()

    fun start() {
        for ((type, serviceType) in SERVICES) startOne(type, serviceType)
    }

    private fun startOne(type: TvType, serviceType: String) {
        synchronized(lock) {
            if (listeners.containsKey(type)) return
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(lock) { listeners.remove(type) }
                }

                override fun onServiceFound(info: NsdServiceInfo) {
                    synchronized(lock) { pending.addLast(info to type) }
                    resolveNext()
                }

                override fun onServiceLost(info: NsdServiceInfo) {
                    _tvs.update { list -> list.filterNot { it.name == info.serviceName && it.type == type } }
                }
            }
            listeners[type] = l
            runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, l) }
                .onFailure { listeners.remove(type) }
        }
    }

    fun stop() {
        synchronized(lock) {
            listeners.values.forEach { l -> runCatching { nsd.stopServiceDiscovery(l) } }
            listeners.clear()
            pending.clear()
            resolving = false
        }
    }

    // NsdManager can only resolve one service at a time, so resolve them in a queue.
    private fun resolveNext() {
        val (info, type) = synchronized(lock) {
            if (resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true
            next
        }
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = resolved()

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        val tv = DiscoveredTv(serviceInfo.serviceName, host, type, serviceInfo.port)
                        _tvs.update { list -> list.filterNot { it.host == host && it.type == type } + tv }
                    }
                    resolved()
                }
            })
        }.onFailure { resolved() }
    }

    private fun resolved() {
        synchronized(lock) { resolving = false }
        resolveNext()
    }

    private companion object {
        val SERVICES = listOf(
            TvType.ANDROID_TV to "_androidtvremote2._tcp.",
            TvType.CLOUD_TV to "_cloudwalker._tcp.",
        )
    }
}
