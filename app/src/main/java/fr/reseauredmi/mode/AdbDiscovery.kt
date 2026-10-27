package fr.reseauredmi.mode

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cherche les deux services du débogage sans fil sur le téléphone lui-même.
 * Le port d’appairage n’est annoncé que tant que la fenêtre du code est ouverte.
 * Le port de connexion l’est tant que « Débogage sans fil » est activé.
 */
object AdbDiscovery {
    data class Endpoint(val host: String, val port: Int)
    data class Both(val pairing: Endpoint?, val connect: Endpoint?)

    fun findBoth(context: Context, timeoutMs: Long): Both {
        check(Looper.myLooper() != Looper.getMainLooper()) { "recherche mDNS hors du fil principal" }
        val app = context.applicationContext
        val nsd = app.getSystemService(NsdManager::class.java) ?: return Both(null, null)
        val wifi = app.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("mode-adb-mdns")
        lock?.setReferenceCounted(false)
        runCatching { lock?.acquire() }
        return try {
            Search(nsd).await(timeoutMs)
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    private class Search(private val nsd: NsdManager) {
        private val pairing = AtomicReference<Endpoint?>(null)
        private val connect = AtomicReference<Endpoint?>(null)
        private val ready = CountDownLatch(1)
        private val resolving = AtomicBoolean(false)
        private val queue = ConcurrentLinkedQueue<NsdServiceInfo>()
        private val discoveries = mutableListOf<Discovery>()

        fun await(timeoutMs: Long): Both {
            discoveries += Discovery(AdbServiceTypes.PAIRING)
            discoveries += Discovery(AdbServiceTypes.CONNECT)
            discoveries.forEach { it.start() }
            try {
                ready.await(timeoutMs, TimeUnit.MILLISECONDS)
            } finally {
                discoveries.forEach { it.stop() }
            }
            return Both(pairing.get(), connect.get())
        }

        private fun accept(info: NsdServiceInfo) {
            val role = AdbServiceTypes.role(info.serviceType) ?: return
            if (info.port !in 1..65535) return
            val endpoint = Endpoint(AdbServiceTypes.host(addressesOf(info)), info.port)
            val target = if (role == AdbServiceTypes.Role.PAIRING) pairing else connect
            target.compareAndSet(null, endpoint)
            if (pairing.get() != null && connect.get() != null) ready.countDown()
        }

        private fun enqueue(info: NsdServiceInfo) {
            queue.add(info)
            drain()
        }

        private fun drain() {
            if (!resolving.compareAndSet(false, true)) return
            val next = queue.poll()
            if (next == null) {
                resolving.set(false)
                if (queue.isNotEmpty()) drain()
                return
            }
            try {
                @Suppress("DEPRECATION")
                nsd.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        accept(resolved)
                        resolving.set(false)
                        drain()
                    }

                    override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                        resolving.set(false)
                        drain()
                    }
                })
            } catch (_: Exception) {
                resolving.set(false)
                drain()
            }
        }

        private fun addressesOf(info: NsdServiceInfo): List<String?> {
            val found = mutableListOf<String?>()
            if (Build.VERSION.SDK_INT >= 34) {
                for (address in info.hostAddresses) found += address.hostAddress
            }
            @Suppress("DEPRECATION")
            found += info.host?.hostAddress
            return found
        }

        private inner class Discovery(private val type: String) : NsdManager.DiscoveryListener {
            private val started = AtomicBoolean(false)
            private val stopRequested = AtomicBoolean(false)

            fun start() {
                try {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, this)
                } catch (_: Exception) {
                    started.set(false)
                }
            }

            fun stop() {
                stopRequested.set(true)
                if (started.compareAndSet(true, false)) {
                    runCatching { nsd.stopServiceDiscovery(this) }
                }
            }

            override fun onDiscoveryStarted(serviceType: String) {
                started.set(true)
                if (stopRequested.get()) stop()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                started.set(false)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                started.set(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                started.set(false)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (AdbServiceTypes.role(serviceInfo.serviceType) != null) enqueue(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }
    }
}
