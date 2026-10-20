package app.anylyralabs.lyraproxy.qoder

import app.anylyralabs.lyraproxy.proto.HubModel

/**
 * 一个 Qoder 账号。
 *
 * 与 WorkBuddy 的凭证分开建模：Qoder 用的是自己的设备码令牌体系，
 * 字段和刷新方式都不同，混用会让两边的过期判断互相污染。
 */
data class QoderAccount(
    val id: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    /** 绝对毫秒时间戳；0 表示未知。 */
    val expiresAt: Long,
    val machineId: String,
    val source: String = "oauth",
)

/** 模型目录条目，字段直接来自 Qoder 的 model/list 响应。 */
data class QoderModel(
    /** 上游用于指定模型的 key（如 qmodel_latest）。 */
    val key: String,
    /** 展示名，同时是客户端看到的模型 ID（去掉空格后）。 */
    val displayName: String,
    val supportsImages: Boolean,
    val supportsReasoning: Boolean,
    val contextWindow: Int,
    /** 计费倍率；0 表示目录未提供。 */
    val priceFactor: Double = 0.0,
    /** 上游目录里的原始条目，聊天时需要原样回传。 */
    val raw: String = "",
) {
    /** 客户端可见的模型 ID：展示名去掉空白。 */
    val id: String get() = displayName.replace(Regex("\\s+"), "").ifEmpty { key }
}

/** 转成界面通用的模型行。 */
fun QoderModel.asHubModel(): HubModel = HubModel(
    id = id,
    name = displayName,
    rate = if (priceFactor > 0.0) String.format(java.util.Locale.US, "x%.2f", priceFactor) else "",
    // 未知倍率用 -1，避免被当成「免费」。
    multiplier = if (priceFactor > 0.0) priceFactor else -1.0,
    vendor = "Qoder",
    supportsImages = supportsImages,
    supportsReasoning = supportsReasoning,
    contextWindow = contextWindow,
)

/**
 * 设备码登录会话。
 *
 * [verifier] 是 PKCE 的秘密，只在轮询时使用；[authUrl] 给用户打开浏览器。
 */
data class QoderDeviceFlow(
    val authUrl: String,
    val nonce: String,
    val verifier: String,
    val machineId: String,
)

/** 设备码轮询的结果。 */
sealed class QoderPoll {
    data object Pending : QoderPoll()
    data class Done(val account: QoderAccount) : QoderPoll()
    data class Failed(val message: String) : QoderPoll()
}

/** 连通性测试的原始结果，用于排查签名/权限问题。 */
data class QoderProbe(
    val ok: Boolean,
    val summary: String,
    val detail: String,
)

/** 一个额度桶：总量、已用、剩余都可能是未知。 */
data class QoderQuota(
    val total: Double? = null,
    val used: Double? = null,
    val remaining: Double? = null,
    val unit: String = "",
) {
    /** 已用比例，用于进度条；缺少总量时返回 null。 */
    val fraction: Float?
        get() {
            val totalValue = total ?: return null
            if (totalValue <= 0.0) return null
            val usedValue = used ?: (totalValue - (remaining ?: 0.0))
            return (usedValue / totalValue).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Qoder 的额度快照。
 *
 * 与 WorkBuddy 的余额是两套东西：这里按「用户额度 / 加赠额度 / 组织资源包」
 * 三类展示，单位由上游给出。
 */
data class QoderUsage(
    val userQuota: QoderQuota? = null,
    val addOnQuota: QoderQuota? = null,
    val orgQuota: QoderQuota? = null,
    /** 额度重置或到期时间，毫秒；0 表示未知。 */
    val expiresAt: Long = 0L,
) {
    val hasAny: Boolean get() = userQuota != null || addOnQuota != null || orgQuota != null
}
