package fr.reseauredmi.mode

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import fr.reseauredmi.mode.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val controller by lazy { NetworkModeController(this) }
    private var highlighted: ForcedMode? = null
    private var selectedSubId: Int = SimResolver.defaultSubId()
    private var busy = false

    private val monitor by lazy {
        NetworkMonitor(this, { selectedSubId }) { snapshot ->
            binding.networkHeadline.text = snapshot.headline
            binding.networkDetail.text = snapshot.detail
        }
    }

    private val phonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        renderSims()
        monitor.publishNow()
    }

    private val shizukuPermission = Shizuku.OnRequestPermissionResultListener { _, _ ->
        renderShizuku()
        refreshPreference()
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        renderShizuku()
        refreshPreference()
    }

    private val binderDead = Shizuku.OnBinderDeadListener { renderShizuku() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extra = (20 * resources.displayMetrics.density).toInt()
            view.setPadding(extra + bars.left, extra + bars.top, extra + bars.right, extra + bars.bottom)
            insets
        }

        highlighted = Prefs.mode(this)
        binding.bootCheck.isChecked = Prefs.reapplyOnBoot(this)
        binding.bootCheck.setOnCheckedChangeListener { _, checked ->
            Prefs.setReapplyOnBoot(this, checked)
        }
        binding.card3g.setOnClickListener { apply(ForcedMode.WCDMA) }
        binding.card4g.setOnClickListener { apply(ForcedMode.LTE) }
        binding.adbSetupButton.setOnClickListener {
            startActivity(Intent(this, AdbSetupActivity::class.java))
        }
        binding.shizukuAction.setOnClickListener { onShizukuAction() }
        binding.radioButton.setOnClickListener { openRadio() }
        binding.permissionButton.setOnClickListener {
            phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
        }
        binding.detailsButton.setOnClickListener {
            binding.detailsText.isVisible = !binding.detailsText.isVisible
        }

        Shizuku.addRequestPermissionResultListener(shizukuPermission)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)

        renderSelection()
        renderPreference(highlighted)
        renderAdb()
        renderShizuku()
        maybeAskPhonePermission()
    }

    override fun onStart() {
        super.onStart()
        renderSims()
        monitor.start()
        renderAdb()
        renderShizuku()
        refreshPreference()
    }

    override fun onStop() {
        monitor.stop()
        super.onStop()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermission)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        super.onDestroy()
    }

    private fun maybeAskPhonePermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        binding.permissionButton.isVisible = !granted
        if (!granted) {
            phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun renderSims() {
        val options = SimResolver.options(this)
        if (options.none { it.subId == selectedSubId }) {
            selectedSubId = options.firstOrNull()?.subId ?: SimResolver.defaultSubId()
        }
        val row = binding.simRow
        row.removeAllViews()
        val showChooser = options.size > 1
        row.isVisible = showChooser
        if (!showChooser) return
        options.forEach { option ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = option.label
                isCheckable = true
                isChecked = option.subId == selectedSubId
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    val gap = (6 * resources.displayMetrics.density).toInt()
                    marginStart = gap
                    marginEnd = gap
                }
                setOnClickListener {
                    selectedSubId = option.subId
                    renderSims()
                    monitor.publishNow()
                    refreshPreference()
                }
            }
            row.addView(button)
        }
    }

    private fun apply(mode: ForcedMode) {
        if (busy) return
        busy = true
        binding.progress.isVisible = true
        binding.statusText.text = getString(R.string.status_applying)
        val subId = selectedSubId
        AppExecutors.io.execute {
            val outcome = controller.apply(mode, subId)
            runOnUiThread {
                busy = false
                binding.progress.isVisible = false
                showOutcome(outcome)
                renderAdb()
                renderSims()
                monitor.publishNow()
            }
        }
    }

    private fun showOutcome(outcome: ApplyOutcome) {
        binding.statusText.text = when {
            outcome.needsSetup -> outcome.detail
            outcome.level == ModeReport.Level.CONFIRMED && outcome.mode == ForcedMode.WCDMA ->
                getString(R.string.status_confirmed_3g)
            outcome.level == ModeReport.Level.CONFIRMED -> getString(R.string.status_confirmed_4g)
            outcome.level == ModeReport.Level.SENT -> getString(R.string.status_sent)
            else -> getString(R.string.status_failed)
        }
        binding.detailsText.text = outcome.detail
        if (outcome.level != ModeReport.Level.FAILED) {
            highlighted = outcome.mode
            renderSelection()
            renderPreference(outcome.mode)
        } else if (outcome.needsSetup) {
            dialog(R.string.adb_needed_title, R.string.adb_needed_body) {
                startActivity(Intent(this, AdbSetupActivity::class.java))
            }
        }
    }

    private fun refreshPreference() {
        if (busy) return
        if (!Prefs.adbLoopbackReady(this) && !ShizukuStatus.ready()) return
        val subId = selectedSubId
        AppExecutors.io.execute {
            val report = controller.read(subId) ?: return@execute
            val mode = ModeReport.inferred(report)
            runOnUiThread {
                if (mode != null) {
                    highlighted = mode
                    renderSelection()
                    renderPreference(mode)
                }
                if (binding.detailsText.text.isNullOrBlank()) {
                    binding.detailsText.text = report.text()
                }
            }
        }
    }

    private fun renderSelection() {
        val density = resources.displayMetrics.density
        val width = (4 * density).toInt()
        binding.card3g.strokeWidth = if (highlighted == ForcedMode.WCDMA) width else 0
        binding.card4g.strokeWidth = if (highlighted == ForcedMode.LTE) width else 0
        binding.card3g.contentDescription = getString(
            if (highlighted == ForcedMode.WCDMA) R.string.cd_3g_on else R.string.cd_3g,
        )
        binding.card4g.contentDescription = getString(
            if (highlighted == ForcedMode.LTE) R.string.cd_4g_on else R.string.cd_4g,
        )
    }

    private fun renderPreference(mode: ForcedMode?) {
        binding.preferenceLine.text = when (mode) {
            ForcedMode.WCDMA -> getString(R.string.preference_3g)
            ForcedMode.LTE -> getString(R.string.preference_4g)
            null -> getString(R.string.preference_unknown)
        }
    }

    private fun renderAdb() {
        val ready = Prefs.adbLoopbackReady(this)
        binding.adbStatus.text = getString(if (ready) R.string.adb_state_ready else R.string.adb_state_missing)
        binding.adbSetupButton.text = getString(if (ready) R.string.adb_setup_again else R.string.adb_setup_button)
    }

    private fun renderShizuku() {
        val (status, action, visible) = when {
            !ShizukuStatus.installed(this) -> Triple(
                getString(R.string.shizuku_state_missing),
                getString(R.string.shizuku_action_install),
                true,
            )
            !ShizukuStatus.running() -> Triple(
                getString(R.string.shizuku_state_stopped),
                getString(R.string.shizuku_action_open),
                true,
            )
            !ShizukuStatus.granted() -> Triple(
                getString(R.string.shizuku_state_denied),
                getString(R.string.shizuku_action_grant),
                true,
            )
            else -> Triple(getString(R.string.shizuku_state_ready), "", false)
        }
        binding.shizukuStatus.text = status
        binding.shizukuAction.isVisible = visible
        binding.shizukuAction.text = action
        binding.permissionButton.isVisible = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE,
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun onShizukuAction() {
        when {
            !ShizukuStatus.installed(this) -> openShizukuInstall()
            !ShizukuStatus.running() -> openShizukuApp()
            !ShizukuStatus.granted() -> Shizuku.requestPermission(REQUEST_SHIZUKU)
        }
    }

    private fun openRadio() {
        val opened = RadioInfoOpener.open(this)
        if (opened) {
            Snackbar.make(binding.root, R.string.radio_hint, Snackbar.LENGTH_LONG).show()
        } else {
            dialog(R.string.radio_manual_title, R.string.radio_manual, null)
        }
    }

    private fun openShizukuInstall() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuStatus.PACKAGE}"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            startActivity(web)
        }
    }

    private fun openShizukuApp() {
        val launch = packageManager.getLaunchIntentForPackage(ShizukuStatus.PACKAGE)
        if (launch != null) startActivity(launch) else openShizukuInstall()
    }

    private fun dialog(title: Int, message: Int, action: (() -> Unit)?) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.close, null)
        if (action != null) {
            builder.setPositiveButton(R.string.continue_action) { _, _ -> action() }
        }
        builder.show()
    }

    private companion object {
        const val REQUEST_SHIZUKU = 1001
    }
}
