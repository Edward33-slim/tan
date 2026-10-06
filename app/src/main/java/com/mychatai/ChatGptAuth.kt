package com.mychatai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import android.util.Base64

object ApiHttp {
    val http = OkHttpClient()
}

object ChatGptAuth {
    private const val PREF = "chatgpt_auth"
    private const val AUTH = "https://auth.openai.com/api/accounts/authorize"
    private const val TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
    private const val RESOURCE = "https://api.openai.com/v1"

    fun email(ctx: Context): String =
        ctx.getSharedPreferences(PREF, 0).getString("email", "") ?: ""

    suspend fun signIn(activity: Activity): String = withContext(Dispatchers.IO) {
        val p = activity.getSharedPreferences(PREF, 0)
        val savedClient = p.getString("client_id", null)
        val client = savedClient ?: "dynamic_agent_client"
        val host = p.getString("host_id", null)
            ?: ("urn:uuid:" + UUID.randomUUID().toString()).also {
                p.edit().putString("host_id", it).apply()
            }

        val verifier = random(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val state = random(32)
        val nonce = random(32)
        val server = ServerSocket(1455, 1, InetAddress.getByName("127.0.0.1"))
        val redirect = "http://127.0.0.1:1455/auth/callback"

        val q = StringBuilder()
        q.append("client_id=").append(enc(client))
        if (savedClient == null) q.append("&agent_name_hint=").append(enc("MyChatAi"))
        q.append("&ext_agent_host_id=").append(enc(host))
        q.append("&response_type=code&redirect_uri=").append(enc(redirect))
        q.append("&scope=").append(enc("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"))
        q.append("&resource=").append(enc(RESOURCE))
        q.append("&state=").append(enc(state))
        q.append("&nonce=").append(enc(nonce))
        q.append("&code_challenge_method=S256&code_challenge=").append(enc(challenge))

        withContext(Dispatchers.Main) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AUTH + "?" + q))
        }

        val socket = server.accept()
        val line = BufferedReader(InputStreamReader(socket.getInputStream())).readLine() ?: ""
        val target = line.substringAfter("GET ").substringBefore(" HTTP/")
        val params = parse(target.substringAfter('?', ""))

        PrintWriter(socket.getOutputStream()).use {
            it.print("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n<html><body>MyChatAi: يمكنك العودة إلى التطبيق.</body></html>")
            it.flush()
        }
        socket.close()
        server.close()

        if (params["state"] != state) throw IllegalStateException("فشل التحقق من OAuth")
        params["error"]?.let { throw IllegalStateException("تسجيل الدخول مرفوض: " + it) }

        val code = params["code"] ?: throw IllegalStateException("لم يصل رمز تسجيل الدخول")
        val returnedClient = params["client_id"] ?: client
        if (savedClient != null && returnedClient != savedClient) {
            throw IllegalStateException("OAuth client ID غير مطابق")
        }

        val token = exchange(code, returnedClient, verifier, redirect)
        validate(token.optString("id_token"), returnedClient, nonce)

        val id = payload(token.getString("id_token"))
        val mail = id.optString("email", "")
        p.edit()
            .putString("client_id", returnedClient)
            .putString("access", token.getString("access_token"))
            .putString("refresh", token.getString("refresh_token"))
            .putString("id", token.getString("id_token"))
            .putString("email", mail)
            .putLong("exp", System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000L)
            .apply()

        mail.ifBlank { "تم تسجيل الدخول" }
    }

    suspend fun validAccessToken(ctx: Context): String = withContext(Dispatchers.IO) {
        val p = ctx.getSharedPreferences(PREF, 0)
        val access = p.getString("access", null)
            ?: throw IllegalStateException("سجّل الدخول إلى ChatGPT أولاً")
        if (System.currentTimeMillis() < p.getLong("exp", 0L) - 300000L) {
            return@withContext access
        }
        refresh(ctx)
    }

    suspend fun forceRefresh(ctx: Context): String = withContext(Dispatchers.IO) {
        refresh(ctx)
    }

    private fun refresh(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREF, 0)
        val client = p.getString("client_id", null)
            ?: throw IllegalStateException("جلسة ChatGPT غير مكتملة")
        val oldRefresh = p.getString("refresh", null)
            ?: throw IllegalStateException("سجّل الدخول مجدداً")

        val form = "grant_type=refresh_token&client_id=" + enc(client) +
            "&refresh_token=" + enc(oldRefresh) + "&resource=" + enc(RESOURCE)

        val request = Request.Builder()
            .url(TOKEN)
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = ApiHttp.http.newCall(request).execute()
        val raw = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IllegalStateException("تجديد ChatGPT فشل: " + response.code)
        }

        val json = JSONObject(raw)
        val access = json.getString("access_token")
        val newRefresh = json.optString("refresh_token", oldRefresh)

        p.edit()
            .putString("access", access)
            .putString("refresh", newRefresh)
            .putLong("exp", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000L)
            .apply()

        return access
    }

    private fun exchange(code: String, client: String, verifier: String, redirect: String): JSONObject {
        val form = "grant_type=authorization_code&client_id=" + enc(client) +
            "&code=" + enc(code) + "&code_verifier=" + enc(verifier) +
            "&redirect_uri=" + enc(redirect) + "&resource=" + enc(RESOURCE)

        val request = Request.Builder()
            .url(TOKEN)
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = ApiHttp.http.newCall(request).execute()
        val raw = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IllegalStateException("تبادل OAuth فشل: " + response.code)
        }
        return JSONObject(raw)
    }

    private fun validate(token: String, client: String, nonce: String) {
        if (token.isBlank()) throw IllegalStateException("لم يصل ID token")
        val parts = token.split('.')
        if (parts.size != 3) throw IllegalStateException("ID token غير صالح")

        val header = JSONObject(String(
            Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8
        ))
        val payload = payload(token)

        if (payload.optString("iss") != "https://auth.openai.com" ||
            payload.optString("aud") != client ||
            payload.optString("nonce") != nonce) {
            throw IllegalStateException("بيانات ID token غير صالحة")
        }
        if (payload.optLong("exp", 0L) * 1000L < System.currentTimeMillis()) {
            throw IllegalStateException("ID token منتهي")
        }

        val rawJwks = ApiHttp.http.newCall(
            Request.Builder().url("https://auth.openai.com/.well-known/jwks.json").build()
        ).execute().body?.string() ?: ""

        val jwks = JSONObject(rawJwks)
        val keys = jwks.optJSONArray("keys") ?: JSONArray()
        var x5c: String? = null

        for (i in 0 until keys.length()) {
            val key = keys.getJSONObject(i)
            if (key.optString("kid") == header.optString("kid")) {
                x5c = key.optJSONArray("x5c")?.optString(0)
                break
            }
        }

        if (x5c.isNullOrBlank()) throw IllegalStateException("مفتاح OpenAI غير موجود")

        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(Base64.decode(x5c, Base64.DEFAULT).inputStream()) as X509Certificate

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(cert.publicKey)
        signature.update((parts[0] + "." + parts[1]).toByteArray())

        if (!signature.verify(
                Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            )
        ) {
            throw IllegalStateException("توقيع ID token غير صالح")
        }
    }

    private fun payload(token: String): JSONObject {
        val encoded = token.split('.')[1]
        return JSONObject(String(
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8
        ))
    }

    private fun parse(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i < 0) null else
                URLDecoder.decode(it.substring(0, i), "UTF-8") to
                URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun random(size: Int): String =
        ByteArray(size).also { SecureRandom().nextBytes(it) }.let {
            Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
}
