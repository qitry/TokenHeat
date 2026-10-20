// Copyright 2026, LyraProxy contributors
// SPDX-License-Identifier: Apache-2.0

package app.anylyralabs.lyraproxy.ui.chat

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

/**
 * 网络工具的 API Key。
 *
 * Exa / Firecrawl 都需要密钥，没有免密钥模式；[webToolsAvailable] 为 false 时聊天照常进行，
 * 只是模型拿到的工具结果是「不可用」，[ChatScreen] 也会在界面上标明。
 */
data class ChatKeys(
    val exa: String = "",
    val firecrawl: String = "",
) {
    val webToolsAvailable: Boolean get() = exa.isNotBlank() || firecrawl.isNotBlank()

    companion object {
        private const val PREFS = "lyra_chat"
        private const val KEY_EXA = "exa_key"
        private const val KEY_FIRECRAWL = "firecrawl_key"

        fun load(context: Context): ChatKeys {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return ChatKeys(
                exa = prefs.getString(KEY_EXA, "").orEmpty(),
                firecrawl = prefs.getString(KEY_FIRECRAWL, "").orEmpty(),
            )
        }

        fun save(context: Context, keys: ChatKeys) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EXA, keys.exa.trim())
                .putString(KEY_FIRECRAWL, keys.firecrawl.trim())
                .apply()
        }
    }
}

/**
 * 网络工具设置面板。挂在 [ChatScreen] 的根布局里，通过 `rememberMochiSheetState().show()` 打开。
 *
 * 输入即保存（`exa_key` / `firecrawl_key`），这样下滑关闭、返回关闭都不会丢。
 * 每次面板重新显示时从 SharedPreferences 读一次，避免多个入口之间状态不同步。
 */
@Composable
fun ChatSettingsSheet(
    state: MochiSheetState,
    onDismiss: () -> Unit,
    onKeysChanged: (ChatKeys) -> Unit,
) {
    val context = LocalContext.current
    val initial = remember { ChatKeys.load(context) }
    var exa by remember { mutableStateOf(initial.exa) }
    var firecrawl by remember { mutableStateOf(initial.firecrawl) }

    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            val keys = ChatKeys.load(context)
            exa = keys.exa
            firecrawl = keys.firecrawl
        }
    }

    fun persist(nextExa: String, nextFirecrawl: String) {
        val keys = ChatKeys(nextExa.trim(), nextFirecrawl.trim())
        ChatKeys.save(context, keys)
        onKeysChanged(keys)
    }

    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    val available = exa.isNotBlank() || firecrawl.isNotBlank()

    MochiSheet(state = state, onDismiss = onDismiss) {
        MochiSheetHeader(
            title = "网络工具",
            actions = { MochiBarButton("完成", onClick = { state.hide() }) },
        )
        MochiSheetContent {
            MochiListSection(
                header = "API Key",
                footer = if (available) {
                    "已配置：模型需要时可以调用 Exa / Firecrawl 搜索，并抓取网页正文。"
                } else {
                    "未配置时网络工具不可用：聊天不受影响，模型会直接回答，不会发起联网请求。"
                },
            ) {
                MochiListTextFieldRow(
                    value = exa,
                    onValueChange = {
                        exa = it
                        persist(it, firecrawl)
                    },
                    label = "Exa",
                    placeholder = "x-api-key",
                    secure = true,
                )
                MochiListTextFieldRow(
                    value = firecrawl,
                    onValueChange = {
                        firecrawl = it
                        persist(exa, it)
                    },
                    label = "Firecrawl",
                    placeholder = "fc-…",
                    secure = true,
                )
            }

            MochiListSection(header = "状态") {
                MochiListRow("Exa", value = if (exa.isNotBlank()) "已配置" else "未配置")
                MochiListRow("Firecrawl", value = if (firecrawl.isNotBlank()) "已配置" else "未配置")
            }

            Text(
                "两家搜索服务都没有免密钥模式。只填一家也能用：搜索会优先走已配置的那家，失败时再尝试另一家。",
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                style = ts.footnote,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
