package io.github.utshabasak.bookstorebd.feature.auth

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONException
import org.json.JSONObject

/**
 * The bot check: Cloudflare Turnstile on the site's own page, in a web view
 * the app controls. It usually passes without the person doing anything; now
 * and then it asks them to tick a box. The token comes back through a message
 * channel open to the site's origin only, and goes with the request, where the
 * server checks it with Cloudflare exactly as it does for the website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChallengeSheet(
    url: String,
    onToken: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val latestToken by rememberUpdatedState(onToken)
    var failed by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.feature_auth_challenge_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (failed) R.string.feature_auth_challenge_failed else R.string.feature_auth_challenge_body),
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            AndroidView(
                factory = { context -> challengeView(context, url, dark, onToken = { latestToken(it) }, onFailed = { failed = true }) },
                onRelease = { it.destroy() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun challengeView(
    context: android.content.Context,
    url: String,
    dark: Boolean,
    onToken: (String) -> Unit,
    onFailed: () -> Unit,
): WebView {
    val page = url.toUri()
    val origin = "${page.scheme}://${page.authority}"
    return WebView(context).apply {
        // Turnstile is a script, so it needs JavaScript; nothing else is enabled.
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = ChallengeClient(allowedHosts = setOf(page.host, CLOUDFLARE_HOST), onFailed = onFailed)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, CHANNEL, setOf(origin)) { _, message, _, isMainFrame, _ ->
                if (!isMainFrame) return@addWebMessageListener
                val data =
                    try {
                        JSONObject(message.data.orEmpty())
                    } catch (_: JSONException) {
                        return@addWebMessageListener
                    }
                when (data.optString("type")) {
                    "token" -> data.optString("token").takeIf { it.isNotBlank() }?.let(onToken)
                    "error", "expired" -> onFailed()
                }
            }
        } else {
            onFailed()
        }
        loadUrl(page.buildUpon().appendQueryParameter("theme", if (dark) "dark" else "light").build().toString())
    }
}

/**
 * Keeps the web view on the challenge, and survives its renderer being stopped.
 *
 * onRenderProcessGone is implemented below; androidx.webkit's lint check does
 * not recognise the Kotlin override, hence the suppression.
 */
@SuppressLint("MissingOnRenderProcessGone")
private class ChallengeClient(
    private val allowedHosts: Set<String?>,
    private val onFailed: () -> Unit,
) : WebViewClient() {
    // Only the site and Cloudflare's challenge pages load here; nothing navigates away.
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = request.url.host !in allowedHosts

    // The renderer was stopped (low memory, or a crash): the app carries on, and the person can try again.
    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        onFailed()
        return true
    }
}

/** The name the page's script posts to (server/controllers/appChallenge.controller.ts). */
private const val CHANNEL = "BookStoreBDChallenge"
private const val CLOUDFLARE_HOST = "challenges.cloudflare.com"
