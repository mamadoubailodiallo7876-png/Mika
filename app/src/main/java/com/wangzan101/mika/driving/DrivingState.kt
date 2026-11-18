package com.wangzan101.mika.driving

import android.content.Context
import android.content.SharedPreferences

/** 驾驶模式的状态机:IDLE → LISTENING → PROCESSING → SPEAKING → IDLE;出错或打断从任何状态回 IDLE。 */
enum class DrivingState { IDLE, LISTENING, PROCESSING, SPEAKING }

/** 驾驶模式自己的开关,和 Mika 原有设置分开存([DrivingSettingsActivity] 里改)。 */
class DrivingPrefs private constructor(private val prefs: SharedPreferences) {
    /** 常驻监听「Jarvis」。需要 Porcupine AccessKey;关掉后只能点屏幕上的 SPEAK。 */
    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(WAKE_WORD, true)
        set(v) { prefs.edit().putBoolean(WAKE_WORD, v).apply() }

    /** 连上车载蓝牙时自动进入驾驶模式。 */
    var bluetoothTriggerEnabled: Boolean
        get() = prefs.getBoolean(BT_TRIGGER, true)
        set(v) { prefs.edit().putBoolean(BT_TRIGGER, v).apply() }

    /** true:任何蓝牙音频设备(包括耳机)都算;false:只认名字里带车厂/车机关键词的。 */
    var bluetoothAnyAudioDevice: Boolean
        get() = prefs.getBoolean(BT_ANY_AUDIO, true)
        set(v) { prefs.edit().putBoolean(BT_ANY_AUDIO, v).apply() }

    /** 声音模型(约 67 MB)只在不计流量的网络(Wi-Fi)下下载。 */
    var wifiOnlyDownload: Boolean
        get() = prefs.getBoolean(WIFI_ONLY, true)
        set(v) { prefs.edit().putBoolean(WIFI_ONLY, v).apply() }

    companion object {
        private const val WAKE_WORD = "wake_word"
        private const val BT_TRIGGER = "bt_trigger"
        private const val BT_ANY_AUDIO = "bt_any_audio"
        private const val WIFI_ONLY = "wifi_only"

        fun get(context: Context) = DrivingPrefs(
            context.applicationContext.getSharedPreferences("jarvis_driving", Context.MODE_PRIVATE),
        )
    }
}
