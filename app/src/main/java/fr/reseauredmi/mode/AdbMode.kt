package fr.reseauredmi.mode

import android.content.Context
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException

/**
 * Bascule le réseau en ouvrant ADB, en lançant la commande, puis en refermant.
 *
 * Préparation : pendant que le débogage sans fil est actif, envoyer le service `tcpip:5555`.
 * adbd écoute ensuite sur 0.0.0.0:5555 (donc aussi sur 127.0.0.1) jusqu’au redémarrage,
 * parce que la propriété `service.adb.tcp.port` n’est pas persistante.
 * Ce chemin n’a pas été exécuté sur un Redmi dans cet environnement : MIUI peut couper
 * le démon quand le Wi-Fi se déconnecte.
 */
object AdbMode {
    sealed class SetupResult {
        data object Ready : SetupResult()
        data class Failed(val message: String) : SetupResult()
    }

    sealed class Attempt {
        data class Applied(val outcome: ApplyOutcome) : Attempt()
        data class Offline(val message: String) : Attempt()
    }

    fun prepareWireless(
        context: Context,
        hostField: String,
        pairPortField: Int?,
        codeField: String,
        connectPort: Int?,
        log: (String) -> Unit,
    ): SetupResult {
        val parsed = AdbEndpoint.parseHost(hostField)
        val host = parsed.address
        val pairPort = pairPortField ?: parsed.port
        val code = codeField.trim()
        if (!AdbEndpoint.ipv4(host)) {
            return SetupResult.Failed("Indiquez l’adresse IPv4 affichée par le débogage sans fil.")
        }
        if (pairPort == null || !AdbEndpoint.port(pairPort)) {
            return SetupResult.Failed("Indiquez le port d’appairage.")
        }
        if (!code.matches(Regex("""\d{6}"""))) {
            return SetupResult.Failed("Le code d’appairage comporte 6 chiffres. Il expire vite : ouvrez-en un nouveau.")
        }
        if (connectPort == null || !AdbEndpoint.port(connectPort)) {
            return SetupResult.Failed("Indiquez le port de connexion du débogage sans fil (pas seulement le port d’appairage).")
        }
        if (connectPort == pairPort) {
            return SetupResult.Failed("Le port de connexion et le port d’appairage sont deux nombres différents.")
        }
        val hosts = listOf(host, PhoneCommands.LOOPBACK).distinct()
        return try {
            log("Chargement de la clé de l’application…")
            AdbIdentity.get(context)
            var last: Exception? = null
            AdbSession(context).use { session ->
                var pairedHost: String? = null
                for (candidate in hosts) {
                    if (!TcpProbe.open(candidate, pairPort, 2_500)) {
                        log("Rien n’écoute sur $candidate:$pairPort.")
                        continue
                    }
                    log("Appairage sur $candidate:$pairPort…")
                    try {
                        session.pair(candidate, pairPort, code)
                        pairedHost = candidate
                        log("Appairage réussi.")
                        break
                    } catch (error: Exception) {
                        last = error
                        log(french(error))
                        break
                    }
                }
                val paired = pairedHost
                if (paired == null) {
                    return SetupResult.Failed(last?.let { french(it) } ?: "Appairage impossible. Vérifiez le code, il expire vite.")
                }
                var connected = false
                for (candidate in (listOf(paired) + hosts).distinct()) {
                    log("Connexion au débogage sans fil sur $candidate:$connectPort…")
                    try {
                        session.connect(candidate, connectPort, prompt = true, handshakeSeconds = 25)
                        connected = true
                        log("Connecté. Bascule vers le port fixe ${PhoneCommands.PORT}…")
                        break
                    } catch (error: Exception) {
                        last = error
                        session.drop()
                        log(french(error))
                    }
                }
                if (!connected) {
                    return SetupResult.Failed(last?.let { french(it) } ?: "Connexion impossible.")
                }
                session.tcpip()
            }
            log("Le démon redémarre. Vérification de ${PhoneCommands.LOOPBACK}:${PhoneCommands.PORT}…")
            Thread.sleep(1_200)
            var failure: Exception? = null
            repeat(6) { attempt ->
                try {
                    confirmLoopback(context, prompt = true, handshakeSeconds = 12)
                    log("C’est prêt. Le Wi-Fi peut être déconnecté. Au redémarrage, cette préparation est à refaire.")
                    return SetupResult.Ready
                } catch (error: Exception) {
                    failure = error
                    log("Essai ${attempt + 1} : ${french(error)}")
                    Thread.sleep(700)
                }
            }
            Prefs.setAdbLoopbackReady(context, false)
            SetupResult.Failed(
                failure?.let { french(it) }
                    ?: "Le port ${PhoneCommands.PORT} ne répond pas. Le débogage sans fil doit rester actif pendant la bascule.",
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            SetupResult.Failed("Interrompu.")
        } catch (error: Exception) {
            Prefs.setAdbLoopbackReady(context, false)
            SetupResult.Failed(french(error))
        }
    }

    fun prepareFromPc(context: Context, log: (String) -> Unit): SetupResult {
        log("Connexion à ${PhoneCommands.LOOPBACK}:${PhoneCommands.PORT}…")
        log("Si « Autoriser le débogage USB » s’affiche, cochez toujours autoriser, puis acceptez.")
        return try {
            confirmLoopback(context, prompt = true, handshakeSeconds = 40)
            log("Port local confirmé. Le Wi-Fi et l’ordinateur ne sont plus nécessaires, jusqu’au redémarrage.")
            SetupResult.Ready
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            SetupResult.Failed("Interrompu.")
        } catch (error: Exception) {
            Prefs.setAdbLoopbackReady(context, false)
            SetupResult.Failed(french(error))
        }
    }

    fun apply(context: Context, mode: ForcedMode, subId: Int, slotIndex: Int): Attempt {
        return try {
            val outcome = AdbSession(context).use { session ->
                session.connect(
                    PhoneCommands.LOOPBACK,
                    PhoneCommands.PORT,
                    prompt = false,
                    handshakeSeconds = 8,
                )
                val raw = runShell(context, session, mode, subId, slotIndex)
                val report = ModeReport.parse("via=adb\n$raw")
                val level = ModeReport.level(report, mode)
                Prefs.setAdbLoopbackReady(context, true)
                ApplyOutcome(level, mode, report.text(), needsSetup = false)
            }
            Attempt.Applied(outcome)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            offline(context, "Interrompu.")
        } catch (error: Exception) {
            offline(context, french(error))
        }
    }

    fun read(context: Context, subId: Int, slotIndex: Int): ModeReport.Report? {
        if (!Prefs.adbLoopbackReady(context)) return null
        return try {
            AdbSession(context).use { session ->
                session.connect(
                    PhoneCommands.LOOPBACK,
                    PhoneCommands.PORT,
                    prompt = false,
                    handshakeSeconds = 6,
                )
                val getOutput = session.exec(PhoneCommands.getAllowed(slotIndex))
                val settingGet = session.exec(PhoneCommands.getSetting(PhoneCommands.settingKeys(subId).last()))
                val raw = AdbReport.assemble(
                    probe = "",
                    grant = "",
                    setOutput = "",
                    settingPuts = emptyList(),
                    getOutput = getOutput,
                    settingGet = settingGet,
                )
                ModeReport.parse("via=adb\n$raw")
            }
        } catch (_: Exception) {
            Prefs.setAdbLoopbackReady(context, false)
            null
        }
    }

    private fun confirmLoopback(context: Context, prompt: Boolean, handshakeSeconds: Long) {
        AdbSession(context).use { session ->
            session.connect(PhoneCommands.LOOPBACK, PhoneCommands.PORT, prompt, handshakeSeconds)
            val probe = session.exec(PhoneCommands.probe())
            if (!AdbReport.probeOk(probe)) {
                throw AdbUnavailable("Le shell local n’a pas répondu.")
            }
            session.exec(PhoneCommands.grantReadPhoneState(context.packageName))
            Prefs.setAdbLoopbackReady(context, true)
        }
    }

    private fun runShell(
        context: Context,
        session: AdbSession,
        mode: ForcedMode,
        subId: Int,
        slotIndex: Int,
    ): String {
        val probe = session.exec(PhoneCommands.probe())
        if (!AdbReport.probeOk(probe)) throw AdbUnavailable("Le shell local n’a pas répondu.")
        val grant = session.exec(PhoneCommands.grantReadPhoneState(context.packageName))
        val setOutput = session.exec(PhoneCommands.setAllowed(slotIndex, mode.mask))
        val settingPuts = PhoneCommands.settingKeys(subId).map { key ->
            session.exec(PhoneCommands.putSetting(key, mode.ril))
        }
        val getOutput = session.exec(PhoneCommands.getAllowed(slotIndex))
        val settingGet = session.exec(PhoneCommands.getSetting(PhoneCommands.settingKeys(subId).last()))
        return AdbReport.assemble(probe, grant, setOutput, settingPuts, getOutput, settingGet)
    }

    private fun offline(context: Context, message: String): Attempt.Offline {
        Prefs.setAdbLoopbackReady(context, false)
        return Attempt.Offline(message)
    }

    private fun french(error: Throwable): String {
        if (error is AdbUnavailable) return error.message ?: "Accès local indisponible."
        var current: Throwable? = error
        while (current != null) {
            val text = current.message.orEmpty()
            when (current) {
                is AdbAuthenticationFailedException ->
                    return "La clé a été refusée. Acceptez « Autoriser le débogage USB » et cochez toujours autoriser, ou recommencez l’appairage."
                is AdbPairingRequiredException ->
                    return "Ce port demande encore un appairage. Vérifiez le code et le port d’appairage."
            }
            if (text.contains("ECONNREFUSED", true) ||
                text.contains("Connection refused", true) ||
                text.contains("failed to connect", true)
            ) {
                return "Connexion refusée. Le débogage sans fil est-il actif, ou le port ${PhoneCommands.PORT} a-t-il été ouvert ?"
            }
            if (text.contains("timed out", true) || text.contains("timeout", true)) {
                return "Délai dépassé. Si une demande d’autorisation est affichée, acceptez-la puis réessayez."
            }
            if (text.contains("handshake", true) || text.contains("ssl", true)) {
                return "Le chiffrement du débogage sans fil a échoué. Générez un nouveau code d’appairage."
            }
            current = current.cause
        }
        return error.message?.take(300) ?: error.javaClass.simpleName
    }
}
