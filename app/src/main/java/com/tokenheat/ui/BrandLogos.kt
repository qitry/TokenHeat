package com.tokenheat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokenheat.R
import com.tokenheat.proto.HubModel
import com.tokenheat.proto.Provider

/**
 * Recognizable AI model family brand identity mapped to official light & dark assets.
 * Pure model families only (no vendors/providers mixed in).
 */
enum class ModelBrand(
    val label: String,
    val lightIconRes: Int? = null,
    val darkIconRes: Int? = null,
) {
    OPENAI("OpenAI", R.drawable.ic_model_openai_light, R.drawable.ic_model_openai_dark),
    ANTHROPIC("Claude", R.drawable.ic_model_claude_light, R.drawable.ic_model_claude_dark),
    GOOGLE_GEMINI("Gemini", R.drawable.ic_model_gemini_light, R.drawable.ic_model_gemini_dark),
    DEEPSEEK("DeepSeek", R.drawable.ic_model_deepseek_light, R.drawable.ic_model_deepseek_dark),
    ZHIPU_GLM("智谱 GLM", R.drawable.ic_model_zhipu_light, R.drawable.ic_model_zhipu_dark),
    QWEN("通义千问", R.drawable.ic_model_qwen_light, R.drawable.ic_model_qwen_dark),
    HUNYUAN("腾讯混元", R.drawable.ic_model_hunyuan_light, R.drawable.ic_model_hunyuan_dark),
    ERNIE("百度文心", R.drawable.ic_model_wenxin_light, R.drawable.ic_model_wenxin_dark),
    DOUBAO("字节豆包", R.drawable.ic_model_doubao_light, R.drawable.ic_model_doubao_dark),
    META_LLAMA("Meta Llama", R.drawable.ic_model_meta_light, R.drawable.ic_model_meta_dark),
    MISTRAL("Mistral", R.drawable.ic_model_mistral_light, R.drawable.ic_model_mistral_dark),
    UNKNOWN_BOT("未知模型", null, null),
}

/**
 * Upstream provider account platform brands mapped to official light & dark assets.
 * Strictly used in credential & provider management screens.
 */
enum class ProviderBrand(
    val label: String,
    val lightIconRes: Int,
    val darkIconRes: Int,
) {
    WORKBUDDY("WorkBuddy", R.drawable.ic_provider_workbuddy_light, R.drawable.ic_provider_workbuddy_dark),
    ZCODE("ZCode", R.drawable.ic_provider_zcode_light, R.drawable.ic_provider_zcode_dark),
    ZEN("Zen", R.drawable.ic_provider_zen_light, R.drawable.ic_provider_zen_dark),
    QODER("Qoder", R.drawable.ic_provider_qoder_light, R.drawable.ic_provider_qoder_dark),
    ANTIGRAVITY("Antigravity CLI", R.drawable.ic_provider_antigravity_light, R.drawable.ic_provider_antigravity_dark),
    ;

    companion object {
        fun from(provider: Provider): ProviderBrand = when (provider) {
            Provider.WORKBUDDY -> WORKBUDDY
            Provider.ZCODE -> ZCODE
            Provider.ZEN -> ZEN
            Provider.QODER_CN, Provider.QODER_GLOBAL -> QODER
            Provider.ANTIGRAVITY -> ANTIGRAVITY
        }

        fun from(group: ProviderGroup): ProviderBrand = when (group) {
            ProviderGroup.WORKBUDDY -> WORKBUDDY
            ProviderGroup.ZCODE -> ZCODE
            ProviderGroup.ZEN -> ZEN
            ProviderGroup.QODER -> QODER
            ProviderGroup.ANTIGRAVITY -> ANTIGRAVITY
        }
    }
}

/**
 * Resolves a model to its authentic model brand, stripping vendor route prefixes
 * so models never inherit vendor identities.
 */
object BrandResolver {
    fun resolve(model: HubModel): ModelBrand {
        return resolve(cleanModelId(model.id), model.name)
    }

    fun resolve(modelId: String): ModelBrand {
        return resolve(cleanModelId(modelId), "")
    }

    private fun cleanModelId(rawId: String): String {
        val slashIdx = rawId.indexOf('/')
        return if (slashIdx >= 0) rawId.substring(slashIdx + 1) else rawId
    }

    private fun resolve(cleanId: String, name: String): ModelBrand {
        val text = "$name $cleanId".lowercase()
        return when {
            text.contains("deepseek") -> ModelBrand.DEEPSEEK
            text.contains("claude") || text.contains("anthropic") -> ModelBrand.ANTHROPIC
            text.contains("gpt") || text.contains("openai") || text.contains("chatgpt") ||
                text.contains("o1") || text.contains("o3") -> ModelBrand.OPENAI
            text.contains("gemini") -> ModelBrand.GOOGLE_GEMINI
            text.contains("glm") || text.contains("chatglm") -> ModelBrand.ZHIPU_GLM
            text.contains("qwen") || text.contains("千问") -> ModelBrand.QWEN
            text.contains("hunyuan") || text.contains("混元") -> ModelBrand.HUNYUAN
            text.contains("ernie") || text.contains("文心") -> ModelBrand.ERNIE
            text.contains("doubao") || text.contains("豆包") -> ModelBrand.DOUBAO
            text.contains("llama") -> ModelBrand.META_LLAMA
            text.contains("mistral") || text.contains("codestral") || text.contains("pixtral") -> ModelBrand.MISTRAL
            else -> ModelBrand.UNKNOWN_BOT
        }
    }
}

/**
 * Square brand logo badge for LLM models with adaptive Light / Dark theme support.
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
    val shape = IconSquircleShape
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val iconRes = if (isDark) brand.darkIconRes else brand.lightIconRes

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = brand.label,
                modifier = Modifier
                    .size(size * 0.72f)
                    .clip(IconSquircleShape),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Lucide.Bot,
                contentDescription = brand.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/**
 * Square brand logo badge for account providers with adaptive Light / Dark theme support.
 * Displayed exclusively in provider and credential settings.
 */
@Composable
fun ProviderBrandBadge(
    provider: Provider,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    ProviderBrandBadgeContent(ProviderBrand.from(provider), modifier, size)
}

@Composable
fun ProviderBrandBadge(
    group: ProviderGroup,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    ProviderBrandBadgeContent(ProviderBrand.from(group), modifier, size)
}

@Composable
private fun ProviderBrandBadgeContent(
    brand: ProviderBrand,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val shape = IconSquircleShape
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val iconRes = if (isDark) brand.darkIconRes else brand.lightIconRes

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = brand.label,
            modifier = Modifier
                .size(size * 0.72f)
                .clip(IconSquircleShape),
            contentScale = ContentScale.Fit,
        )
    }
}
