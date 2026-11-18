package com.wangzan101.mika.driving

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Background = Color(0xFF0A0A0A)
private val Accent = Color(0xFF00E5FF)
private val Muted = Color(0xFF8A8A8A)
private val Danger = Color(0xFFFF5252)

/** 免提驾驶界面:只有一个大按钮和当前状态,没有别的导航。状态来自 [DrivingService] 的 StateFlow。 */
@Composable
fun DrivingScreen(onSpeak: () -> Unit, onInterrupt: () -> Unit, onExit: () -> Unit) {
    val state by DrivingService.state.collectAsState()
    val transcript by DrivingService.transcript.collectAsState()
    val response by DrivingService.response.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(Background).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (state) {
                DrivingState.IDLE -> if (response.isBlank()) {
                    SpeakButton(onSpeak)
                } else {
                    // 刚说完:回答留在屏上,SPEAK 在下面,再问一句直接点
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { BodyText(response) }
                        SpeakButton(onSpeak, size = 140)
                    }
                }
                DrivingState.LISTENING -> Status("Listening...", transcript) { Waveform() }
                DrivingState.PROCESSING -> Status("Thinking...", transcript) { SpinningArc() }
                DrivingState.SPEAKING -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { StreamingText(response) }
                    InterruptButton(onInterrupt)
                }
            }
        }
        TextButton(onClick = onExit) {
            Text("EXIT DRIVING MODE", color = Muted, fontSize = 14.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SpeakButton(onClick: () -> Unit, size: Int = 240) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    val ring by pulse.animateFloat(0.15f, 0.45f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ring")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size((size + 40).dp).scale(scale).alpha(ring).clip(CircleShape).background(Accent))
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF101A1C)).border(3.dp, Accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("SPEAK", color = Accent, fontSize = (size / 7).sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun Status(label: String, transcript: String, indicator: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        indicator()
        Text(label, color = Accent, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        if (transcript.isNotBlank()) Text("“$transcript”", color = Muted, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}

/** 5 根竖条错开着起伏。 */
@Composable
private fun Waveform() {
    val t = rememberInfiniteTransition(label = "wave")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(120.dp)) {
        repeat(5) { i ->
            val h by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(450 + i * 90, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(Modifier.width(14.dp).height((120 * h).dp).clip(RoundedCornerShape(7.dp)).background(Accent))
        }
    }
}

@Composable
private fun SpinningArc() {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(110.dp)) {
        val stroke = 10.dp.toPx()
        drawArc(
            color = Accent, startAngle = angle, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** 回答一个词一个词地出现(跟着朗读的节奏);回答变长(下一句开始播)时接着往后出。 */
@Composable
private fun StreamingText(text: String) {
    var shown by remember { mutableIntStateOf(0) }
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }
    LaunchedEffect(text) {
        if (words.isEmpty()) shown = 0
        if (shown > words.size) shown = 0
        while (shown < words.size) {
            delay(140)
            shown++
        }
    }
    BodyText(words.take(shown).joinToString(" "))
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = Color.White, fontSize = 30.sp, lineHeight = 40.sp, textAlign = TextAlign.Center)
}

@Composable
private fun InterruptButton(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(40.dp)).background(Danger).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("INTERRUPT", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}
