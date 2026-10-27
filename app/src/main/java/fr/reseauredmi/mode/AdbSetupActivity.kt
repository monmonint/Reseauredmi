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
import io.github.muntashirakon.adb.android.AdbMdns

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
        binding.findPairButton.setOnClickListener { discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, pairing = true) }
        binding.findConnectButton.setOnClickListener { discover(AdbMdns.SERVICE_TYPE_TLS_CONNECT, pairing = false) }
        binding.pairButton.setOnClickListener { startWireless() }
        binding.pcButton.setOnClickListener { startPc() }
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun startWireless() {
        if (busy) return
        val host = binding.hostInput.text?.toString().orEmpty()
        val pairPort = binding.pairPortInput.text?.toString()?.toIntOrNull()
        val code = binding.codeInput.text?.toString().orEmpty()
        val connectPort = binding.connectPortInput.text?.toString()?.toIntOrNull()
        setBusy(true)
        log("Préparation…")
        AppExecutors.io.execute {
            val result = AdbMode.prepareWireless(this, host, pairPort, code, connectPort, ::log)
            runOnUiThread {
                setBusy(false)
                if (result is AdbMode.SetupResult.Failed) log(result.message)
            }
        }
    }

    private fun startPc() {
        if (busy) return
        setBusy(true)
        log("Vérification du port local…")
        AppExecutors.io.execute {
            val result = AdbMode.prepareFromPc(this, ::log)
            runOnUiThread {
                setBusy(false)
                if (result is AdbMode.SetupResult.Failed) log(result.message)
            }
        }
    }

    private fun discover(serviceType: String, pairing: Boolean) {
        if (busy) return
        setBusy(true)
        log(if (pairing) "Recherche du port d’appairage…" else "Recherche du port de connexion…")
        AppExecutors.io.execute {
            val found = try {
                AdbDiscovery.find(this, serviceType, 4_000)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                setBusy(false)
                if (found == null) {
                    log("Aucun port trouvé. Recopiez l’adresse affichée dans les options développeur.")
                    return@runOnUiThread
                }
                binding.hostInput.setText(found.host)
                if (pairing) {
                    binding.pairPortInput.setText(found.port.toString())
                } else {
                    binding.connectPortInput.setText(found.port.toString())
                }
                log("Trouvé ${found.host}:${found.port}.")
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
        binding.findPairButton.isEnabled = !value
        binding.findConnectButton.isEnabled = !value
    }

    private fun log(line: String) {
        runOnUiThread {
            binding.logText.append(line.trim() + "\n")
        }
    }
}
