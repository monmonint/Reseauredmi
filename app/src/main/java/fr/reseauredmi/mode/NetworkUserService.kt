package fr.reseauredmi.mode

import android.content.Context
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tourne dans un processus Shizuku (uid shell 2000, ou 0 si root).
 * Ce processus n'est pas soumis aux restrictions d'API cachées, et le uid
 * shell possède MODIFY_PHONE_STATE : les appels ITelephony sont acceptés.
 */
@Keep
class NetworkUserService() : INetworkModeService.Stub() {

    @Suppress("unused")
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun apply(
        subId: Int,
        slotIndex: Int,
        rilMode: Int,
        bitmask: Long,
        packageName: String,
    ): String {
        return probe(subId, slotIndex, packageName, mutate = true, rilMode = rilMode, bitmask = bitmask)
    }

    override fun read(subId: Int, slotIndex: Int, packageName: String): String {
        return probe(subId, slotIndex, packageName, mutate = false, rilMode = 0, bitmask = 0L)
    }

    private fun probe(
        subId: Int,
        slotIndex: Int,
        packageName: String,
        mutate: Boolean,
        rilMode: Int,
        bitmask: Long,
    ): String {
        val fields = mutableListOf<String>()
        val log = StringBuilder()
        fun field(key: String, value: String) {
            fields += "$key=${value.replace('\n', ' ').trim()}"
        }
        fun note(line: String) {
            log.appendLine(line)
        }

        field("uid", Process.myUid().toString())
        note("uid=${Process.myUid()} sub=$subId slot=$slotIndex mutate=$mutate")

        if (mutate) {
            val known = (rilMode == ForcedMode.WCDMA.ril && bitmask == ForcedMode.WCDMA.mask) ||
                (rilMode == ForcedMode.LTE.ril && bitmask == ForcedMode.LTE.mask)
            if (!known) {
                field("error", "mode")
                note("mode refusé ril=$rilMode mask=$bitmask")
                return format(fields, log)
            }
        }

        grantReadPhoneState(packageName, ::note)

        val telephony = try {
            telephony()
        } catch (error: Throwable) {
            note("ITelephony indisponible : ${root(error)}")
            null
        }

        if (telephony != null) {
            note(
                "méthodes: " + telephony.javaClass.methods
                    .map { it.name }
                    .distinct()
                    .filter { name ->
                        name.contains("Network", ignoreCase = true) ||
                            name.contains("Preferred", ignoreCase = true) ||
                            name.contains("Allowed", ignoreCase = true)
                    }
                    .sorted()
                    .joinToString(","),
            )
            if (mutate) {
                recordSet(
                    fields,
                    ::note,
                    "set_allowed",
                    call(telephony, "setAllowedNetworkTypesForReason", subId, REASON_USER, bitmask),
                )
                recordSet(
                    fields,
                    ::note,
                    "set_preferred",
                    call(telephony, "setPreferredNetworkType", subId, rilMode),
                )
            }
            when (val preferred = call(telephony, "getPreferredNetworkType", subId)) {
                is Call.Ok -> asInt(preferred.value)?.let { field("preferred", it.toString()) }
                is Call.Err -> note("getPreferredNetworkType : ${preferred.message}")
                Call.Missing -> note("getPreferredNetworkType absente")
            }
            when (val allowed = call(telephony, "getAllowedNetworkTypesForReason", subId, REASON_USER)) {
                is Call.Ok -> asLong(allowed.value)?.let { field("allowed", it.toString()) }
                is Call.Err -> note("getAllowedNetworkTypesForReason : ${allowed.message}")
                Call.Missing -> note("getAllowedNetworkTypesForReason absente")
            }
        }

        if (mutate) {
            val shellSet = runShell(setCommand(slotIndex, bitmask))
            val completed = shellSet.code == 0 && shellSet.text.contains("completed", ignoreCase = true)
            field("shell_set", completed.toString())
            note("cmd set exit=${shellSet.code} ${shellSet.text}")
            val wrote = writeSettings(subId, rilMode, ::note)
            field("settings_put", wrote.toString())
        }

        val shellGet = runShell(getCommand(slotIndex))
        networkLine(shellGet.text)?.let { field("shell_get", it) }
        note("cmd get exit=${shellGet.code} ${shellGet.text}")

        readSettings(subId)?.let { field("settings", it) }

        return format(fields, log)
    }

    private fun grantReadPhoneState(packageName: String, note: (String) -> Unit) {
        if (!packageName.matches(PACKAGE_NAME)) return
        val result = runShell(PhoneCommands.grantReadPhoneState(packageName))
        note("pm grant ${result.code} ${result.text}")
    }

    private fun recordSet(
        fields: MutableList<String>,
        note: (String) -> Unit,
        key: String,
        result: Call,
    ) {
        when (result) {
            is Call.Ok -> {
                val ok = result.value !is Boolean || result.value
                fields += "$key=$ok"
                note("$key -> ${result.value}")
            }
            is Call.Err -> {
                fields += "$key=false"
                note("$key erreur : ${result.message}")
            }
            Call.Missing -> note("$key absente")
        }
    }

    private fun telephony(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "phone") as? IBinder
            ?: error("service phone absent")
        val stub = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("ITelephony nul")
    }

    private fun call(target: Any, name: String, vararg args: Any?): Call {
        val types = Array(args.size) { index ->
            primitiveType(args[index]) ?: return Call.Err("argument nul")
        }
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == name && method.parameterTypes.contentEquals(types)
        } ?: return Call.Missing
        return try {
            Call.Ok(method.invoke(target, *args))
        } catch (error: InvocationTargetException) {
            Call.Err(root(error))
        } catch (error: Throwable) {
            Call.Err(root(error))
        }
    }

    private fun primitiveType(value: Any?): Class<*>? = when (value) {
        null -> null
        is Int -> Int::class.javaPrimitiveType
        is Long -> Long::class.javaPrimitiveType
        is Boolean -> Boolean::class.javaPrimitiveType
        else -> value.javaClass
    }

    private fun asInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Short -> value.toInt()
        else -> null
    }

    private fun asLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        else -> null
    }

    private fun setCommand(slotIndex: Int, bitmask: Long): Array<String> =
        PhoneCommands.setAllowed(slotIndex, bitmask)

    private fun getCommand(slotIndex: Int): Array<String> = PhoneCommands.getAllowed(slotIndex)

    private fun writeSettings(subId: Int, rilMode: Int, note: (String) -> Unit): Boolean {
        var ok = true
        for (key in PhoneCommands.settingKeys(subId)) {
            val result = runShell(PhoneCommands.putSetting(key, rilMode))
            note("settings put $key -> ${result.code} ${result.text}")
            if (result.code != 0) ok = false
        }
        return ok
    }

    private fun readSettings(subId: Int): String? {
        val key = PhoneCommands.settingKeys(subId).last()
        val result = runShell(PhoneCommands.getSetting(key))
        return result.text.lineSequence().map { it.trim() }.firstOrNull { it.toIntOrNull() != null }
    }

    private fun networkLine(text: String): String? {
        return text.lineSequence().map { it.trim() }.lastOrNull { line ->
            ModeReport.looksLikeNetworkList(line)
        }
    }

    private data class Cmd(val code: Int, val text: String)

    private fun runShell(command: Array<String>): Cmd {
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val process = ProcessBuilder(*command).start()
            val output = pool.submit<String> { slurp(process.inputStream) }
            val error = pool.submit<String> { slurp(process.errorStream) }
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Cmd(-1, "timeout")
            } else {
                val text = listOf(output.get(2, TimeUnit.SECONDS), error.get(2, TimeUnit.SECONDS))
                    .joinToString("\n")
                    .trim()
                Cmd(process.exitValue(), text.take(1500))
            }
        } catch (error: Throwable) {
            Cmd(-1, root(error))
        } finally {
            pool.shutdownNow()
        }
    }

    private fun slurp(stream: InputStream): String = stream.bufferedReader().use { it.readText() }

    private fun format(fields: List<String>, log: StringBuilder): String {
        return fields.joinToString("\n") + "\n---\n" + log.toString().take(3500)
    }

    private fun root(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current.message ?: current.javaClass.simpleName
    }

    private companion object {
        const val REASON_USER = 0
        val PACKAGE_NAME = Regex("^[a-zA-Z0-9._]+$")
    }

    private sealed class Call {
        data class Ok(val value: Any?) : Call()
        data class Err(val message: String) : Call()
        data object Missing : Call()
    }
}
