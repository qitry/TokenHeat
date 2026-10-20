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
) {
    /** 客户端可见的模型 ID：展示名去掉空白。 */
    val id: String get() = displayName.replace(Regex("\\s+"), "").ifEmpty { key }
}

/** 转成界面通用的模型行。 */
fun QoderModel.asHubModel(): HubModel = HubModel(
    id = id,
    name = displayName,
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
