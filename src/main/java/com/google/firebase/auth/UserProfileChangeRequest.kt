package com.google.firebase.auth

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

class UserProfileChangeRequest private constructor(
    internal val displayName: String?,
    internal val photoUrl: String?
) : Parcelable {
    override fun describeContents(): Int = displayName.hashCode() + photoUrl.hashCode()

    override fun writeToParcel(
        dest: Parcel,
        flags: Int
    ) {
        dest.writeString(displayName)
        dest.writeString(photoUrl)
    }

    internal companion object CREATOR : Parcelable.Creator<UserProfileChangeRequest> {
        override fun createFromParcel(parcel: Parcel): UserProfileChangeRequest {
            val displayName = parcel.readString()
            val photoUri = parcel.readString()
            return UserProfileChangeRequest(displayName, photoUri)
        }

        override fun newArray(size: Int): Array<UserProfileChangeRequest?> = arrayOfNulls(size)
    }

    class Builder {
        private var displayName: String? = null
        private var photoUri: Uri? = null

        fun setDisplayName(name: String?): Builder {
            this.displayName = name
            return this
        }

        fun setPhotoUri(uri: Uri?): Builder {
            this.photoUri = uri
            return this
        }

        fun build(): UserProfileChangeRequest = UserProfileChangeRequest(displayName, photoUri?.toString())
    }
}
