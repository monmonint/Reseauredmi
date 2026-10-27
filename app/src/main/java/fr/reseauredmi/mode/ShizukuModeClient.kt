package fr.reseauredmi.mode

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuModeClient {
    private val lock = Any()
    private var service: INetworkModeService? = null

    fun apply(context: Context, subId: Int, slotIndex: Int, mode: ForcedMode): String {
        return call(context) { binder ->
            binder.apply(subId, slotIndex, mode.ril, mode.mask, context.packageName)
        }
    }

    fun read(context: Context, subId: Int, slotIndex: Int): String {
        return call(context) { binder ->
            binder.read(subId, slotIndex, context.packageName)
        }
    }

    private fun call(context: Context, block: (INetworkModeService) -> String): String {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Appel Shizuku bloquant depuis le thread principal"
        }
        synchronized(lock) {
            val app = context.applicationContext
            val first = connect(app)
            return try {
                block(first)
            } catch (error: RemoteException) {
                service = null
                block(connect(app))
            }
        }
    }

    private fun connect(context: Context): INetworkModeService {
        val current = service
        if (current != null && current.asBinder().isBinderAlive) return current
        if (!Shizuku.pingBinder()) {
            throw IllegalStateException("Shizuku est arrêté")
        }
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null) {
                    service = INetworkModeService.Stub.asInterface(binder)
                    runCatching { binder.linkToDeath({ service = null }, 0) }
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, NetworkUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("modem")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("reseauredmi-modem")
        Shizuku.bindUserService(args, connection)
        if (!latch.await(20, TimeUnit.SECONDS) || service == null) {
            throw IllegalStateException("Le service Shizuku n’a pas répondu")
        }
        return service ?: throw IllegalStateException("Le service Shizuku n’a pas répondu")
    }
}
