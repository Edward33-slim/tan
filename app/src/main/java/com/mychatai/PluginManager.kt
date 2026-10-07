package com.mychatai

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PluginEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val type: String,
    val token: String,
    val read: Boolean = true,
    val create: Boolean = true,
    val update: Boolean = true,
    val delete: Boolean = true
)

@Composable
fun PluginManagerDialog(ctx: Context, onDismiss: () -> Unit) {
    var plugins by remember { mutableStateOf(loadPlugins(ctx)) }
    var showAdd by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(list: List<PluginEntry>) {
        plugins = list
        savePlugins(ctx, list)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("المكونات الإضافية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "الأذونات",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "تحدد هذه الأذونات ما يمكن للمكون المتصل تنفيذه. لا يمكن للمكون تجاوز صلاحيات الخدمة نفسها.",
                    color = Color.LightGray
                )

                if (plugins.isEmpty()) {
                    Text("لا توجد مكونات مضافة.", color = Color.Gray)
                }

                plugins.forEach { p ->
                    PluginRow(
                        plugin = p,
                        busy = busyId == p.id,
                        onPermission = { next -> save(plugins.map { if (it.id == p.id) next else it }) },
                        onConnect = {
                            busyId = p.id
                            message = ""
                            scope.launch {
                                try {
                                    val result = PluginApi.test(p)
                                    message = p.name + ": " + result
                                } catch (e: Exception) {
                                    message = p.name + ": " + (e.message ?: "فشل الاتصال")
                                } finally {
                                    busyId = null
                                }
                            }
                        },
                        onDelete = {
                            save(plugins.filterNot { it.id == p.id })
                            message = "تمت إزالة " + p.name
                        }
                    )
                }

                Button(
                    onClick = { showCatalog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تصفح المكونات الإضافية") }

                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إضافة مكون إضافي") }

                if (message.isNotBlank()) {
                    Text(message, color = Color(240, 200, 120))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("حفظ وإغلاق") }
        }
    )

    if (showAdd) {
        AddPluginDialog(
            onDismiss = { showAdd = false },
            onSave = {
                save(plugins + it)
                showAdd = false
                message = "تمت إضافة " + it.name
            }
        )
    }

    if (showCatalog) {
        PluginCatalogDialog(
            existing = plugins,
            onDismiss = { showCatalog = false },
            onAdd = {
                if (plugins.none { it.type == it.type }) {
                    save(plugins + it)
                } else {
                    save(plugins + it.copy(id = UUID.randomUUID().toString()))
                }
                showCatalog = false
                message = "تمت إضافة " + it.name + ". أدخل رمز الوصول ثم اضغط اتصال."
            }
        )
    }
}

@Composable
private fun PluginRow(
    plugin: PluginEntry,
    busy: Boolean,
    onPermission: (PluginEntry) -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(plugin.name)
                    Text(plugin.url, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (plugin.token.isBlank()) "غير متصل" else "رمز محفوظ", color = Color.LightGray)
            }
            PermissionSwitch("قراءة", plugin.read) { onPermission(plugin.copy(read = it)) }
            PermissionSwitch("إنشاء", plugin.create) { onPermission(plugin.copy(create = it)) }
            PermissionSwitch("تعديل", plugin.update) { onPermission(plugin.copy(update = it)) }
            PermissionSwitch("حذف", plugin.delete) { onPermission(plugin.copy(delete = it)) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onConnect, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(if (busy) "جارٍ الاتصال…" else "اختبار الاتصال")
                }
                TextButton(onClick = onDelete) { Text("إزالة") }
            }
        }
    }
}

@Composable
private fun PermissionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AddPluginDialog(onDismiss: () -> Unit, onSave: (PluginEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مكون إضافي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("رابط API") }, singleLine = true)
                OutlinedTextField(
                    token, { token = it },
                    label = { Text("رمز الوصول") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text(
                    "سيتم استخدام الرمز فقط مع هذا المكون. استخدم API رسميًا للخدمة.",
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onSave(
                        PluginEntry(
                            name = name.trim(),
                            url = url.trim().removeSuffix("/"),
                            type = "custom",
                            token = token.trim()
                        )
                    )
                }
            ) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PluginCatalogDialog(
    existing: List<PluginEntry>,
    onDismiss: () -> Unit,
    onAdd: (PluginEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصفح المكونات الإضافية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogItem(
                    "GitHub",
                    "https://api.github.com",
                    "GitHub REST API",
                    existing.any { it.type == "github" }
                ) {
                    onAdd(
                        PluginEntry(
                            name = "GitHub",
                            url = "https://api.github.com",
                            type = "github",
                            token = ""
                        )
                    )
                }
                CatalogItem(
                    "Dropbox",
                    "https://api.dropboxapi.com",
                    "Dropbox API",
                    existing.any { it.type == "dropbox" }
                ) {
                    onAdd(
                        PluginEntry(
                            name = "Dropbox",
                            url = "https://api.dropboxapi.com",
                            type = "dropbox",
                            token = ""
                        )
                    )
                }
                Text(
                    "يمكن إضافة خدمات أخرى من خلال إضافة مكون API مخصص باستخدام API رسمي.",
                    color = Color.Gray
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun CatalogItem(
    name: String,
    url: String,
    description: String,
    added: Boolean,
    onAdd: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name)
                Text(description, color = Color.Gray)
            }
            Button(onClick = onAdd, enabled = !added) {
                Text(if (added) "مضاف" else "إضافة")
            }
        }
    }
}

object PluginApi {
    private val http = OkHttpClient()

    suspend fun test(plugin: PluginEntry): String = withContext(Dispatchers.IO) {
        if (plugin.token.isBlank()) {
            throw IllegalStateException("أدخل رمز الوصول أولاً")
        }
        when (plugin.type) {
            "github" -> {
                val r = http.newCall(
                    Request.Builder()
                        .url("https://api.github.com/user")
                        .addHeader("Authorization", "Bearer " + plugin.token)
                        .addHeader("Accept", "application/vnd.github+json")
                        .addHeader("X-GitHub-Api-Version", "2026-03-10")
                        .get()
                        .build()
                ).execute()
                val raw = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IllegalStateException("GitHub " + r.code)
                val login = JSONObject(raw).optString("login")
                "متصل بالحساب " + login
            }
            "dropbox" -> {
                val body = "{}".toRequestBody("application/json".toMediaType())
                val r = http.newCall(
                    Request.Builder()
                        .url("https://api.dropboxapi.com/2/users/get_current_account")
                        .addHeader("Authorization", "Bearer " + plugin.token)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()
                ).execute()
                val raw = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IllegalStateException("Dropbox " + r.code)
                val name = JSONObject(raw).optJSONObject("name")?.optString("display_name").orEmpty()
                "متصل" + if (name.isNotBlank()) " بالحساب " + name else ""
            }
            else -> {
                val r = http.newCall(
                    Request.Builder()
                        .url(plugin.url)
                        .addHeader("Authorization", "Bearer " + plugin.token)
                        .get()
                        .build()
                ).execute()
                if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
                "اتصال API ناجح"
            }
        }
    }
}

fun loadPlugins(ctx: Context): List<PluginEntry> {
    val raw = ctx.getSharedPreferences("plugins_v2", 0).getString("data", null) ?: return emptyList()
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            PluginEntry(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "مكون"),
                url = o.optString("url", ""),
                type = o.optString("type", "custom"),
                token = o.optString("token", ""),
                read = o.optBoolean("read", true),
                create = o.optBoolean("create", true),
                update = o.optBoolean("update", true),
                delete = o.optBoolean("delete", true)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun savePlugins(ctx: Context, plugins: List<PluginEntry>) {
    val a = JSONArray()
    plugins.forEach {
        a.put(
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("url", it.url)
                .put("type", it.type)
                .put("token", it.token)
                .put("read", it.read)
                .put("create", it.create)
                .put("update", it.update)
                .put("delete", it.delete)
        )
    }
    ctx.getSharedPreferences("plugins_v2", 0).edit().putString("data", a.toString()).apply()
}
