package com.wangzan101.mika.driving

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.ui.theme.animationsOff
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jarvis 的 HUD 视觉件(暂定值:没有既有设计系统,这些色值、尺寸都是这次定的,以后要和 DESIGN.md 对齐)。
 * 颜色和 [com.wangzan101.mika.ui.theme.DarkColors] 保持一致:近黑海军蓝底、青色发光线、冰白字、金色提示、红色警示。
 */
object Hud {
    val Bg = Color(0xFF05090F)
    val Panel = Color(0xFF0B1420)
    val Cyan = Color(0xFF29D8FF)
    val CyanDim = Color(0x6629D8FF)
    val CyanHair = Color(0x3329D8FF)
    val CyanFill = Color(0x1429D8FF)
    val Gold = Color(0xFFFFB300)
    val Red = Color(0xFFFF5A4F)
    val Ink = Color(0xFFE6F6FF)
    val Steel = Color(0xFF8FB4C8)
    val OnCyan = Color(0xFF031018)

    /** 标签、读数用的等宽字:字符间距拉开、全大写,像仪表。 */
    val Mono = FontFamily.Monospace

    fun label(size: Int = 11) = TextStyle(fontFamily = Mono, fontSize = size.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
}

/** 切掉左上和右下两个角的多边形,HUD 面板和按钮的外形。 */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        return Outline.Generic(
            Path().apply {
                moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c)
                lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
            },
        )
    }
}

/** 背景:深海军蓝渐变 + 淡青色网格 + 四周压暗。静态,不耗电。 */
@Composable
fun HudBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Hud.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF0C1B2A), Hud.Bg), center = Offset(size.width / 2, size.height * 0.42f), radius = size.maxDimension * 0.75f))
            val step = 36.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Color(0x0D29D8FF), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0x0D29D8FF), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
        content()
    }
}

enum class HudTone { Primary, Quiet, Danger }

/** 切角按钮。Primary 青色实心;Quiet 青色描边;Danger 红色描边。最小高度 56dp,方便开车时点。 */
@Composable
fun HudButton(onClick: () -> Unit, modifier: Modifier = Modifier, tone: HudTone = HudTone.Primary, content: @Composable RowScope.() -> Unit) {
    val (fill, line, text) = when (tone) {
        HudTone.Primary -> Triple(Hud.Cyan, Hud.Cyan, Hud.OnCyan)
        HudTone.Quiet -> Triple(Hud.CyanFill, Hud.CyanDim, Hud.Cyan)
        HudTone.Danger -> Triple(Color(0x26FF5A4F), Hud.Red, Hud.Red)
    }
    val shape = ChamferShape(12.dp)
    Row(
        modifier.defaultMinSize(minHeight = 56.dp).clip(shape).background(fill).border(1.5.dp, line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides text) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.merge(Hud.label(14).copy(fontWeight = FontWeight.Bold))) { content() }
        }
    }
}

/** 切角面板:深色底、青色细线,上边左侧一小段亮线当「识别点」。 */
@Composable
fun HudPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = ChamferShape(14.dp)
    Box(modifier.clip(shape).background(Hud.Panel.copy(alpha = 0.92f)).border(1.dp, Hud.CyanHair, shape)) {
        Canvas(Modifier.matchParentSize()) {
            val cut = 14.dp.toPx()
            drawLine(Hud.Cyan, Offset(cut, 1f), Offset(cut + 36.dp.toPx(), 1f), 3f)
        }
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** 小标签:等宽、全大写、拉开字距,前面一个青色小点。 */
@Composable
fun HudLabel(text: String, modifier: Modifier = Modifier, color: Color = Hud.Steel, dot: Color? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dot != null) Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(text.uppercase(), style = Hud.label(), color = color)
    }
}

/** 设置页的分组标题:标签 + 一条淡青色的线。 */
@Composable
fun HudSection(title: String) {
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudLabel(title, color = Hud.Cyan)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hud.CyanHair))
    }
}

/**
 * 圆形主视觉(方舟反应堆):一个状态一种动法。
 * IDLE 外圈刻度慢转、核心缓慢呼吸;LISTENING 一圈随声音起伏的辐射条;PROCESSING 分段弧快速反向旋转;SPEAKING 核心跳动并向外扩散涟漪。
 * 系统关了动画时画成静止的一帧。
 */
@Composable
fun ArcReactor(state: DrivingState, level: Float, modifier: Modifier = Modifier) {
    val still = animationsOff(LocalContext.current)
    val transition = rememberInfiniteTransition(label = "reactor")
    // 一个 0..1 的相位,每 12 秒一圈;各层的转速都取整数倍,循环时没有跳变
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val t = if (still) 0f else phase
    Canvas(modifier.semantics { contentDescription = "Jarvis ${state.name.lowercase()}"; role = Role.Image }) {
        val r = size.minDimension / 2
        val c = center
        val twoPi = (2 * PI).toFloat()
        val spinMid = when (state) { DrivingState.PROCESSING -> 10 else -> 2 }
        val pulse = when (state) {
            DrivingState.IDLE -> 1f + 0.05f * sin(t * twoPi * 4)
            DrivingState.LISTENING -> 1f + 0.10f * level
            DrivingState.PROCESSING -> 1f + 0.06f * sin(t * twoPi * 24)
            DrivingState.SPEAKING -> 1f + 0.12f * abs(sin(t * twoPi * 18))
        }
        // 核心辉光
        drawCircle(Brush.radialGradient(listOf(Hud.Cyan.copy(alpha = 0.55f), Hud.Cyan.copy(alpha = 0.12f), Color.Transparent), c, r * 0.62f * pulse), r * 0.62f * pulse, c)
        // 外圈 + 刻度
        drawCircle(Hud.CyanDim, r * 0.96f, c, style = Stroke(1.5.dp.toPx()))
        rotate(t * 360f, c) {
            for (i in 0 until 72) {
                val a = i / 72f * twoPi
                val long = i % 6 == 0
                tick(c, a, r * 0.93f, r * (if (long) 0.84f else 0.89f), if (long) Hud.Cyan else Hud.CyanDim, if (long) 2.dp.toPx() else 1.dp.toPx())
            }
        }
        // 分段弧
        rotate(-t * 360f * spinMid, c) {
            for (i in 0 until 3) arcRing(c, r * 0.74f, i * 120f + 10f, 92f, Hud.Cyan, 7.dp.toPx())
        }
        rotate(t * 360f * spinMid * 1.5f, c) {
            for (i in 0 until 4) arcRing(c, r * 0.64f, i * 90f + 20f, 50f, Hud.CyanDim, 2.dp.toPx())
        }
        // 听:一圈随声音起伏的辐射条
        if (state == DrivingState.LISTENING) {
            val n = 56
            for (i in 0 until n) {
                val a = i / n.toFloat() * twoPi
                val wobble = 0.35f + 0.65f * abs(sin(t * twoPi * 6 + i * 0.7f) * cos(t * twoPi * 3 + i * 1.3f))
                val len = r * (0.04f + 0.20f * wobble * (0.25f + level))
                tick(c, a, r * 0.54f, r * 0.54f + len, Hud.Cyan, 3.dp.toPx())
            }
        }
        // 说:向外扩散的涟漪
        if (state == DrivingState.SPEAKING) {
            for (k in 0 until 2) {
                val p = (t * 3 + k * 0.5f) % 1f
                drawCircle(Hud.Cyan.copy(alpha = (1f - p) * 0.5f), r * (0.52f + 0.44f * p), c, style = Stroke(2.dp.toPx()))
            }
        }
        // 内环 + 核心
        drawCircle(Hud.Cyan, r * 0.52f, c, style = Stroke(2.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Color.White, Hud.Cyan, Hud.Cyan.copy(alpha = 0.2f)), c, r * 0.24f * pulse), r * 0.24f * pulse, c)
    }
}

private fun DrawScope.tick(c: Offset, angle: Float, from: Float, to: Float, color: Color, width: Float) {
    val dx = cos(angle); val dy = sin(angle)
    drawLine(color, Offset(c.x + dx * from, c.y + dy * from), Offset(c.x + dx * to, c.y + dy * to), width, StrokeCap.Butt)
}

private fun DrawScope.arcRing(c: Offset, radius: Float, start: Float, sweep: Float, color: Color, width: Float) {
    drawArc(color, start, sweep, false, Offset(c.x - radius, c.y - radius), Size(radius * 2, radius * 2), style = Stroke(width, cap = StrokeCap.Butt))
}
