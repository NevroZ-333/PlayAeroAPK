@file:OptIn(ExperimentalFoundationApi::class)

package com.nevroz.playeraero

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin

val DeepBlue = Color(0xFF0B3C5D)
val Aqua = Color(0xFF00D2E6)
val SkyBlue = Color(0xFF1E9BEA)
val Grass = Color(0xFFA8E86B)
private val AeroShadow = Color(0xFF0277BD)

private class Bub(val x: Float, val off: Float, val r: Float, val speed: Int)

private val Bubbles = run {
    val rnd = java.util.Random(7)
    List(16) { Bub(rnd.nextFloat(), rnd.nextFloat(), 6f + rnd.nextFloat() * 26f, 2 + rnd.nextInt(4)) }
}

fun DrawScope.drawBubble(c: Offset, r: Float, alpha: Float = 1f) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.White.copy(0.10f * alpha), Color.White.copy(0.5f * alpha)),
            center = c, radius = r),
        radius = r, center = c)
    drawCircle(color = Color.White.copy(0.75f * alpha), radius = r, center = c, style = Stroke(1.5f))
    drawCircle(color = Color.White.copy(0.9f * alpha), radius = r * 0.17f,
        center = Offset(c.x - r * 0.42f, c.y - r * 0.42f))
}

/** Небо, солнечный блик, трава и всплывающие пузыри — классический Frutiger Aero. */
@Composable
fun AeroBackground(bubbles: Boolean, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "ph")
    val glow by t.animateFloat(0.8f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gl")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF1E9BEA), Color(0xFF6CCBFF), Color(0xFFC9F3FF), Color(0xFFBDF29A))))) {
        Canvas(Modifier.fillMaxSize()) {
            val sun = Offset(size.width * 0.85f, size.height * 0.05f)
            drawCircle(brush = Brush.radialGradient(
                listOf(Color.White.copy(0.85f * glow), Color.Transparent), center = sun, radius = size.width),
                radius = size.width, center = sun)
            val hill = Path().apply {
                moveTo(0f, size.height); lineTo(0f, size.height * 0.9f)
                quadraticBezierTo(size.width * 0.5f, size.height * 0.8f, size.width, size.height * 0.92f)
                lineTo(size.width, size.height); close()
            }
            drawPath(hill, Brush.verticalGradient(listOf(Color(0xFF9BE564).copy(0.55f), Color(0xFF4CC24F).copy(0.55f))))
            if (bubbles) Bubbles.forEach { b ->
                val p = (phase * b.speed + b.off) % 1f
                val y = size.height * (1.1f - p * 1.25f)
                val x = size.width * b.x + sin(2f * PI.toFloat() * (phase * b.speed + b.off)) * 16.dp.toPx()
                drawBubble(Offset(x, y), b.r.dp.toPx(), 0.9f)
            }
        }
        content()
    }
}

/** Стеклянная панель с глянцевым «двойным» градиентом. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), strong: Boolean = false): Modifier = this
    .shadow(10.dp, shape, ambientColor = AeroShadow, spotColor = AeroShadow)
    .clip(shape)
    .background(Brush.verticalGradient(
        0f to Color.White.copy(if (strong) 0.80f else 0.62f),
        0.5f to Color.White.copy(if (strong) 0.48f else 0.34f),
        0.51f to Color.White.copy(if (strong) 0.36f else 0.24f),
        1f to Color.White.copy(0.14f)))
    .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.25f))), shape)

@Composable
fun GlossButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    Box(
        modifier.size(size).scale(sc)
            .shadow(12.dp, CircleShape, ambientColor = AeroShadow, spotColor = AeroShadow)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF7BE8FF), Color(0xFF0FA6E8), Color(0xFF0277BD))))
            .border(2.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(0.3f))), CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = size * 0.06f)
            .fillMaxWidth(0.72f).fillMaxHeight(0.45f).clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.85f), Color.White.copy(0.05f)))))
        content()
    }
}

@Composable
fun AeroChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(300), label = "chip")
    val shape = RoundedCornerShape(50)
    Box(Modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(
            lerp(Color.White.copy(0.75f), Color(0xFF7BE8FF), a), lerp(Color.White.copy(0.3f), Color(0xFF0FA6E8), a))))
        .border(1.dp, Color.White.copy(0.9f), shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(text, color = lerp(DeepBlue, Color.White, a), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun AText(s: String, size: Int = 14, bold: Boolean = false, color: Color = DeepBlue,
          modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) =
    Text(s, modifier = modifier, color = color, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)

@Composable
fun Cover(track: Track?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, track?.uri) {
        value = if (track == null) null else Covers.get(ctx, track)
    }
    Box(modifier.clip(shape).border(1.5.dp, Color.White.copy(0.8f), shape)) {
        Crossfade(bmp, animationSpec = tween(450), label = "cover") { b ->
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else FallbackArt(track)
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.White.copy(0.38f), 0.45f to Color.Transparent)))
    }
}

@Composable
fun FallbackArt(t: Track?) {
    val h = (t?.title?.hashCode() ?: 0).absoluteValue % 3
    val pal = listOf(
        listOf(Color(0xFF29B6F6), Color(0xFF00E5FF)),
        listOf(Color(0xFF4CC24F), Color(0xFFB9F27C)),
        listOf(Color(0xFF0277BD), Color(0xFF7BE8FF)))[h]
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(pal)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.92f), modifier = Modifier.fillMaxSize(0.5f))
    }
}
