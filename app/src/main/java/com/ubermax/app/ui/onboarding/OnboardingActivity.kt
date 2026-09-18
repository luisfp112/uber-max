package com.ubermax.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivityOnboardingBinding
import com.ubermax.app.databinding.ItemPermissionBinding
import com.ubermax.app.service.UberAccessibilityService
import com.ubermax.app.util.AppPermission
import com.ubermax.app.util.PermissionItem
import com.ubermax.app.util.PermissionSnapshot
import com.ubermax.app.util.PermissionStatus

/**
 * Pantalla de configuración inicial: muestra el checklist de permisos, permite
 * concederlos y avisa si falta alguno obligatorio.
 *
 * La lógica de "qué falta" vive en [PermissionStatus] (JVM pura); aquí solo se
 * consulta el estado real del sistema y se lanzan los intents/requests.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOnboardingContinue.setOnClickListener {
            val missing = PermissionStatus.missingRequired(snapshot())
            if (missing.isEmpty()) {
                Toast.makeText(this, R.string.onboarding_done, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.onboarding_missing_required, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val snapshot = snapshot()
        val items = PermissionStatus.checklist(snapshot)

        binding.permissionsContainer.removeAllViews()
        items.forEach { item ->
            binding.permissionsContainer.addView(buildRow(item))
        }

        val ready = PermissionStatus.isReady(snapshot)
        binding.tvOnboardingStatus.text = getString(
            if (ready) R.string.onboarding_ready else R.string.onboarding_missing_required
        )
        binding.tvOnboardingStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (ready) R.color.profit_positive else R.color.warning
            )
        )
    }

    private fun buildRow(item: PermissionItem): android.view.View {
        val row = ItemPermissionBinding.inflate(layoutInflater, binding.permissionsContainer, false)
        row.tvPermissionName.text = getString(nameRes(item.permission))
        row.tvPermissionReason.text = getString(reasonRes(item.permission))
        row.tvPermissionTag.text = getString(
            if (item.required) R.string.permission_required_label else R.string.permission_optional_label
        )
        row.tvPermissionState.text = getString(
            if (item.granted) R.string.permission_granted else R.string.permission_denied
        )
        row.tvPermissionState.setTextColor(
            ContextCompat.getColor(
                this,
                if (item.granted) R.color.profit_positive else R.color.warning
            )
        )

        if (item.granted) {
            row.btnPermissionAction.visibility = android.view.View.GONE
        } else {
            row.btnPermissionAction.visibility = android.view.View.VISIBLE
            row.btnPermissionAction.text = getString(
                if (item.permission == AppPermission.ACCESSIBILITY) {
                    R.string.permission_action_open
                } else {
                    R.string.permission_action_grant
                }
            )
            row.btnPermissionAction.setOnClickListener { handleAction(item.permission) }
        }
        return row.root
    }

    private fun handleAction(permission: AppPermission) {
        when (permission) {
            AppPermission.ACCESSIBILITY ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

            AppPermission.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }

            AppPermission.BATTERY_OPTIMIZATION -> {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
                runCatching { startActivity(intent) }
            }

            AppPermission.OVERLAY ->
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )

            AppPermission.LOCATION ->
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )

            AppPermission.MICROPHONE ->
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))

            AppPermission.BLUETOOTH ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                }
        }
    }

    private fun snapshot(): PermissionSnapshot {
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else true

        val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)

        val microphone = granted(Manifest.permission.RECORD_AUDIO)

        val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else true

        val battery = try {
            (getSystemService(POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(packageName)
        } catch (t: Throwable) {
            false
        }

        return PermissionSnapshot(
            accessibility = UberAccessibilityService.isRunning,
            notifications = notifications,
            batteryOptimizationIgnored = battery,
            overlay = Settings.canDrawOverlays(this),
            location = location,
            microphone = microphone,
            bluetooth = bluetooth
        )
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun nameRes(permission: AppPermission): Int = when (permission) {
        AppPermission.ACCESSIBILITY -> R.string.perm_accessibility_name
        AppPermission.NOTIFICATIONS -> R.string.perm_notifications_name
        AppPermission.BATTERY_OPTIMIZATION -> R.string.perm_battery_name
        AppPermission.OVERLAY -> R.string.perm_overlay_name
        AppPermission.LOCATION -> R.string.perm_location_name
        AppPermission.MICROPHONE -> R.string.perm_microphone_name
        AppPermission.BLUETOOTH -> R.string.perm_bluetooth_name
    }

    private fun reasonRes(permission: AppPermission): Int = when (permission) {
        AppPermission.ACCESSIBILITY -> R.string.perm_accessibility_reason
        AppPermission.NOTIFICATIONS -> R.string.perm_notifications_reason
        AppPermission.BATTERY_OPTIMIZATION -> R.string.perm_battery_reason
        AppPermission.OVERLAY -> R.string.perm_overlay_reason
        AppPermission.LOCATION -> R.string.perm_location_reason
        AppPermission.MICROPHONE -> R.string.perm_microphone_reason
        AppPermission.BLUETOOTH -> R.string.perm_bluetooth_reason
    }
}
