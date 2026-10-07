package com.mychatai

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.UUID

object GoogleAuth {
    private const val PREF = "google_auth"
    private const val KEY_SERVER_CLIENT_ID = "server_client_id"
    const val DEFAULT_SERVER_CLIENT_ID = "377365141235-voaafi94dtvlimo5jve57fbk36mld1jp.apps.googleusercontent.com"

    fun email(ctx: Context): String =
        ctx.getSharedPreferences(PREF, 0).getString("email", "") ?: ""

    fun serverClientId(ctx: Context): String =
        ctx.getSharedPreferences(PREF, 0).getString(KEY_SERVER_CLIENT_ID, "") ?: ""

    fun setServerClientId(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREF, 0).edit()
            .putString(KEY_SERVER_CLIENT_ID, value.trim())
            .apply()
    }

    suspend fun signIn(activity: Activity): String {
        val serverClientId = serverClientId(activity).ifBlank { DEFAULT_SERVER_CLIENT_ID }
        if (serverClientId.isBlank() ||
            !serverClientId.endsWith(".apps.googleusercontent.com")
        ) {
            throw IllegalStateException(
                "أدخل Google Web Client ID الصحيح أولاً"
            )
        }

        val nonce = UUID.randomUUID().toString()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request
        )

        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("لم يتم استلام بيانات اعتماد Google صالحة")
        }

        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val email = google.id
        if (email.isBlank()) {
            throw IllegalStateException("Google لم يُرجع البريد الإلكتروني")
        }

        activity.getSharedPreferences(PREF, 0).edit()
            .putString("email", email)
            .putString("id_token", google.idToken)
            .apply()

        return email
    }
}
