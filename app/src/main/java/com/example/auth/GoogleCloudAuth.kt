package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.CloudSyncPrefsStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val AUTH_TAG = "Auth"

fun ensureFirebaseApp(context: Context): FirebaseApp? {
    val appContext = context.applicationContext ?: context
    val existingApps = runCatching { FirebaseApp.getApps(appContext) }.getOrNull().orEmpty()
    if (existingApps.isNotEmpty()) {
        return runCatching { FirebaseApp.getInstance() }.getOrNull() ?: existingApps.firstOrNull()
    }
    val initializedFromResources = runCatching { FirebaseApp.initializeApp(appContext) }
        .onFailure { Log.w(AUTH_TAG, "FirebaseApp.initializeApp failed: ${it.message}", it) }
        .getOrNull()
    if (initializedFromResources != null) {
        return initializedFromResources
    }
    val options = runCatching { FirebaseOptions.fromResource(appContext) }.getOrNull()
        ?: buildFirebaseOptionsFromStrings(appContext)
        ?: return null
    return runCatching { FirebaseApp.initializeApp(appContext, options) }
        .onFailure { Log.e(AUTH_TAG, "FirebaseApp.initializeApp(options) failed: ${it.message}", it) }
        .getOrNull()
}

private fun resolveStringResource(context: Context, name: String): String? {
    val resId = context.resources.getIdentifier(name, "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun buildFirebaseOptionsFromStrings(context: Context): FirebaseOptions? {
    val appId = resolveStringResource(context, "google_app_id").orEmpty()
    val apiKey = resolveStringResource(context, "google_api_key").orEmpty()
    if (appId.isEmpty() || apiKey.isEmpty()) return null
    val builder = FirebaseOptions.Builder()
        .setApplicationId(appId)
        .setApiKey(apiKey)
    resolveStringResource(context, "project_id")?.let { builder.setProjectId(it) }
    resolveStringResource(context, "gcm_defaultSenderId")?.let { builder.setGcmSenderId(it) }
    resolveStringResource(context, "google_storage_bucket")?.let { builder.setStorageBucket(it) }
    return builder.build()
}

fun safeFirebaseAuth(context: Context? = null): FirebaseAuth? {
    val app = context?.let { ensureFirebaseApp(it) }
    return runCatching {
        if (app != null) FirebaseAuth.getInstance(app) else Firebase.auth
    }.onFailure { err ->
        Log.e(AUTH_TAG, "FirebaseAuth unavailable: ${err.message}", err)
    }.getOrNull()
}

private fun resolveFirebaseAuthWithError(context: Context): Pair<FirebaseAuth?, String?> {
    return try {
        val app = ensureFirebaseApp(context)
        val auth = if (app != null) FirebaseAuth.getInstance(app) else Firebase.auth
        auth to null
    } catch (t: Throwable) {
        Log.e(AUTH_TAG, "Failed to obtain FirebaseAuth: ${t.message}", t)
        val hasAppId = resolveStringResource(context, "google_app_id") != null
        val msg = if (!hasAppId) {
            "Google Sign-In is not configured in this APK build (missing google-services config)."
        } else {
            val detail = t.localizedMessage?.trim().orEmpty()
            if (detail.isNotEmpty()) {
                "Cloud authentication could not start ($detail)."
            } else {
                "Cloud authentication could not initialize on this device."
            }
        }
        null to msg
    }
}

private fun resolveDefaultWebClientId(context: Context): String? {
    return resolveStringResource(context, "default_web_client_id")
}

private fun buildLegacyGoogleSignInOptions(clientId: String): GoogleSignInOptions {
    return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(clientId)
        .requestEmail()
        .build()
}

tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

suspend fun <T> Task<T>.awaitGmsTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            val ex = task.exception ?: IllegalStateException("Task failed without exception")
            cont.resumeWithException(ex)
        }
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    runCatching { addAuthStateListener(listener) }
    trySend(runCatching { currentUser }.getOrNull())
    awaitClose { runCatching { removeAuthStateListener(listener) } }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val auth = safeFirebaseAuth(context)
    if (auth == null) {
        onUnauthenticated()
        return
    }
    if (runCatching { auth.currentUser }.getOrNull() != null) {
        CloudSyncPrefsStore(context).setExplicitGoogleSignOut(false)
        onAuthSuccess()
        return
    }
    if (CloudSyncPrefsStore(context).isExplicitGoogleSignOut()) {
        onUnauthenticated()
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId.isNullOrBlank()) {
        onUnauthenticated()
        return
    }
    val activityOrContext = context.findActivityOrNull() ?: context
    val activeCredentialManager = credentialManager
        ?: runCatching { CredentialManager.create(activityOrContext) }.getOrNull()
    if (activeCredentialManager == null) {
        onUnauthenticated()
        return
    }

    scope.launch {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val cred = activeCredentialManager.getCredential(activityOrContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).awaitGmsTask()
                CloudSyncPrefsStore(context).setExplicitGoogleSignOut(false)
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.d(AUTH_TAG, "Silent auto-sign-in skipped or unavailable: ${t.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    onLaunchLegacyFallback: ((Intent) -> Unit)? = null
) {
    val (auth, firebaseInitError) = resolveFirebaseAuthWithError(context)
    if (auth == null) {
        onAuthError(firebaseInitError ?: "Cloud authentication is not ready on this device.")
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId.isNullOrBlank()) {
        onAuthError("Google Sign-In configuration is missing: default_web_client_id not found.")
        return
    }

    scope.launch {
        val activityOrContext = context.findActivityOrNull() ?: context
        val activeCredentialManager = credentialManager
            ?: runCatching { CredentialManager.create(activityOrContext) }
                .onFailure { Log.w(AUTH_TAG, "CredentialManager.create failed: ${it.message}", it) }
                .getOrNull()

        var lastCredentialError: Throwable? = null

        if (activeCredentialManager != null) {
            try {
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val cred = activeCredentialManager.getCredential(activityOrContext, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).awaitGmsTask()
                    CloudSyncPrefsStore(context).setExplicitGoogleSignOut(false)
                    onAuthSuccess()
                    return@launch
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
                return@launch
            } catch (t: Throwable) {
                lastCredentialError = t
                Log.w(AUTH_TAG, "GetSignInWithGoogleOption unavailable, trying GetGoogleIdOption: ${t.message}", t)
            }

            try {
                val allAccountsOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(allAccountsOption)
                    .build()
                val cred = activeCredentialManager.getCredential(activityOrContext, fallbackRequest).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).awaitGmsTask()
                    CloudSyncPrefsStore(context).setExplicitGoogleSignOut(false)
                    onAuthSuccess()
                    return@launch
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
                return@launch
            } catch (t: Throwable) {
                lastCredentialError = t
                Log.w(AUTH_TAG, "GetGoogleIdOption failed: ${t.message}", t)
            }
        }

        val playServicesStatus = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activityOrContext)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)

        if (onLaunchLegacyFallback != null && playServicesStatus == ConnectionResult.SUCCESS) {
            try {
                val gso = buildLegacyGoogleSignInOptions(clientId)
                val googleSignInClient = GoogleSignIn.getClient(activityOrContext, gso)
                runCatching { googleSignInClient.signOut().awaitGmsTask() }
                onLaunchLegacyFallback(googleSignInClient.signInIntent)
                return@launch
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(AUTH_TAG, "Legacy GoogleSignIn launcher preparation failed: ${t.message}", t)
                lastCredentialError = t
            }
        }

        if (playServicesStatus != ConnectionResult.SUCCESS) {
            val statusStr = GoogleApiAvailability.getInstance().getErrorString(playServicesStatus)
            onAuthError("Google Play Services is required for Google Sign-In ($statusStr). Please update or enable Google Play Services.")
            return@launch
        }

        when (lastCredentialError) {
            is NoCredentialException -> {
                onAuthError("No Gmail account was found on this device. Add an account in Settings > Accounts and try again.")
            }
            null -> {
                onAuthError("Google Sign-In could not open on this device. Please ensure Google Play Services is enabled and try again.")
            }
            else -> {
                onAuthError(lastCredentialError.localizedMessage ?: "Google Sign-In failed. Please try again.")
            }
        }
    }
}

fun handleLegacyGoogleSignInResult(
    context: Context,
    data: Intent?,
    resultCode: Int,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {}
) {
    if (resultCode == Activity.RESULT_CANCELED && data == null) {
        onAuthCancelled()
        return
    }
    val (auth, firebaseInitError) = resolveFirebaseAuthWithError(context)
    if (auth == null) {
        onAuthError(firebaseInitError ?: "Cloud authentication is not ready on this device.")
        return
    }

    scope.launch {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).awaitGmsTask()
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                onAuthError("Google Sign-In did not return a valid ID token. Please try again.")
                return@launch
            }
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitGmsTask()
            CloudSyncPrefsStore(context).setExplicitGoogleSignOut(false)
            onAuthSuccess()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: ApiException) {
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED,
                CommonStatusCodes.CANCELED -> {
                    Log.w(AUTH_TAG, "Legacy Google Sign-In cancelled (${e.statusCode}): ${e.message}")
                    onAuthCancelled()
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    onAuthError("No internet connection. Please turn on your network and try again.")
                }
                CommonStatusCodes.DEVELOPER_ERROR -> {
                    Log.e(AUTH_TAG, "Google Sign-In DEVELOPER_ERROR (10)", e)
                    onAuthError("Google Sign-In configuration error on this build. Please update the app or contact support.")
                }
                else -> {
                    Log.e(AUTH_TAG, "Legacy Google Sign-In failed (code=${e.statusCode}): ${e.message}", e)
                    onAuthError("Google Sign-In failed (code ${e.statusCode}). Please check your Gmail account and try again.")
                }
            }
        } catch (t: Throwable) {
            Log.e(AUTH_TAG, "Legacy Google Sign-In failed", t)
            onAuthError(t.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }
}

fun switchGoogleCloudAccount(
    context: Context,
    credentialManager: CredentialManager?,
    onBeforeSignOut: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    onLaunchLegacyFallback: ((Intent) -> Unit)? = null
) {
    scope.launch {
        withContext(NonCancellable) {
            runCatching { onBeforeSignOut() }
            runCatching { credentialManager?.clearCredentialState(ClearCredentialStateRequest()) }
            runCatching {
                val clientId = resolveDefaultWebClientId(context)
                if (!clientId.isNullOrBlank()) {
                    GoogleSignIn.getClient(context, buildLegacyGoogleSignInOptions(clientId)).signOut().awaitGmsTask()
                }
            }
            runCatching { safeFirebaseAuth(context)?.signOut() }
        }
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onAuthError = onAuthError,
            scope = scope,
            onAuthCancelled = onAuthCancelled,
            onLaunchLegacyFallback = onLaunchLegacyFallback
        )
    }
}

fun signOutFromGoogleCloud(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    context: Context? = null,
    onBeforeSignOut: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    context?.let { CloudSyncPrefsStore(it).setExplicitGoogleSignOut(true) }
    scope.launch {
        withContext(NonCancellable) {
            try {
                onBeforeSignOut()
            } catch (t: Throwable) {
                Log.w(AUTH_TAG, "Pre-sign-out listener teardown warning: ${t.message}")
            }
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (t: Throwable) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${t.message}")
            }
            if (context != null) {
                runCatching {
                    val clientId = resolveDefaultWebClientId(context)
                    if (!clientId.isNullOrBlank()) {
                        GoogleSignIn.getClient(context, buildLegacyGoogleSignInOptions(clientId)).signOut().awaitGmsTask()
                    }
                }
            }
            runCatching { safeFirebaseAuth(context)?.signOut() }
            onSignOutComplete()
        }
    }
}
