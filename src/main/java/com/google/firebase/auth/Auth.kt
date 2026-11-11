/*
 * The Kotlin extensions the Android SDK's firebase-auth ships as com.google.firebase.auth.AuthKt, so that code written
 * against them compiles against this SDK like it does against the other ported libraries' facades (FirestoreKt,
 * DatabaseKt and the rest, which come with their jars).
 */
package com.google.firebase.auth

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp

/** Returns the [FirebaseAuth] instance of the default [FirebaseApp]. */
val Firebase.auth: FirebaseAuth
    get() = FirebaseAuth.getInstance()

/** Returns the [FirebaseAuth] instance of a given [FirebaseApp]. */
fun Firebase.auth(app: FirebaseApp): FirebaseAuth = FirebaseAuth.getInstance(app)

/** Returns a [ActionCodeSettings] instance initialized using the [init] function. */
fun actionCodeSettings(init: ActionCodeSettings.Builder.() -> Unit): ActionCodeSettings =
    ActionCodeSettings.newBuilder().apply(init).build()

/** Returns a [OAuthProvider] instance initialized using the [init] function. */
fun oAuthProvider(
    providerId: String,
    init: OAuthProvider.Builder.() -> Unit
): OAuthProvider = OAuthProvider.newBuilder(providerId).apply(init).build()

/** Returns a [OAuthProvider] instance initialized using the [init] function. */
fun oAuthProvider(
    providerId: String,
    firebaseAuth: FirebaseAuth,
    init: OAuthProvider.Builder.() -> Unit
): OAuthProvider = OAuthProvider.newBuilder(providerId, firebaseAuth).apply(init).build()

/** Returns a [AuthCredential] instance initialized using the [init] function. */
fun oAuthCredential(
    providerId: String,
    init: OAuthProvider.CredentialBuilder.() -> Unit
): AuthCredential = OAuthProvider.newCredentialBuilder(providerId).apply(init).build()

/** Returns a [UserProfileChangeRequest] instance initialized using the [init] function. */
fun userProfileChangeRequest(init: UserProfileChangeRequest.Builder.() -> Unit): UserProfileChangeRequest =
    UserProfileChangeRequest.Builder().apply(init).build()
