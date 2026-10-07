package com.mychatai

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
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
import java.io.File
import java.util.UUID
import android.util.Base64

private val Bg=Color(17,17,17)
private val Card=Color(30,30,30)
private val UserBubble=Color(55,55,55)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MyChatAiApp()}}
}

enum class Provider{CHATGPT,CLAUDE}
data class Attachment(val id:String,val name:String,val mime:String,val path:String,val size:Long)
data class ChatMessage(val id:String=UUID.randomUUID().toString(),val role:String,val text:String,val attachments:List<Attachment> = emptyList())
data class Chat(val id:String=UUID.randomUUID().toString(),var title:String,val messages:MutableList<ChatMessage> = mutableListOf())
@Composable
fun MyChatAiApp(){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var chats by remember{mutableStateOf(loadChats(ctx))}
    var activeId by remember{mutableStateOf(chats.firstOrNull()?.id ?: "")}
    var provider by remember{mutableStateOf(Provider.CHATGPT)}
    var input by remember{mutableStateOf(TextFieldValue())}
    var pending by remember{mutableStateOf<List<Attachment>>(emptyList())}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var showChats by remember{mutableStateOf(false)}
    var showSettings by remember{mutableStateOf(false)}
    var showAccount by remember{mutableStateOf(false)}
    var showPlugins by remember{mutableStateOf(false)}
    var renameTarget by remember{mutableStateOf<Chat?>(null)}

    if(chats.isEmpty()){
        val c=Chat(title="محادثة جديدة")
        chats=listOf(c);activeId=c.id;saveChats(ctx,chats)
    }
    val active=chats.firstOrNull{it.id==activeId} ?: chats.first()
    val scroll=rememberLazyListState()

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris:List<Uri>->
        if(uris.isNotEmpty())scope.launch{
            try{
                val added=uris.map{copyAttachment(ctx,it)}
                pending=pending+added
                status=""
            }catch(e:Exception){
                status="تعذر إرفاق الملفات: "+(e.message ?: "خطأ")
            }
        }
    }

    LaunchedEffect(active.messages.size){
        if(active.messages.isNotEmpty())scroll.animateScrollToItem(active.messages.size-1)
    }

    MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Card,primary=Color(160,160,160))){
        Column(Modifier.fillMaxSize().background(Bg)){
            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={showChats=true}){Text("☰",color=Color.White)}
                Text(active.title,color=Color.White,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                AssistChip(
                    onClick={provider=if(provider==Provider.CHATGPT)Provider.CLAUDE else Provider.CHATGPT},
                    label={Text(if(provider==Provider.CHATGPT)"GPT-6 Pro" else "Claude Opus 5.5")}
                )
                TextButton(onClick={showSettings=true}){Text("⚙",color=Color.White)}
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal=10.dp),
                state=scroll,verticalArrangement=Arrangement.spacedBy(8.dp)
            ){
                items(active.messages,key={it.id}){m->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement=if(m.role=="user")Arrangement.End else Arrangement.Start
                    ){
                        Surface(shape=RoundedCornerShape(18.dp),color=if(m.role=="user")UserBubble else Card){
                            Column(Modifier.padding(12.dp)){
                                m.attachments.forEach{a->Text("📎 "+a.name,color=Color.LightGray)}
                                if(m.text.isNotBlank())Text(m.text,color=Color.White)
                            }
                        }
                    }
                }
                if(busy)item{Text("جاري التفكير…",color=Color.Gray,modifier=Modifier.padding(12.dp))}
            }

            if(pending.isNotEmpty()){
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    pending.take(3).forEach{a->
                        AssistChip(
                            onClick={pending=pending.filterNot{it.id==a.id};File(a.path).delete()},
                            label={Text("📎 "+a.name.take(18))}
                        )
                    }
                    if(pending.size>3)Text("+${pending.size-3}",color=Color.Gray,modifier=Modifier.padding(8.dp))
                }
            }

            if(status.isNotBlank())Text(status,color=Color(240,140,140),modifier=Modifier.padding(horizontal=14.dp,vertical=4.dp))

            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.Bottom){
                IconButton(onClick={picker.launch(arrayOf("*/*"))}){Text("＋",color=Color.White,style=MaterialTheme.typography.headlineSmall)}
                OutlinedTextField(
                    value=input,onValueChange={input=it},modifier=Modifier.weight(1f),
                    placeholder={Text("اكتب رسالتك…")},maxLines=6
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    enabled=!busy&&(input.text.isNotBlank()||pending.isNotEmpty()),
                    onClick={
                        val user=ChatMessage(role="user",text=input.text.trim(),attachments=pending)
                        val index=chats.indexOfFirst{it.id==active.id}
                        if(index>=0){
                            val copy=chats.toMutableList()
                            val updated=Chat(active.id,active.title,active.messages.toMutableList())
                            updated.messages.add(user)
                            if(updated.title=="محادثة جديدة"&&user.text.isNotBlank())updated.title=user.text.take(36)
                            copy[index]=updated
                            chats=copy;saveChats(ctx,chats)
                            input=TextFieldValue();pending=emptyList();busy=true;status=""
                            scope.launch{
                                try{
                                    val answer=ApiClient.ask(ctx,provider,updated.messages,apiKey(ctx,provider))
                                    val out=chats.toMutableList()
                                    val current=out.indexOfFirst{it.id==activeId}
                                    if(current>=0){
                                        val c=out[current]
                                        val cc=Chat(c.id,c.title,c.messages.toMutableList())
                                        cc.messages.add(ChatMessage(role="assistant",text=answer))
                                        out[current]=cc;chats=out;saveChats(ctx,chats)
                                    }
                                }catch(e:Exception){status=e.message ?: "حدث خطأ"}finally{busy=false}
                            }
                        }
                    }
                ){Text("إرسال")}
            }
        }

        if(showChats){
            ChatListDialog(
                chats=chats,activeId=activeId,onDismiss={showChats=false},
                onNew={
                    val c=Chat(title="محادثة جديدة")
                    chats=listOf(c)+chats;activeId=c.id;saveChats(ctx,chats);showChats=false
                },
                onOpen={id->activeId=id;showChats=false},
                onRename={renameTarget=it},
                onDelete={chat->
                    deleteChatPermanently(chat)
                    chats=chats.filterNot{it.id==chat.id}
                    if(chats.isEmpty())chats=listOf(Chat(title="محادثة جديدة"))
                    activeId=chats.first().id;saveChats(ctx,chats)
                }
            )
        }

        renameTarget?.let{target->
            RenameDialog(target.title,onDismiss={renameTarget=null},onSave={name->
                chats=chats.map{if(it.id==target.id)Chat(it.id,name.trim().ifBlank{"محادثة جديدة"},it.messages.toMutableList())else it}
                saveChats(ctx,chats);renameTarget=null
            })
        }

        if(showSettings)SettingsDialog(ctx,{showSettings=false},{showSettings=false;showAccount=true},{showSettings=false;showPlugins=true})
        if(showAccount)AccountDialog(ctx){showAccount=false}
        if(showPlugins)PluginManagerDialog(ctx){showPlugins=false}
    }
}

@Composable
fun ChatListDialog(
    chats:List<Chat>,activeId:String,onDismiss:()->Unit,onNew:()->Unit,
    onOpen:(String)->Unit,onRename:(Chat)->Unit,onDelete:(Chat)->Unit
){
    AlertDialog(
        onDismissRequest=onDismiss,title={Text("المحادثات")},
        text={
            Column{
                Button(onClick=onNew,Modifier.fillMaxWidth()){Text("+ محادثة جديدة")}
                Spacer(Modifier.height(8.dp))
                chats.forEach{c->
                    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(c.title,color=if(c.id==activeId)Color.White else Color.LightGray,modifier=Modifier.weight(1f).clickable{onOpen(c.id)})
                        TextButton(onClick={onRename(c)}){Text("تعديل")}
                        TextButton(onClick={onDelete(c)}){Text("حذف")}
                    }
                }
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("إغلاق")}}
    )
}

@Composable
fun RenameDialog(old:String,onDismiss:()->Unit,onSave:(String)->Unit){
    var value by remember{mutableStateOf(old)}
    AlertDialog(
        onDismissRequest=onDismiss,title={Text("إعادة تسمية المحادثة")},
        text={OutlinedTextField(value,{value=it},singleLine=true,label={Text("الاسم")})},
        confirmButton={Button(onClick={onSave(value)}){Text("حفظ")}},
        dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}}
    )
}

@Composable
fun SettingsDialog(ctx:Context,onDismiss:()->Unit,onAccount:()->Unit,onPlugins:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,title={Text("الإعدادات")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onAccount,Modifier.fillMaxWidth()){Text("الحساب وتسجيل الدخول")}
                Button(onClick=onPlugins,Modifier.fillMaxWidth()){Text("المكونات الإضافية")}
                ApiKeysInline(ctx)
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("إغلاق")}}
    )
}

@Composable
fun ApiKeysInline(ctx:Context){
    var openai by remember{mutableStateOf(apiKey(ctx,Provider.CHATGPT))}
    var claude by remember{mutableStateOf(apiKey(ctx,Provider.CLAUDE))}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        OutlinedTextField(openai,{openai=it},label={Text("OpenAI API key")},singleLine=true,visualTransformation=PasswordVisualTransformation())
        OutlinedTextField(claude,{claude=it},label={Text("Anthropic API key")},singleLine=true,visualTransformation=PasswordVisualTransformation())
        Button(
            onClick={ctx.getSharedPreferences("keys",0).edit().putString("openai",openai.trim()).putString("anthropic",claude.trim()).apply()},
            Modifier.fillMaxWidth()
        ){Text("حفظ المفاتيح")}
    }
}

@Composable
fun AccountDialog(ctx:Context,onDismiss:()->Unit){
    val activity=ctx as? Activity
    var message by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var email by remember{mutableStateOf(GoogleAuth.email(ctx))}
    val googleSignedIn = email.isNotBlank()
    AlertDialog(
        onDismissRequest=onDismiss,title={Text("الحساب")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("تسجيل الدخول إلى MyChatAi",color=Color.LightGray)
                if(googleSignedIn)Text("Google: "+email,color=Color.White)
                Button(enabled=!busy&&activity!=null,onClick={
                    val a=activity ?: return@Button
                    busy=true;message=""
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main){
                        try{email=GoogleAuth.signIn(a);message="تم تسجيل الدخول بحساب Google بنجاح."}
                        catch(e:Exception){message=e.message ?: "فشل تسجيل الدخول إلى Google"}
                        finally{busy=false}
                    }
                },modifier=Modifier.fillMaxWidth()){Text(if(busy)"جارٍ تسجيل الدخول…" else "Google")}

                if(message.isNotBlank())Text(message,color=Color(240,200,120))
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("إغلاق")}}
    )
}
fun apiKey(ctx:Context,p:Provider):String=
    ctx.getSharedPreferences("keys",0).getString(if(p==Provider.CHATGPT)"openai" else "anthropic","") ?: ""

fun loadChats(ctx:Context):List<Chat>{
    val raw=ctx.getSharedPreferences("chats",0).getString("data",null)
    if(raw.isNullOrBlank()){
        val old=ctx.getSharedPreferences("chat",0).getString("messages","[]") ?: "[]"
        val a=JSONArray(old);val c=Chat(title="محادثة قديمة")
        for(i in 0 until a.length()){
            val o=a.getJSONObject(i)
            c.messages.add(ChatMessage(role=o.getString("role"),text=o.getString("text")))
        }
        return listOf(c)
    }
    val a=JSONArray(raw)
    return (0 until a.length()).map{ci->
        val o=a.getJSONObject(ci)
        val c=Chat(o.getString("id"),o.optString("title","محادثة جديدة"))
        val m=o.optJSONArray("messages") ?: JSONArray()
        for(i in 0 until m.length()){
            val x=m.getJSONObject(i);val at=mutableListOf<Attachment>();val aa=x.optJSONArray("attachments") ?: JSONArray()
            for(j in 0 until aa.length()){
                val z=aa.getJSONObject(j)
                at.add(Attachment(z.getString("id"),z.getString("name"),z.getString("mime"),z.getString("path"),z.getLong("size")))
            }
            c.messages.add(ChatMessage(x.getString("id"),x.getString("role"),x.optString("text",""),at))
        }
        c
    }
}

fun saveChats(ctx:Context,chats:List<Chat>){
    val a=JSONArray()
    chats.forEach{c->
        val o=JSONObject().put("id",c.id).put("title",c.title);val m=JSONArray()
        c.messages.forEach{x->
            val mo=JSONObject().put("id",x.id).put("role",x.role).put("text",x.text);val aa=JSONArray()
            x.attachments.forEach{f->aa.put(JSONObject().put("id",f.id).put("name",f.name).put("mime",f.mime).put("path",f.path).put("size",f.size))}
            mo.put("attachments",aa);m.put(mo)
        }
        o.put("messages",m);a.put(o)
    }
    ctx.getSharedPreferences("chats",0).edit().putString("data",a.toString()).apply()
}

fun deleteChatPermanently(chat:Chat){
    chat.messages.flatMap{it.attachments}.forEach{File(it.path).delete()}
}

suspend fun copyAttachment(ctx:Context,uri:Uri):Attachment=withContext(Dispatchers.IO){
    val name=(uri.lastPathSegment ?: "file").substringAfterLast('/').ifBlank{"file"}
    val mime=ctx.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.',""))
        ?: "application/octet-stream"
    val dir=File(ctx.filesDir,"attachments").apply{mkdirs()}
    val file=File(dir,UUID.randomUUID().toString()+"_"+name.replace("/","_"))
    var size=0L
    ctx.contentResolver.openInputStream(uri)?.use{input->
        file.outputStream().use{out->
            val buf=ByteArray(64*1024)
            while(true){
                val n=input.read(buf)
                if(n<=0)break
                size+=n
                if(size>50L*1024*1024)throw IllegalStateException("الملف أكبر من 50 MB")
                out.write(buf,0,n)
            }
        }
    } ?: throw IllegalStateException("تعذر فتح الملف")
    Attachment(UUID.randomUUID().toString(),name,mime,file.absolutePath,size)
}

object ApiClient{
    private val http=OkHttpClient()

    suspend fun ask(ctx:Context,provider:Provider,messages:List<ChatMessage>,key:String):String=withContext(Dispatchers.IO){
        if(provider==Provider.CHATGPT){
            if(key.isBlank())throw IllegalStateException("ضع مفتاح OpenAI API في الإعدادات أولاً")
            openai(messages,key)
        }else{
            if(key.isBlank())throw IllegalStateException("ضع مفتاح Anthropic API في الإعدادات أولاً")
            claude(messages,key)
        }
    }

    private fun openai(messages:List<ChatMessage>,key:String):String{
        val input=JSONArray()
        messages.takeLast(80).forEach{m->
            if(m.attachments.isEmpty()){
                input.put(JSONObject().put("role",if(m.role=="user")"user" else "assistant").put("content",m.text))
            }else{
                val parts=JSONArray()
                if(m.text.isNotBlank())parts.put(JSONObject().put("type","input_text").put("text",m.text))
                m.attachments.forEach{a->
                    if(a.mime.startsWith("image/")){
                        val b=Base64.encodeToString(File(a.path).readBytes(),Base64.NO_WRAP)
                        parts.put(JSONObject().put("type","input_image").put("image_url","data:"+a.mime+";base64,"+b))
                    }else if(a.name.matches(Regex(".*\\.(txt|md|json|xml|csv|kt|java|py|js|ts|html|css|log|gradle|yml|yaml|properties)$",RegexOption.IGNORE_CASE))){
                        val t=File(a.path).readBytes().toString(Charsets.UTF_8).take(120000)
                        parts.put(JSONObject().put("type","input_text").put("text","[ملف "+a.name+"]\n"+t))
                    }else{
                        parts.put(JSONObject().put("type","input_text").put("text","[مرفق "+a.name+"، الحجم "+a.size+" bytes]"))
                    }
                }
                input.put(JSONObject().put("role",if(m.role=="user")"user" else "assistant").put("content",parts))
            }
        }
        val body=JSONObject().put("model","gpt-6-astra").put("input",input).toString().toRequestBody("application/json".toMediaType())
        val r=http.newCall(Request.Builder().url("https://api.openai.com/v1/responses").addHeader("Authorization","Bearer "+key).post(body).build()).execute()
        val raw=r.body?.string() ?: ""
        if(!r.isSuccessful)throw IllegalStateException("OpenAI "+r.code+": "+friendlyError(raw))
        val j=JSONObject(raw)
        val direct=j.optString("output_text")
        if(direct.isNotBlank())return direct
        val out=j.optJSONArray("output") ?: throw IllegalStateException("OpenAI: لم يتم إرجاع نص")
        val sb=StringBuilder()
        for(i in 0 until out.length()){
            val x=out.optJSONObject(i) ?: continue
            val c=x.optJSONArray("content") ?: continue
            for(k in 0 until c.length()){
                val p=c.optJSONObject(k) ?: continue
                if(p.optString("type")=="output_text")sb.append(p.optString("text"))
            }
        }
        return sb.toString().ifBlank{"OpenAI: استجابة فارغة"}
    }

    private fun claude(messages:List<ChatMessage>,key:String):String{
        val arr=JSONArray()
        messages.takeLast(120).forEach{m->
            val c=JSONArray()
            if(m.text.isNotBlank())c.put(JSONObject().put("type","text").put("text",m.text))
            m.attachments.forEach{a->
                if(a.mime.startsWith("image/")){
                    val b=Base64.encodeToString(File(a.path).readBytes(),Base64.NO_WRAP)
                    c.put(JSONObject().put("type","image").put("source",JSONObject().put("type","base64").put("media_type",a.mime).put("data",b)))
                }else{
                    c.put(JSONObject().put("type","text").put("text","[مرفق "+a.name+"، الحجم "+a.size+" bytes]"))
                }
            }
            arr.put(JSONObject().put("role",if(m.role=="user")"user" else "assistant").put("content",c))
        }
        val body=JSONObject().put("model","claude-opus-5-5").put("max_tokens",4096).put("messages",arr).toString().toRequestBody("application/json".toMediaType())
        val r=http.newCall(Request.Builder().url("https://api.anthropic.com/v1/messages").addHeader("x-api-key",key).addHeader("anthropic-version","2023-06-01").post(body).build()).execute()
        val raw=r.body?.string() ?: ""
        if(!r.isSuccessful)throw IllegalStateException("Claude "+r.code+": "+friendlyError(raw))
        val c=JSONObject(raw).optJSONArray("content") ?: throw IllegalStateException("Claude: لم يتم إرجاع محتوى")
        val sb=StringBuilder()
        for(i in 0 until c.length()){
            val x=c.optJSONObject(i) ?: continue
            if(x.optString("type")=="text")sb.append(x.optString("text"))
        }
        return sb.toString().ifBlank{"Claude: استجابة فارغة"}
    }

    private fun friendlyError(raw:String):String{
        return try{
            JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf{it.isNotBlank()} ?: raw.take(700)
        }catch(_:Exception){raw.take(700)}
    }
}
