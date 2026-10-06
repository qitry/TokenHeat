package com.tokenheat.proto

/**
 * Which upstream account system a credential belongs to.
 *
 * WorkBuddy and ZCode are unrelated account systems with different login
 * flows, token shapes and chat endpoints, so the provider travels with every
 * credential and selects the code path at each step.
 */
enum class Provider(val label: String) {
    WORKBUDDY("WorkBuddy"),
    ZCODE("ZCode"),
}
