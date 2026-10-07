package com.tokenheat.proto

/**
 * Which upstream account system a credential belongs to.
 *
 * WorkBuddy and ZCode are unrelated account systems with different login
 * flows, token shapes and chat endpoints, so the provider travels with every
 * credential and selects the code path at each step.
 */
enum class Provider(val label: String, val routeKey: String) {
    WORKBUDDY("WorkBuddy", "workbuddy"),
    ZCODE("ZCode", "zcode"),
    ZEN("Zen", "zen"),
    QODER_CN("Qoder 国内版", "qoder-cn"),
    QODER_GLOBAL("Qoder 国际版", "qoder-global"),
    ANTIGRAVITY("Antigravity CLI", "antigravity"),
    ;

    companion object {
        /** Parses a `provider/model` id head back into its provider. */
        fun byRouteKey(key: String): Provider? = entries.firstOrNull { it.routeKey == key }
    }
}
