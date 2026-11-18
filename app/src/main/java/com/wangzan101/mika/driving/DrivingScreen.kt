package com.wangzan101.mika.driving

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 免提驾驶界面,Stark 风格的 HUD:页眉(名字 + 状态读数)、中间的圆形反应堆、状态词、对话读数、底部一个大按钮。
 * 状态词保持平白(READY / LISTENING / THINKING / SPEAKING),开车时一眼能读;动效只在中间那一块。
 * 状态来自 [DrivingService] 的 StateFlow。
 */
@Composable
fun DrivingScreen(onSpeak: () -> Unit, onInterrupt: () -> Unit, onExit: () -> Unit) {
    val state by DrivingService.state.collectAsState()
    val transcript by DrivingService.transcript.collectAsState()
    val response by DrivingService.response.collectAsState()
    val level by DrivingService.level.collectAsState()
    val running by DrivingService.runningFlow.collectAsState()
    val wakeError by DrivingService.wakeError.collectAsState()
    val prefs = DrivingPrefs.get(LocalContext.current)
    DrivingContent(
        DrivingUi(
            state, transcript, response, level,
            mic = running, wake = prefs.wakeWordEnabled && running && wakeError == null,
            voice = if (prefs.voiceEngine == VoiceEngine.Kokoro && !prefs.kokoroTooSlow) "kokoro" else "piper",
        ),
        onSpeak, onInterrupt, onExit,
    )
}

/** 画面要的全部输入,和 Android、服务都无关,方便截图和预览。 */
data class DrivingUi(
    val state: DrivingState,
    val transcript: String = "",
    val response: String = "",
    val level: Float = 0f,
    val mic: Boolean = true,
    val wake: Boolean = true,
    val voice: String = "kokoro",
)

@Composable
fun DrivingContent(ui: DrivingUi, onSpeak: () -> Unit, onInterrupt: () -> Unit, onExit: () -> Unit) {
    val state = ui.state
    HudBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HudHeader(ui)
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // 圆形反应堆同时是「说话」按钮(只在待命时)
                ArcReactor(
                    state, ui.level,
                    Modifier.fillMaxWidth(0.78f).aspectRatio(1f).then(
                        if (state == DrivingState.IDLE) {
                            Modifier.clickable(role = Role.Button, onClick = onSpeak).semantics { contentDescription = "Speak to Jarvis" }
                        } else Modifier,
                    ),
                )
                Text(
                    when (state) {
                        DrivingState.IDLE -> "TAP TO SPEAK"
                        DrivingState.LISTENING -> "LISTENING"
                        DrivingState.PROCESSING -> "THINKING"
                        DrivingState.SPEAKING -> "SPEAKING"
                    },
                    Modifier.padding(top = 16.dp),
                    color = if (state == DrivingState.IDLE) Hud.Cyan else Hud.Ink,
                    style = Hud.label(22).copy(fontWeight = FontWeight.Bold, letterSpacing = 5.sp),
                )
                Box(Modifier.padding(top = 14.dp).weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Readout(state, ui.transcript, ui.response)
                }
            }
            if (state == DrivingState.SPEAKING || state == DrivingState.PROCESSING) {
                HudButton(onInterrupt, Modifier.fillMaxWidth().padding(bottom = 4.dp), HudTone.Danger) { Text("INTERRUPT", fontSize = 18.sp) }
            }
            Text(
                "EXIT DRIVING MODE",
                Modifier.clickable(role = Role.Button, onClick = onExit).padding(horizontal = 16.dp, vertical = 14.dp),
                color = Hud.Steel, style = Hud.label(12),
            )
        }
    }
}

/** 页眉:名字、一行说明,右边是系统读数(麦克风、唤醒词、声音)。 */
@Composable
private fun HudHeader(ui: DrivingUi) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("J.A.R.V.I.S.", color = Hud.Cyan, style = Hud.label(20).copy(fontWeight = FontWeight.Bold, letterSpacing = 6.sp))
            HudLabel("Systems " + if (ui.mic) "online" else "standby", dot = if (ui.mic) Hud.Cyan else Hud.Steel)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HudLabel("Mic " + if (ui.mic) "live" else "off", dot = if (ui.mic) Hud.Cyan else Hud.Steel)
            HudLabel("Wake " + if (ui.wake) "armed" else "off", dot = if (ui.wake) Hud.Cyan else Hud.Steel)
            HudLabel("Voice " + ui.voice, dot = Hud.Cyan)
        }
    }
}

/** 听到的话和回答。回答按句子随朗读增长,每句里再一个词一个词地出现。 */
@Composable
private fun Readout(state: DrivingState, transcript: String, response: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (transcript.isNotBlank() && state != DrivingState.SPEAKING) {
            HudPanel(Modifier.fillMaxWidth()) {
                Column {
                    HudLabel("You", color = Hud.Steel)
                    Text(transcript, Modifier.padding(top = 4.dp), color = Hud.Ink, fontSize = 20.sp, lineHeight = 28.sp)
                }
            }
        }
        if (response.isNotBlank()) {
            HudPanel(Modifier.fillMaxWidth()) {
                Column {
                    HudLabel("Jarvis", color = Hud.Cyan)
                    if (state == DrivingState.SPEAKING) StreamingText(response) else Body(response)
                }
            }
        }
    }
}

@Composable
private fun StreamingText(text: String) {
    var shown by remember { mutableIntStateOf(0) }
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }
    LaunchedEffect(text) {
        if (words.isEmpty() || shown > words.size) shown = 0
        while (shown < words.size) {
            delay(140)
            shown++
        }
    }
    Body(words.take(shown).joinToString(" "))
}

@Composable
private fun Body(text: String) {
    Text(text, Modifier.padding(top = 4.dp), color = Hud.Ink, fontSize = 26.sp, lineHeight = 36.sp, textAlign = TextAlign.Start)
}
