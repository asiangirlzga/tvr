package com.example.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredTv(val name: String, val host: String)

/** Finds Android TV / Google TV devices on the local network via mDNS (_androidtvremote2._tcp). */
@Suppress("DEPRECATION")
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val lock = Any()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs.asStateFlow()

    fun start() {
        synchronized(lock) {
            if (listener != null) return
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(lock) { listener = null }
                }

                override fun onServiceFound(info: NsdServiceInfo) {
                    synchronized(lock) { pending.addLast(info) }
                    resolveNext()
                }

                override fun onServiceLost(info: NsdServiceInfo) {
                    _tvs.update { list -> list.filterNot { it.name == info.serviceName } }
                }
            }
            listener = l
            runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
                .onFailure { listener = null }
        }
    }

    fun stop() {
        synchronized(lock) {
            listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
            listener = null
            pending.clear()
            resolving = false
        }
    }

    // NsdManager can only resolve one service at a time, so resolve them in a queue.
    private fun resolveNext() {
        val info = synchronized(lock) {
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
                        val tv = DiscoveredTv(serviceInfo.serviceName, host)
                        _tvs.update { list -> list.filterNot { it.host == host } + tv }
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
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
