package com.tokenheat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokenheat.proto.HubModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Recognizable AI vendor brand identity for model cards.
 */
enum class ModelBrand {
    OPENAI,
    ANTHROPIC,
    GOOGLE_GEMINI,
    DEEPSEEK,
    ZHIPU_GLM,
    QWEN,
    HUNYUAN,
    ERNIE,
    DOUBAO,
    META_LLAMA,
    MISTRAL,
    OPENCODE_ZEN,
    QODER,
    UNKNOWN_BOT,
}

object BrandResolver {
    fun resolve(model: HubModel): ModelBrand {
        return resolve("${model.vendor} ${model.name} ${model.id}")
    }

    fun resolve(modelId: String): ModelBrand {
        val raw = modelId.lowercase()
        return when {
            raw.contains("deepseek") -> ModelBrand.DEEPSEEK
            raw.contains("claude") || raw.contains("anthropic") -> ModelBrand.ANTHROPIC
            raw.contains("gpt") || raw.contains("openai") || raw.contains("chatgpt") || raw.contains("o1-") || raw.contains("o3-") -> ModelBrand.OPENAI
            raw.contains("gemini") || raw.contains("google") || raw.contains("antigravity") -> ModelBrand.GOOGLE_GEMINI
            raw.contains("glm") || raw.contains("zhipu") || raw.contains("智谱") || raw.contains("zcode") -> ModelBrand.ZHIPU_GLM
            raw.contains("qwen") || raw.contains("千问") || raw.contains("alibaba") -> ModelBrand.QWEN
            raw.contains("hunyuan") || raw.contains("混元") || raw.contains("workbuddy") || raw.contains("tencent") -> ModelBrand.HUNYUAN
            raw.contains("ernie") || raw.contains("文心") || raw.contains("baidu") -> ModelBrand.ERNIE
            raw.contains("doubao") || raw.contains("豆包") || raw.contains("bytedance") -> ModelBrand.DOUBAO
            raw.contains("llama") || raw.contains("meta") -> ModelBrand.META_LLAMA
            raw.contains("mistral") || raw.contains("codestral") || raw.contains("pixtral") -> ModelBrand.MISTRAL
            raw.contains("zen") || raw.contains("opencode") -> ModelBrand.OPENCODE_ZEN
            raw.contains("qoder") -> ModelBrand.QODER
            else -> ModelBrand.UNKNOWN_BOT
        }
    }
}

/**
 * Square vector brand logo badge for LLM models.
 * Strictly uses Zinc low-saturation palette with distinctive geometric silhouettes.
 */
@Composable
fun ModelBrandBadge(
    model: HubModel,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    ModelBrandBadgeContent(BrandResolver.resolve(model), modifier, size)
}

@Composable
fun ModelBrandBadge(
    modelId: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    ModelBrandBadgeContent(BrandResolver.resolve(modelId), modifier, size)
}

@Composable
private fun ModelBrandBadgeContent(
    brand: ModelBrand,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (brand) {
            ModelBrand.OPENAI -> OpenAILogo(size)
            ModelBrand.ANTHROPIC -> AnthropicLogo(size)
            ModelBrand.GOOGLE_GEMINI -> GeminiLogo(size)
            ModelBrand.DEEPSEEK -> DeepSeekLogo(size)
            ModelBrand.ZHIPU_GLM -> GLMLogo(size)
            ModelBrand.QWEN -> QwenLogo(size)
            ModelBrand.HUNYUAN -> HunyuanLogo(size)
            ModelBrand.ERNIE -> ErnieLogo(size)
            ModelBrand.DOUBAO -> DoubaoLogo(size)
            ModelBrand.META_LLAMA -> LlamaLogo(size)
            ModelBrand.MISTRAL -> MistralLogo(size)
            ModelBrand.OPENCODE_ZEN -> ZenLogo(size)
            ModelBrand.QODER -> QoderLogo(size)
            ModelBrand.UNKNOWN_BOT -> {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = "模型",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Individual Geometric Brand Vector Renderers (Low Saturation Zinc Palette)
// -----------------------------------------------------------------------------

@Composable
private fun OpenAILogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.62f)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.width * 0.38f
        val strokeW = this.size.width * 0.09f
        // 6-petal radial geometry mimicking OpenAI flower
        for (i in 0 until 6) {
            val angle = (i * 60.0 * PI / 180.0).toFloat()
            val x1 = center.x + r * cos(angle)
            val y1 = center.y + r * sin(angle)
            val x2 = center.x + (r * 0.45f) * cos(angle + 0.8f)
            val y2 = center.y + (r * 0.45f) * sin(angle + 0.8f)
            drawLine(
                color = tint,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = tint,
            radius = strokeW * 0.75f,
            center = center,
        )
    }
}

@Composable
private fun AnthropicLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.58f)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.16f
        // Distinctive overlapping angled strokes of Claude/Anthropic
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.85f),
            end = Offset(w * 0.58f, h * 0.15f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.85f, h * 0.85f),
            end = Offset(w * 0.42f, h * 0.15f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.58f),
            end = Offset(w * 0.68f, h * 0.58f),
            strokeWidth = strokeW * 0.85f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun GeminiLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.62f)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        // 4-point sparkle star
        val path = Path().apply {
            moveTo(cx, h * 0.08f)
            quadraticTo(cx, cy, w * 0.92f, cy)
            quadraticTo(cx, cy, cx, h * 0.92f)
            quadraticTo(cx, cy, w * 0.08f, cy)
            quadraticTo(cx, cy, cx, h * 0.08f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
private fun DeepSeekLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            // Whale fin / wave curve
            moveTo(w * 0.12f, h * 0.65f)
            cubicTo(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.2f, w * 0.88f, h * 0.55f)
            cubicTo(w * 0.7f, h * 0.8f, w * 0.35f, h * 0.85f, w * 0.12f, h * 0.65f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
        drawCircle(
            color = tint,
            radius = w * 0.08f,
            center = Offset(w * 0.65f, h * 0.42f),
        )
    }
}

@Composable
private fun GLMLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.58f)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.14f
        // Z-shaped geometry for Zhipu
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.22f)
            lineTo(w * 0.82f, h * 0.22f)
            lineTo(w * 0.22f, h * 0.78f)
            lineTo(w * 0.82f, h * 0.78f)
        }
        drawPath(path = path, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
}

@Composable
private fun QwenLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.4f
        // Hexagon / Diamond polygon
        val path = Path().apply {
            for (i in 0 until 6) {
                val angle = (i * 60.0 * PI / 180.0).toFloat()
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = w * 0.11f))
        drawCircle(color = tint, radius = w * 0.1f, center = Offset(cx, cy))
    }
}

@Composable
private fun HunyuanLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.11f
        // Interlocking dual rings
        drawCircle(
            color = tint,
            radius = w * 0.28f,
            center = Offset(w * 0.4f, h * 0.5f),
            style = Stroke(width = strokeW),
        )
        drawCircle(
            color = tint,
            radius = w * 0.28f,
            center = Offset(w * 0.6f, h * 0.5f),
            style = Stroke(width = strokeW),
        )
    }
}

@Composable
private fun ErnieLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeW = w * 0.1f
        // Concentric burst petals
        drawCircle(color = tint, radius = w * 0.12f, center = Offset(cx, cy))
        drawLine(
            color = tint,
            start = Offset(cx, h * 0.12f),
            end = Offset(cx, h * 0.88f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, cy),
            end = Offset(w * 0.88f, cy),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DoubaoLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.58f)) {
        val w = this.size.width
        val h = this.size.height
        // Friendly round dialogue pebble
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.35f)
            cubicTo(w * 0.2f, h * 0.15f, w * 0.8f, h * 0.15f, w * 0.8f, h * 0.45f)
            cubicTo(w * 0.8f, h * 0.75f, w * 0.45f, h * 0.85f, w * 0.3f, h * 0.82f)
            lineTo(w * 0.15f, h * 0.9f)
            lineTo(w * 0.22f, h * 0.72f)
            cubicTo(w * 0.18f, h * 0.6f, w * 0.2f, h * 0.45f, w * 0.2f, h * 0.35f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
    }
}

@Composable
private fun LlamaLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.11f
        // Mobius infinity loop
        val path = Path().apply {
            moveTo(w * 0.3f, h * 0.5f)
            cubicTo(w * 0.1f, h * 0.25f, w * 0.1f, h * 0.75f, w * 0.3f, h * 0.5f)
            cubicTo(w * 0.5f, h * 0.25f, w * 0.7f, h * 0.25f, w * 0.8f, h * 0.5f)
            cubicTo(w * 0.9f, h * 0.75f, w * 0.7f, h * 0.75f, w * 0.5f, h * 0.5f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
}

@Composable
private fun MistralLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.56f)) {
        val w = this.size.width
        val h = this.size.height
        val step = w / 5f
        // Pixel step ladder M structure
        val rects = listOf(
            Offset(0f, 0f) to Size(step * 0.9f, h),
            Offset(step, step) to Size(step * 0.9f, step * 1.5f),
            Offset(step * 2, step * 2) to Size(step * 0.9f, step * 2f),
            Offset(step * 3, step) to Size(step * 0.9f, step * 1.5f),
            Offset(step * 4, 0f) to Size(step * 0.9f, h),
        )
        rects.forEach { (offset, rectSize) ->
            drawRoundRect(
                color = tint,
                topLeft = offset,
                size = rectSize,
                cornerRadius = CornerRadius(step * 0.2f),
            )
        }
    }
}

@Composable
private fun ZenLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        // Enso circular brush stroke (open Zen circle)
        val path = Path().apply {
            addArc(
                oval = androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f),
                startAngleDegrees = 30f,
                sweepAngleDegrees = 300f,
            )
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = w * 0.14f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun QoderLogo(size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(size * 0.6f)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.12f
        // Q-shaped geometric loop
        drawCircle(
            color = tint,
            radius = w * 0.32f,
            center = Offset(w * 0.45f, h * 0.45f),
            style = Stroke(width = strokeW),
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.52f, h * 0.55f),
            end = Offset(w * 0.85f, h * 0.85f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}
