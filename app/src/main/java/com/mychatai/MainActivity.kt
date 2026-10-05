package com.mychatai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyChatAiApp() }
    }
}

enum class Provider { CHATGPT, CLAUDE }
data class ChatMessage(val role: String, val text: String)

@Composable
fun MyChatAiApp() {
    val ctx = LocalContext.current
    var provider by remember { mutableStateOf(Provider.CHATGPT) }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var messages by remember { mutableStateOf(loadMessages(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val info = readAttachment(ctx, uri)
                    input = input.copy(
                        text = input.text + "\n\n[ملف مرفوع: \${info.first}]\n\${info.second}\n"
                    )
                } catch (e: Exception) {
                    status = "تعذر قراءة الملف: \${e.message}"
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(17, 17, 17),
            surface = Color(28, 28, 28),
            primary = Color(160, 160, 160)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(17, 17, 17))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MyChatAi",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = { showSettings = true }) {
                    Text("⚙", color = Color.White)
                }

                AssistChip(
                    onClick = {
                        provider = if (provider == Provider.CHATGPT) Provider.CLAUDE else Provider.CHATGPT
                    },
                    label = {
                        Text(if (provider == Provider.CHATGPT) "GPT-6 Pro" else "Claude Opus 5.5")
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (message.role == "user") Color(55, 55, 55) else Color(30, 30, 30)
                        ) {
                            Text(message.text, Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                }
                if (busy) {
                    item { Text("جاري التفكير…", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                }
            }

            if (status.isNotEmpty()) {
                Text(status, color = Color(220, 120, 120), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("＋", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اكتب رسالتك…") },
                    maxLines = 6
                )

                Spacer(Modifier.width(6.dp))

                Button(
                    enabled = !busy && input.text.isNotBlank(),
                    onClick = {
                        val userText = input.text.trim()
                        input = TextFieldValue()
                        val updated = messages + ChatMessage("user", userText)
                        messages = updated
                        saveMessages(ctx, updated)
                        busy = true
                        status = ""

                        scope.launch {
                            try {
                                val answer = ApiClient.ask(provider, updated, apiKey(ctx, provider))
                                val result = messages + ChatMessage("assistant", answer)
                                messages = result
                                saveMessages(ctx, result)
                            } catch (e: Exception) {
                                status = e.message ?: "حدث خطأ"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("إرسال")
                }
            }
        }

        if (showSettings) {
            ApiKeyDialog(ctx) { showSettings = false }
        }
    }
}

@Composable
fun ApiKeyDialog(ctx: Context, onDone: () -> Unit) {
    var openai by remember { mutableStateOf(apiKey(ctx, Provider.CHATGPT)) }
    var claude by remember { mutableStateOf(apiKey(ctx, Provider.CLAUDE)) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("مفاتيح API") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(openai, { openai = it }, label = { Text("OpenAI API key") }, singleLine = true)
                OutlinedTextField(claude, { claude = it }, label = { Text("Anthropic API key") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                ctx.getSharedPreferences("keys", Context.MODE_PRIVATE).edit()
                    .putString("openai", openai.trim())
                    .putString("anthropic", claude.trim())
                    .apply()
                onDone()
            }) { Text("حفظ") }
        }
    )
}

fun apiKey(ctx: Context, provider: Provider): String =
    ctx.getSharedPreferences("keys", Context.MODE_PRIVATE)
        .getString(if (provider == Provider.CHATGPT) "openai" else "anthropic", "") ?: ""

fun loadMessages(ctx: Context): List<ChatMessage> {
    val json = ctx.getSharedPreferences("chat", Context.MODE_PRIVATE).getString("messages", "[]") ?: "[]"
    val array = JSONArray(json)
    return (0 until array.length()).map {
        val obj = array.getJSONObject(it)
        ChatMessage(obj.getString("role"), obj.getString("text"))
    }
}

fun saveMessages(ctx: Context, messages: List<ChatMessage>) {
    val array = JSONArray()
    messages.forEach { array.put(JSONObject().put("role", it.role).put("text", it.text)) }
    ctx.getSharedPreferences("chat", Context.MODE_PRIVATE).edit().putString("messages", array.toString()).apply()
}

suspend fun readAttachment(ctx: Context, uri: Uri): Pair<String, String> {
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    val ext = name.substringAfterLast('.', "").lowercase()

    val text = when {
        ext in setOf("txt","md","json","xml","csv","kt","java","py","js","ts","html","css","log","properties","gradle","yml","yaml") ->
            bytes.toString(Charsets.UTF_8).take(120000)

        ext in setOf("zip","apk","xapk","apkm","apks") -> {
            val tmp = File(ctx.cacheDir, name)
            tmp.writeBytes(bytes)
            val sb = StringBuilder("Archive entries:\n")
            ZipFile(tmp).use { zip ->
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements() && count < 500) {
                    sb.append(entries.nextElement().name).append('\n')
                    count++
                }
            }
            sb.toString()
        }

        else -> "تم إرفاق الملف. الاسم=\$name، الحجم=\${bytes.size} bytes"
    }

    return name to text
}

object ApiClient {
    private val http = OkHttpClient()

    suspend fun ask(provider: Provider, messages: List<ChatMessage>, key: String): String {
        if (key.isBlank()) throw IllegalStateException("ضع مفتاح API في الإعدادات أولاً")
        return if (provider == Provider.CHATGPT) openai(messages, key) else claude(messages, key)
    }

    private fun openai(messages: List<ChatMessage>, key: String): String {
        val input = JSONArray()
        messages.takeLast(80).forEach {
            input.put(JSONObject()
                .put("role", if (it.role == "user") "user" else "assistant")
                .put("content", it.text))
        }

        val body = JSONObject()
            .put("model", "gpt-6-astra")
            .put("input", input)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val response = http.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .addHeader("Authorization", "Bearer \$key")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        ).execute()

        val raw = response.body?.string() ?: throw Exception("OpenAI: Empty response")
        if (!response.isSuccessful) throw Exception("OpenAI \${response.code}: \$raw")

        val json = JSONObject(raw)
        val outputText = json.optString("output_text")
        if (outputText.isNotBlank()) return outputText

        val output = json.optJSONArray("output") ?: throw Exception("OpenAI: no output")
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") result.append(part.optString("text"))
            }
        }
        return result.toString().ifBlank { raw }
    }

    private fun claude(messages: List<ChatMessage>, key: String): String {
        val array = JSONArray()
        messages.takeLast(80).forEach {
            array.put(JSONObject()
                .put("role", if (it.role == "user") "user" else "assistant")
                .put("content", it.text))
        }

        val body = JSONObject()
            .put("model", "claude-opus-5-5")
            .put("max_tokens", 4096)
            .put("messages", array)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val response = http.newCall(
            Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", key)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        ).execute()

        val raw = response.body?.string() ?: throw Exception("Claude: Empty response")
        if (!response.isSuccessful) throw Exception("Claude \${response.code}: \$raw")

        val content = JSONObject(raw).optJSONArray("content") ?: throw Exception("Claude: no content")
        val result = StringBuilder()
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") result.append(item.optString("text"))
        }
        return result.toString().ifBlank { raw }
    }
}
