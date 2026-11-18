package com.wangzan101.mika.driving

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Android Auto 上的 Jarvis:一块消息卡片显示状态和最近的回答,一个按钮开口/打断。 */
class JarvisCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // 库自带的示例名单(Google 的 Android Auto / Automotive 宿主);上架前按需收紧
            HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = MainCarScreen(carContext)
    }
}

/** 观察 [DrivingService] 的状态,每次变化重画模板。 */
class MainCarScreen(carContext: CarContext) : Screen(carContext) {
    init {
        lifecycleScope.launch {
            combine(DrivingService.state, DrivingService.response, DrivingService.transcript) { s, r, t -> Triple(s, r, t) }
                .collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val state = DrivingService.state.value
        val response = DrivingService.response.value
        val title = when (state) {
            DrivingState.IDLE -> "Jarvis"
            DrivingState.LISTENING -> "Listening..."
            DrivingState.PROCESSING -> "Thinking..."
            DrivingState.SPEAKING -> "Speaking"
        }
        val body = when {
            state == DrivingState.LISTENING -> DrivingService.transcript.value.ifBlank { "Go ahead, I'm listening." }
            state == DrivingState.PROCESSING -> DrivingService.transcript.value.ifBlank { "Working on it." }
            response.isNotBlank() -> response
            else -> "Say \"Jarvis\" or tap SPEAK."
        }.truncate(MAX_CHARS)

        // 说话/思考中按钮变成打断,不会在忙的时候再发一次「开口」
        val (label, action) = when (state) {
            DrivingState.IDLE -> "SPEAK" to DrivingService.ACTION_START_LISTENING
            else -> "STOP" to DrivingService.ACTION_INTERRUPT
        }
        return MessageTemplate.Builder(body)
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder().setTitle(label).setOnClickListener { send(action) }.build(),
            )
            .build()
    }

    private fun send(action: String) {
        // 车机上手机多半是熄屏的:服务没在跑时,系统可能不让从后台起麦克风前台服务
        if (!DrivingService.isRunning && action == DrivingService.ACTION_START_LISTENING) {
            DrivingService.send(carContext, action)
            CarToast.makeText(carContext, "Starting Jarvis. If nothing happens, open driving mode on your phone.", CarToast.LENGTH_LONG).show()
            return
        }
        DrivingService.send(carContext, action)
    }

    private fun String.truncate(max: Int) = if (length <= max) this else take(max - 1).trimEnd() + "…"

    private companion object {
        const val MAX_CHARS = 120
    }
}
