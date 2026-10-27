package fr.reseauredmi.mode

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import fr.reseauredmi.mode.databinding.ActivityAdbSetupBinding

class AdbSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdbSetupBinding
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAdbSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extra = (20 * resources.displayMetrics.density).toInt()
            view.setPadding(extra + bars.left, extra + bars.top, extra + bars.right, extra + bars.bottom)
            insets
        }
        binding.developerButton.setOnClickListener { openDeveloperSettings() }
        binding.findBothButton.setOnClickListener { searchOnly() }
        binding.manualToggle.setOnClickListener { toggleManual() }
        binding.pairButton.setOnClickListener { startWireless() }
        binding.pcButton.setOnClickListener { startPc() }
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun startWireless() {
        if (busy) return
        val code = binding.codeInput.text?.toString().orEmpty().trim()
        if (!code.matches(SIX_DIGITS)) {
            log("Entrez le code à 6 chiffres de la fenêtre « Associer avec l’appareil ». Il expire vite : laissez cette fenêtre ouverte.")
            return
        }
        setBusy(true)
        log("Recherche des deux ports. Laissez la fenêtre du code ouverte…")
        AppExecutors.io.execute {
            val found = discover()
            val manualPair = portOf(binding.pairPortInput.text?.toString())
            val manualConnect = portOf(binding.connectPortInput.text?.toString())
            val manualHost = binding.hostInput.text?.toString().orEmpty()
            val pairPort = found.pairing?.port ?: manualPair
            val connectPort = found.connect?.port ?: manualConnect
            val host = found.pairing?.host ?: found.connect?.host ?: manualHost
            val automatic = found.pairing != null && found.connect != null
            runOnUiThread {
                showFound(found)
                fill(found)
                if (automatic || found.pairing != null || found.connect != null) {
                    binding.manualGroup.isVisible = true
                    binding.manualToggle.text = getString(R.string.adb_manual_hide)
                }
            }
            if (pairPort == null || connectPort == null || pairPort == connectPort) {
                runOnUiThread {
                    setBusy(false)
                    log(missing(found, pairPort, connectPort))
                }
                return@execute
            }
            log(
                if (automatic) {
                    "Ports trouvés automatiquement. Une saisie manuelle différente est ignorée."
                } else {
                    "Recherche incomplète : le port manquant vient de la saisie manuelle."
                },
            )
            log("Port d’appairage (fenêtre du code) : $pairPort. Port de connexion (écran Débogage sans fil) : $connectPort.")
            val result = AdbMode.prepareWireless(
                applicationContext,
                host.ifBlank { PhoneCommands.LOOPBACK },
                pairPort,
                code,
                connectPort,
                ::log,
            )
            runOnUiThread {
                setBusy(false)
                if (result is AdbMode.SetupResult.Failed) log(result.message)
            }
        }
    }

    private fun searchOnly() {
        if (busy) return
        setBusy(true)
        log("Recherche des deux ports. La fenêtre « Associer avec l’appareil » doit rester ouverte pour le port d’appairage.")
        AppExecutors.io.execute {
            val found = discover()
            runOnUiThread {
                setBusy(false)
                showFound(found)
                fill(found)
                binding.manualGroup.isVisible = true
                binding.manualToggle.text = getString(R.string.adb_manual_hide)
                if (found.pairing == null || found.connect == null) log(missing(found, found.pairing?.port, found.connect?.port))
            }
        }
    }

    private fun discover(): AdbDiscovery.Both {
        return try {
            AdbDiscovery.findBoth(applicationContext, 6_000)
        } catch (error: Exception) {
            log("Recherche impossible : ${error.message ?: error.javaClass.simpleName}")
            AdbDiscovery.Both(null, null)
        }
    }

    private fun showFound(found: AdbDiscovery.Both) {
        binding.discoveredText.text =
            "Port d’appairage (fenêtre « Associer avec l’appareil », à côté du code) : ${found.pairing?.port ?: "pas trouvé"}.\n" +
                "Port de connexion (écran « Débogage sans fil », sous l’interrupteur) : ${found.connect?.port ?: "pas trouvé"}."
    }

    private fun fill(found: AdbDiscovery.Both) {
        found.pairing?.let { binding.pairPortInput.setText(it.port.toString()) }
        found.connect?.let { binding.connectPortInput.setText(it.port.toString()) }
        val host = found.pairing?.host ?: found.connect?.host
        if (!host.isNullOrBlank() && host != PhoneCommands.LOOPBACK) binding.hostInput.setText(host)
    }

    private fun missing(found: AdbDiscovery.Both, pairPort: Int?, connectPort: Int?): String {
        return when {
            pairPort != null && pairPort == connectPort ->
                "Les deux champs ont le même nombre. Le port d’appairage est celui de la fenêtre du code. Le port de connexion est celui de l’écran Débogage sans fil."
            found.pairing == null && pairPort == null ->
                "Port d’appairage introuvable. Laissez ouverte la fenêtre « Associer avec l’appareil » (celle du code). Le port est le nombre de sa ligne « Adresse IP & Port », pas celui de l’écran Débogage sans fil."
            found.connect == null && connectPort == null ->
                "Port de connexion introuvable. Lisez-le sur l’écran principal « Débogage sans fil », sous l’interrupteur. Ne reprenez pas le port de la fenêtre du code."
            else ->
                "Il manque un port. La fenêtre du code donne le port d’appairage. L’écran Débogage sans fil donne le port de connexion."
        }
    }

    private fun toggleManual() {
        val show = !binding.manualGroup.isVisible
        binding.manualGroup.isVisible = show
        binding.manualToggle.text = getString(if (show) R.string.adb_manual_hide else R.string.adb_manual_show)
    }

    private fun startPc() {
        if (busy) return
        setBusy(true)
        log("Vérification du port local…")
        AppExecutors.io.execute {
            val result = AdbMode.prepareFromPc(applicationContext, ::log)
            runOnUiThread {
                setBusy(false)
                if (result is AdbMode.SetupResult.Failed) log(result.message)
            }
        }
    }

    private fun openDeveloperSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            log("Options développeur introuvables. Appuyez sept fois sur la version MIUI, puis réessayez.")
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.isVisible = value
        binding.pairButton.isEnabled = !value
        binding.pcButton.isEnabled = !value
        binding.findBothButton.isEnabled = !value
    }

    private fun log(line: String) {
        runOnUiThread {
            binding.logText.append(line.trim() + "\n")
        }
    }

    private fun portOf(raw: String?): Int? = raw?.toIntOrNull()?.takeIf { AdbEndpoint.port(it) }

    private companion object {
        val SIX_DIGITS = Regex("""\d{6}""")
    }
}
