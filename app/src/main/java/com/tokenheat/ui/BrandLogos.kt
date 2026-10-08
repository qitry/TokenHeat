package com.tokenheat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokenheat.R
import com.tokenheat.proto.HubModel

/**
 * Recognizable AI vendor brand identity mapped to official downloadable brand assets.
 */
enum class ModelBrand(val label: String, val iconRes: Int? = null) {
    OPENAI("OpenAI", R.drawable.ic_brand_openai),
    ANTHROPIC("Anthropic Claude", R.drawable.ic_brand_claude),
    GOOGLE_GEMINI("Google Gemini", R.drawable.ic_brand_gemini),
    DEEPSEEK("DeepSeek", R.drawable.ic_brand_deepseek),
    ZHIPU_GLM("智谱 GLM", R.drawable.ic_brand_zhipu),
    QWEN("阿里通义千问", R.drawable.ic_brand_qwen),
    HUNYUAN("腾讯混元", R.drawable.ic_brand_hunyuan),
    ERNIE("百度文心", R.drawable.ic_brand_wenxin),
    DOUBAO("字节跳动豆包", R.drawable.ic_brand_doubao),
    META_LLAMA("Meta Llama", R.drawable.ic_brand_meta),
    MISTRAL("Mistral", R.drawable.ic_brand_mistral),
    OPENCODE_ZEN("OpenCode Zen", R.drawable.ic_brand_opencode),
    QODER("Qoder", R.drawable.ic_brand_qoder),
    UNKNOWN_BOT("模型", null),
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
 * Square brand logo badge for LLM models using official brand icons in zinc low-saturation container.
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
    val resId = brand.iconRes

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (resId != null) {
            Image(
                painter = painterResource(resId),
                contentDescription = brand.label,
                modifier = Modifier
                    .size(size * 0.72f)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = brand.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
