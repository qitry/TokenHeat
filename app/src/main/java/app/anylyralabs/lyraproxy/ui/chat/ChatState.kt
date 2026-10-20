// Copyright 2026, LyraProxy contributors
// SPDX-License-Identifier: Apache-2.0

package app.anylyralabs.lyraproxy.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import java.util.UUID

/**
 * 搜索结果 / 网页抓取来源。
 *
 * [domain] 只用于消息末尾的圆形首字母头像，取 host 去掉 `www.`。
 */
@Immutable
data class ChatSource(
    val title: String,
    val url: String,
    val domain: String,
) {
    companion object {
        /** 用标题 + 链接构造，标题为空时退回域名。 */
        fun of(title: String, url: String): ChatSource {
            val host = domainOf(url)
            return ChatSource(title = title.ifBlank { host }, url = url, domain = host)
        }

        /** 从链接里取域名；解析失败时做一次朴素的字符串裁剪。 */
        fun domainOf(url: String): String {
            val host = runCatching { java.net.URI(url).host }.getOrNull()
                ?: url.substringAfter("://", url).substringBefore('/').substringBefore(':')
            return host.removePrefix("www.").ifBlank { "web" }
        }
    }
}

/**
 * 一条聊天消息。
 *
 * [role] 只用 [ROLE_USER] / [ROLE_ASSISTANT]；工具消息由 [ChatEngine] 内部维护，不进入会话列表。
 * [streaming] 为 true 时表示这条回复还在接收；[error] 非空时在气泡下方用警示色显示。
 */
@Immutable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String = "",
    val reasoning: String = "",
    val sources: List<ChatSource> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun user(content: String): ChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ROLE_USER,
            content = content,
        )

        fun assistant(id: String = UUID.randomUUID().toString()): ChatMessage = ChatMessage(
            id = id,
            role = ROLE_ASSISTANT,
            streaming = true,
        )
    }
}

/**
 * 一个会话。标题是可变状态（首条消息后自动命名、侧边栏可改名）；消息用 snapshot 列表，流式追加时自动重组。
 */
@Stable
class ChatSession(
    val id: String,
    title: String,
    messages: List<ChatMessage> = emptyList(),
) {
    var title: String by mutableStateOf(title)

    val messages: SnapshotStateList<ChatMessage> = messages.toMutableStateList()

    fun append(message: ChatMessage) {
        messages.add(message)
    }

    /** 按 id 原地更新（流式 delta、来源、错误都走这里）。 */
    fun update(id: String, transform: (ChatMessage) -> ChatMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = transform(messages[index])
    }

    fun remove(id: String) {
        messages.removeAll { it.id == id }
    }

    /** 停止时把仍在流式的消息收尾，避免界面一直转圈。 */
    fun finishStreaming() {
        for (i in messages.indices) {
            if (messages[i].streaming) messages[i] = messages[i].copy(streaming = false)
        }
    }
}

/**
 * 多会话的纯内存容器：不写数据库，只在进程内保存。
 * 侧边栏的会话标题通过 [Saver] 交给 rememberSaveable，进程被回收后标题还在，消息会丢失（这是有意的取舍）。
 */
@Stable
class ChatStore(
    initial: List<ChatSession> = emptyList(),
    initialCurrentId: String? = null,
) {
    private val _sessions = mutableStateListOf<ChatSession>().apply { addAll(initial) }

    val sessions: List<ChatSession> get() = _sessions

    var currentId: String by mutableStateOf(initialCurrentId ?: initial.firstOrNull()?.id.orEmpty())
        private set

    init {
        // 保证任何时候都至少有一个会话：筛选、发送、删除都不用处理 null
        if (_sessions.isEmpty()) {
            newSession()
        } else if (_sessions.none { it.id == currentId }) {
            currentId = _sessions.first().id
        }
    }

    val current: ChatSession
        get() = _sessions.firstOrNull { it.id == currentId } ?: _sessions.first()

    fun newSession(title: String = DEFAULT_TITLE): ChatSession {
        val session = ChatSession(id = UUID.randomUUID().toString(), title = title)
        _sessions.add(0, session)
        currentId = session.id
        return session
    }

    /** 至少保证有一个会话可选。 */
    fun ensureSession(): ChatSession = current

    fun select(id: String) {
        if (_sessions.any { it.id == id }) currentId = id
    }

    fun delete(id: String) {
        val index = _sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        _sessions.removeAt(index)
        if (_sessions.isEmpty()) {
            newSession()
        } else if (currentId == id) {
            currentId = _sessions[minOf(index, _sessions.lastIndex)].id
        }
    }

    /** 首条用户消息自动命名，仍然叫「新对话」的才改，手动改过的不覆盖。 */
    fun autoTitle(session: ChatSession, text: String) {
        if (session.title != DEFAULT_TITLE) return
        val title = deriveTitle(text)
        if (title.isNotBlank()) session.title = title
    }

    companion object {
        const val DEFAULT_TITLE = "新对话"

        fun deriveTitle(text: String, maxLength: Int = 18): String =
            text.replace(Regex("\\s+"), " ").trim().take(maxLength)

        /**
         * 只把「当前会话 id + 每个会话的 id / 标题」交给 rememberSaveable。
         * 保存结果必须是 Bundle 可写的 ArrayList<String>。
         */
        val Saver: Saver<ChatStore, ArrayList<String>> = Saver(
            save = { store ->
                val out = ArrayList<String>(1 + store.sessions.size * 2)
                out.add(store.currentId)
                store.sessions.forEach { session ->
                    out.add(session.id)
                    out.add(session.title)
                }
                out
            },
            restore = { saved ->
                val restored = ArrayList<ChatSession>()
                var i = 1
                while (i + 1 < saved.size) {
                    val id = saved[i]
                    val title = saved[i + 1]
                    if (id.isNotBlank()) {
                        restored.add(ChatSession(id = id, title = title.ifBlank { DEFAULT_TITLE }))
                    }
                    i += 2
                }
                ChatStore(initial = restored, initialCurrentId = saved.firstOrNull().orEmpty())
            },
        )
    }
}

@Composable
fun rememberChatStore(): ChatStore = rememberSaveable(saver = ChatStore.Saver) {
    ChatStore().apply { ensureSession() }
}
