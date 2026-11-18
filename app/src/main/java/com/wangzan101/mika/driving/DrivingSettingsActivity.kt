package com.wangzan101.mika.driving

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.BuildConfig

/**
 * 驾驶模式的入口页(独立的桌面图标「Jarvis Driving」):授权、开关、声音模型状态,
 * 以及不用真的连车机就能试一遍蓝牙触发的按钮。Mika 原有的设置页不动。
 */
class DrivingSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsContent() }
    }

    @Composable
    private fun SettingsContent() {
        val prefs = remember { DrivingPrefs.get(this) }
        var refresh by remember { mutableIntStateOf(0) }
        var wakeWord by remember { mutableStateOf(prefs.wakeWordEnabled) }
        var btTrigger by remember { mutableStateOf(prefs.bluetoothTriggerEnabled) }
        var btAny by remember { mutableStateOf(prefs.bluetoothAnyAudioDevice) }
        var wifiOnly by remember { mutableStateOf(prefs.wifiOnlyDownload) }
        val model by JarvisModel.status.collectAsState()
        val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter { refresh >= 0 && checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        val modelReady = refresh >= 0 && JarvisModel.isReady(this)

        Column(
            Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Jarvis Driving", color = Color(0xFF00E5FF), fontSize = 28.sp, fontWeight = FontWeight.Bold)

            Section("Permissions")
            if (missing.isEmpty()) {
                Note("Microphone, Bluetooth and notifications are all granted.")
            } else {
                Note("Missing: " + missing.joinToString { it.substringAfterLast('.') })
                Button(onClick = { requestPermissions.launch(missing.toTypedArray()) }) { Text("Grant permissions") }
            }

            Section("Wake word")
            ToggleRow("Listen for \"Jarvis\"", wakeWord) { wakeWord = it; prefs.wakeWordEnabled = it }
            if (BuildConfig.PORCUPINE_ACCESS_KEY.isBlank()) {
                Note("No Porcupine AccessKey in this build: add PORCUPINE_ACCESS_KEY to local.properties and rebuild. Until then use the SPEAK button.")
            }

            Section("Bluetooth trigger")
            ToggleRow("Start when a car connects", btTrigger) { btTrigger = it; prefs.bluetoothTriggerEnabled = it }
            ToggleRow("Any Bluetooth audio device counts (includes headphones)", btAny) { btAny = it; prefs.bluetoothAnyAudioDevice = it }
            OutlinedButton(onClick = { BluetoothTrigger.fire(this@DrivingSettingsActivity) }) { Text("Simulate car connecting") }

            Section("Jarvis voice")
            Note(
                when {
                    modelReady -> "Offline voice installed."
                    model is ModelStatus.Downloading -> "Downloading… ${(model as ModelStatus.Downloading).percent}%"
                    model is ModelStatus.WaitingForWifi -> "Waiting for Wi-Fi (about 67 MB)."
                    model is ModelStatus.Failed -> "Download failed: ${(model as ModelStatus.Failed).reason}"
                    else -> "Not installed (about 67 MB). The phone's built-in voice is used until it is."
                },
            )
            ToggleRow("Download on Wi-Fi only", wifiOnly) { wifiOnly = it; prefs.wifiOnlyDownload = it }
            if (!modelReady) Button(onClick = { JarvisModel.ensure(this@DrivingSettingsActivity); refresh++ }) { Text("Download voice") }

            Section("Driving mode")
            Button(onClick = { startActivity(Intent(this@DrivingSettingsActivity, DrivingActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Start driving mode")
            }
        }
    }

    @Composable
    private fun Section(title: String) = Text(title.uppercase(), color = Color(0xFF8A8A8A), fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 12.dp))

    @Composable
    private fun Note(text: String) = Text(text, color = Color.White, fontSize = 15.sp)

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
