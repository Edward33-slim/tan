package com.mychatai

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
                save(plugins + it.copy(id = UUID.randomUUID().toString()))
                showCatalog = false
                message = "تمت إضافة " + it.name + ". أدخل رمز الوصول ثم اضغط اتصال."
            },
            onAddCustom = {
                showCatalog = false
                showAdd = true
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
            var token by remember(plugin.id) { mutableStateOf(plugin.token) }
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("رمز الوصول") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = { onPermission(plugin.copy(token = token.trim())) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ رمز الوصول") }
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

data class CatalogPlugin(
    val name: String,
    val description: String,
    val url: String,
    val type: String
)

private val pluginCatalog = listOf(
    CatalogPlugin("Gmail", "قراءة وإدارة البريد الإلكتروني", "https://gmail.googleapis.com", "custom"),
    CatalogPlugin("Google Drive", "Drive وDocs وSheets وSlides", "https://www.googleapis.com/drive/v3", "custom"),
    CatalogPlugin("GitHub", "المستودعات والملفات وIssues وPull Requests", "https://api.github.com", "github"),
    CatalogPlugin("Dropbox", "الملفات والمجلدات والتخزين السحابي", "https://api.dropboxapi.com", "dropbox"),
    CatalogPlugin("Supabase", "إدارة واستعلام قواعد البيانات", "https://api.supabase.com", "custom"),
    CatalogPlugin("Slack", "الرسائل والقنوات وبيانات مساحة العمل", "https://slack.com/api", "custom"),
    CatalogPlugin("Notion", "الصفحات وقواعد البيانات والمحتوى", "https://api.notion.com/v1", "custom"),
    CatalogPlugin("Trello", "اللوحات والقوائم والبطاقات", "https://api.trello.com/1", "custom"),
    CatalogPlugin("Jira", "المشاريع والمهام وIssues", "https://your-domain.atlassian.net/rest/api/3", "custom"),
    CatalogPlugin("Microsoft OneDrive", "الملفات والمجلدات عبر Microsoft Graph", "https://graph.microsoft.com/v1.0", "custom"),
    CatalogPlugin("Google Calendar", "الأحداث والتقويمات", "https://www.googleapis.com/calendar/v3", "custom"),
    CatalogPlugin("Linear", "Issues والمشاريع وسير العمل", "https://api.linear.app", "custom")
)

@Composable
private fun PluginCatalogDialog(
    existing: List<PluginEntry>,
    onDismiss: () -> Unit,
    onAdd: (PluginEntry) -> Unit,
    onAddCustom: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        pluginCatalog.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = Color(18, 18, 18)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "المكونات الإضافية",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    TextButton(onClick = onDismiss) { Text("إغلاق") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("بحث في المكونات الإضافية") },
                    leadingIcon = { Text("⌕", color = Color.LightGray) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            TextButton(onClick = { query = "" }) { Text("مسح") }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onAddCustom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("＋ إضافة أي مكون API")
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    if (query.isBlank()) "الأكثر استخداماً" else "نتائج البحث",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(6.dp))

                if (filtered.isEmpty()) {
                    Text(
                        "لا توجد نتيجة. استخدم «إضافة أي مكون API» لإضافة خدمة غير موجودة في القائمة.",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.name }) { item ->
                            val added = existing.any {
                                it.type == item.type && it.name.equals(item.name, ignoreCase = true)
                            }

                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(38, 38, 38)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                item.name.take(1),
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, color = Color.White)
                                        Text(
                                            item.description,
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Button(
                                        enabled = !added,
                                        onClick = {
                                            onAdd(
                                                PluginEntry(
                                                    name = item.name,
                                                    url = item.url,
                                                    type = item.type,
                                                    token = ""
                                                )
                                            )
                                        }
                                    ) {
                                        Text(if (added) "مضاف" else "إضافة")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "يمكنك إضافة أي خدمة أخرى إذا كانت توفر API رسميًا. بعد الإضافة اضبط رمز الوصول وأذونات القراءة/الإنشاء/التعديل/الحذف من شاشة المكون.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
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
