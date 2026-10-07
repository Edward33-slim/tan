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
    /*
     * Replace this with the Web application OAuth client ID created in
     * Google Cloud Console for MyChatAi.
     */
    private const val SERVER_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"
    private const val PREF = "google_auth"

    fun email(ctx: Context): String =
        ctx.getSharedPreferences(PREF, 0).getString("email", "") ?: ""

    suspend fun signIn(activity: Activity): String {
        if (SERVER_CLIENT_ID.startsWith("YOUR_")) {
            throw IllegalStateException("ضع Google Web Client ID في GoogleAuth.kt أولاً")
        }

        val nonce = UUID.randomUUID().toString()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(SERVER_CLIENT_ID)
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
        if (email.isBlank()) throw IllegalStateException("Google لم يُرجع البريد الإلكتروني")

        activity.getSharedPreferences(PREF, 0).edit()
            .putString("email", email)
            .putString("id_token", google.idToken)
            .apply()

        return email
    }
}
