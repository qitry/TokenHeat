package com.tokenheat.data

import android.content.Context
import com.tokenheat.ui.AttachmentType
import com.tokenheat.ui.ChatAttachment
import com.tokenheat.ui.ChatMessage
import com.tokenheat.ui.ChatRole
import com.tokenheat.ui.ChatToolCall
import com.tokenheat.ui.ChatConversation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists and loads chat conversations to and from internal JSON storage.
 */
class ChatConversationStore(context: Context) {

    private val file = File(context.filesDir, "tokenheat-chat-conversations.json")
    private val lock = Any()

    fun load(): List<ChatConversation> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return runCatching {
                val array = JSONArray(file.readText())
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    parseConversation(obj)
                }
            }.getOrDefault(emptyList())
        }
    }

    fun save(conversations: List<ChatConversation>) {
        synchronized(lock) {
            runCatching {
                val array = JSONArray()
                conversations.forEach { conv ->
                    array.put(serializeConversation(conv))
                }
                file.writeText(array.toString())
            }
        }
    }

    private fun parseConversation(obj: JSONObject): ChatConversation {
        val messagesArr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until messagesArr.length()).mapNotNull { i ->
            val m = messagesArr.optJSONObject(i) ?: return@mapNotNull null
            val roleStr = m.optString("role", "USER")
            val role = runCatching { ChatRole.valueOf(roleStr) }.getOrDefault(ChatRole.USER)

            val attachmentsArr = m.optJSONArray("attachments") ?: JSONArray()
            val attachments = (0 until attachmentsArr.length()).mapNotNull { j ->
                val a = attachmentsArr.optJSONObject(j) ?: return@mapNotNull null
                val typeStr = a.optString("type", "IMAGE")
                val type = runCatching { AttachmentType.valueOf(typeStr) }.getOrDefault(AttachmentType.IMAGE)
                ChatAttachment(
                    id = a.optString("id"),
                    type = type,
                    name = a.optString("name"),
                    sizeBytes = a.optLong("sizeBytes"),
                    mimeType = a.optString("mimeType"),
                    textContent = a.optString("textContent"),
                    base64Data = a.optString("base64Data"),
                )
            }

            val toolsArr = m.optJSONArray("toolCalls") ?: JSONArray()
            val toolCalls = (0 until toolsArr.length()).mapNotNull { k ->
                val t = toolsArr.optJSONObject(k) ?: return@mapNotNull null
                ChatToolCall(
                    id = t.optString("id"),
                    name = t.optString("name"),
                    arguments = t.optString("arguments"),
                    result = if (t.has("result")) t.optString("result") else null,
                    isExecuting = false,
                )
            }

            ChatMessage(
                id = m.optString("id", java.util.UUID.randomUUID().toString()),
                role = role,
                content = m.optString("content"),
                reasoningContent = m.optString("reasoningContent"),
                timestamp = m.optLong("timestamp", System.currentTimeMillis()),
                modelId = if (m.has("modelId")) m.optString("modelId") else null,
                isError = m.optBoolean("isError", false),
                isStreaming = false,
                attachments = attachments,
                toolCalls = toolCalls,
            )
        }

        return ChatConversation(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            title = obj.optString("title", "新对话"),
            modelId = if (obj.has("modelId")) obj.optString("modelId") else null,
            messages = messages,
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun serializeConversation(conv: ChatConversation): JSONObject {
        val obj = JSONObject()
        obj.put("id", conv.id)
        obj.put("title", conv.title)
        conv.modelId?.let { obj.put("modelId", it) }
        obj.put("createdAt", conv.createdAt)
        obj.put("updatedAt", conv.updatedAt)

        val messagesArr = JSONArray()
        conv.messages.forEach { msg ->
            val m = JSONObject()
            m.put("id", msg.id)
            m.put("role", msg.role.name)
            m.put("content", msg.content)
            m.put("reasoningContent", msg.reasoningContent)
            m.put("timestamp", msg.timestamp)
            msg.modelId?.let { m.put("modelId", it) }
            m.put("isError", msg.isError)

            if (msg.attachments.isNotEmpty()) {
                val attArr = JSONArray()
                msg.attachments.forEach { att ->
                    val a = JSONObject()
                    a.put("id", att.id)
                    a.put("type", att.type.name)
                    a.put("name", att.name)
                    a.put("sizeBytes", att.sizeBytes)
                    a.put("mimeType", att.mimeType)
                    a.put("textContent", att.textContent)
                    a.put("base64Data", att.base64Data)
                    attArr.put(a)
                }
                m.put("attachments", attArr)
            }

            if (msg.toolCalls.isNotEmpty()) {
                val tArr = JSONArray()
                msg.toolCalls.forEach { t ->
                    val tc = JSONObject()
                    tc.put("id", t.id)
                    tc.put("name", t.name)
                    tc.put("arguments", t.arguments)
                    t.result?.let { tc.put("result", it) }
                    tArr.put(tc)
                }
                m.put("toolCalls", tArr)
            }

            messagesArr.put(m)
        }
        obj.put("messages", messagesArr)

        return obj
    }
}
