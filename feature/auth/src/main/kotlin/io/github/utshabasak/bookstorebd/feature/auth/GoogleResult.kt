package io.github.utshabasak.bookstorebd.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** What asking Google for an ID token came to. */
internal sealed interface GoogleResult {
    data class Token(
        val idToken: String,
    ) : GoogleResult

    /** The person closed the sheet, or the phone has no Google account to offer. */
    data object Cancelled : GoogleResult

    data class Failed(
        val cause: Exception,
    ) : GoogleResult
}

/**
 * Asks Google, through Android's Credential Manager, for an ID token
 * addressed to the site's web client and carrying the server's nonce. The
 * server checks the token itself (server/controllers/appGoogle.controller.ts).
 */
internal suspend fun requestGoogleIdToken(
    activityContext: Context,
    clientId: String,
    nonce: String,
): GoogleResult =
    try {
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
        val response = CredentialManager.create(activityContext).getCredential(activityContext, GetCredentialRequest(listOf(option)))
        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleResult.Failed(IllegalStateException("Unexpected credential type ${credential.type}"))
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleResult.Cancelled
    } catch (_: NoCredentialException) {
        GoogleResult.Cancelled
    } catch (failure: GetCredentialException) {
        GoogleResult.Failed(failure)
    } catch (failure: GoogleIdTokenParsingException) {
        GoogleResult.Failed(failure)
    }
