package com.google.firebase.auth

import android.net.Uri

/** A user's profile with one provider, mirroring the Android interface; [FirebaseUser] is the account's own. */
interface UserInfo {
    val displayName: String?
    val email: String?
    val phoneNumber: String?
    val photoUrl: Uri?
    val providerId: String
    val uid: String
    val isEmailVerified: Boolean
}

internal data class ProviderUserInfo(
    override val displayName: String?,
    override val email: String?,
    override val phoneNumber: String?,
    private val photoUrlString: String?,
    override val providerId: String,
    override val uid: String,
    override val isEmailVerified: Boolean
) : UserInfo {
    override val photoUrl: Uri? get() = photoUrlString?.let(Uri::parse)
}
