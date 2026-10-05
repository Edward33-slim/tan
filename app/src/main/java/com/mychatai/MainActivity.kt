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
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MyChatAiApp() } }
}

enum class Provider { CHATGPT, CLAUDE }
data class ChatMessage(val role:String, val text:String)

@Composable fun MyChatAiApp() {
 val ctx=LocalContext.current
 var provider by remember { mutableStateOf(Provider.CHATGPT) }
 var input by remember { mutableStateOf(TextFieldValue()) }
 var messages by remember { mutableStateOf(loadMessages(ctx)) }
 var busy by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("") }\n var showSettings by remember { mutableStateOf(false) }
 val scope=rememberCoroutineScope(); val list=rememberLazyListState()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
  if(uri!=null) scope.launch { val info=readAttachment(ctx,uri); input=input.copy(text=input.text + "\n\n[ملف مرفوع: ${info.first}]\n${info.second}\n") }
 }
 LaunchedEffect(messages.size){ if(messages.isNotEmpty()) list.animateScrollToItem(messages.size-1) }
 MaterialTheme(colorScheme=darkColorScheme(background=Color(17,17,17),surface=Color(28,28,28),primary=Color(160,160,160))) {
  Column(Modifier.fillMaxSize().background(Color(17,17,17))) {
   Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
    Text("MyChatAi", style=MaterialTheme.typography.titleLarge, color=Color.White, modifier=Modifier.weight(1f))\n    TextButton(onClick={showSettings=true}) { Text("⚙", color=Color.White) }
    AssistChip(onClick={ provider=if(provider==Provider.CHATGPT) Provider.CLAUDE else Provider.CHATGPT }, label={Text(if(provider==Provider.CHATGPT) "ChatGPT" else "Claude")})
   }
   LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=10.dp), state=list, verticalArrangement=Arrangement.spacedBy(8.dp)) {
    items(messages){ m ->
     Row(Modifier.fillMaxWidth(), horizontalArrangement=if(m.role=="user") Arrangement.End else Arrangement.Start){
      Surface(shape=RoundedCornerShape(18.dp), color=if(m.role=="user") Color(55,55,55) else Color(30,30,30)) { Text(m.text, Modifier.padding(12.dp), color=Color.White) }
     }
    }
    if(busy) item { Text("جاري التفكير…", color=Color.Gray, modifier=Modifier.padding(12.dp)) }
   }
   if(status.isNotEmpty()) Text(status, color=Color(220,120,120), modifier=Modifier.padding(horizontal=14.dp,vertical=4.dp))
   Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment=Alignment.Bottom){
    IconButton(onClick={picker.launch(arrayOf("*/*"))}) { Text("＋", color=Color.White, style=MaterialTheme.typography.headlineSmall) }
    OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.weight(1f),placeholder={Text("اكتب رسالتك…")},maxLines=6)
    Spacer(Modifier.width(6.dp)); Button(enabled=!busy && input.text.isNotBlank(),onClick={
      val text=input.text.trim(); input=TextFieldValue(); messages=messages+ChatMessage("user",text); saveMessages(ctx,messages); busy=true; status=""
      scope.launch { try { val answer=ApiClient.ask(provider,messages,apiKey(ctx,provider)); messages=messages+ChatMessage("assistant",answer); saveMessages(ctx,messages) } catch(e:Exception){status=e.message ?: "حدث خطأ"} finally{busy=false} }
    }) { Text("إرسال") }
   }
  }
 }
}

fun apiKey(ctx:Context,p:Provider)=ctx.getSharedPreferences("keys",Context.MODE_PRIVATE).getString(if(p==Provider.CHATGPT)"openai" else "anthropic","") ?: ""
fun loadMessages(ctx:Context):List<ChatMessage>{ val a=JSONArray(ctx.getSharedPreferences("chat",0).getString("messages","[]")); return (0 until a.length()).map{val o=a.getJSONObject(it);ChatMessage(o.getString("role"),o.getString("text"))} }
fun saveMessages(ctx:Context,m:List<ChatMessage>){val a=JSONArray();m.forEach{a.put(JSONObject().put("role",it.role).put("text",it.text))};ctx.getSharedPreferences("chat",0).edit().putString("messages",a.toString()).apply()}

suspend fun readAttachment(ctx:Context,uri:Uri):Pair<String,String>{
 val name=uri.lastPathSegment?.substringAfterLast('/') ?: "file"; val bytes=ctx.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:ByteArray(0)
 val ext=name.substringAfterLast('.',"").lowercase(); val text=when{
  ext in setOf("txt","md","json","xml","csv","kt","java","py","js","ts","html","css","log","properties","gradle","yml","yaml") -> bytes.toString(Charsets.UTF_8).take(120000)
  ext in setOf("zip","apk","xapk","apkm","apks") -> { val tmp=File(ctx.cacheDir,name);tmp.writeBytes(bytes); val sb=StringBuilder("Archive entries:\n"); ZipFile(tmp).use{z->val en=z.entries();var n=0;while(en.hasMoreElements()&&n<500){sb.append(en.nextElement().name).append('\n');n++}};sb.toString() }
  else -> "نوع الملف محفوظ كمرفق. الاسم=$name، الحجم=${bytes.size} bytes"
 }; return name to text
}

object ApiClient{
 private val http=OkHttpClient()
 suspend fun ask(p:Provider,m:List<ChatMessage>,key:String):String{
  if(key.isBlank()) throw IllegalStateException("ضع مفتاح API في إعدادات التطبيق أولاً")
  return if(p==Provider.CHATGPT) openai(m,key) else claude(m,key)
 }
 private fun openai(m:List<ChatMessage>,key:String):String{
  val input=JSONArray();m.takeLast(80).forEach{input.put(JSONObject().put("role",if(it.role=="user")"user" else "assistant").put("content",it.text))}
  val body=JSONObject().put("model","gpt-6-luna").put("input",input).toString().toRequestBody("application/json".toMediaType())
  val r=http.newCall(Request.Builder().url("https://api.openai.com/v1/responses").addHeader("Authorization","Bearer $key").addHeader("Content-Type","application/json").post(body).build()).execute();val s=r.body?.string()?:(throw Exception("Empty response"));if(!r.isSuccessful)throw Exception("OpenAI: ${r.code} $s");val o=JSONObject(s);return o.optString("output_text",s)
 }
 private fun claude(m:List<ChatMessage>,key:String):String{
  val a=JSONArray();m.takeLast(80).forEach{a.put(JSONObject().put("role",if(it.role=="user")"user" else "assistant").put("content",it.text))}
  val body=JSONObject().put("model","claude-sonnet-4-5").put("max_tokens",4096).put("messages",a).toString().toRequestBody("application/json".toMediaType())
  val r=http.newCall(Request.Builder().url("https://api.anthropic.com/v1/messages").addHeader("x-api-key",key).addHeader("anthropic-version","2023-06-01").addHeader("Content-Type","application/json").post(body).build()).execute();val s=r.body?.string()?:(throw Exception("Empty response"));if(!r.isSuccessful)throw Exception("Claude: ${r.code} $s");val c=JSONObject(s).optJSONArray("content")?:throw Exception("Bad Claude response");return c.optJSONObject(0)?.optString("text")?:s
 }
}
