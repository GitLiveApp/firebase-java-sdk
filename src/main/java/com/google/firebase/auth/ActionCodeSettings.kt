package com.google.firebase.auth

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the link in an action email leads and how the app receives it, mirroring the Android class; sent with
 * `sendOobCode` as its continue URL, bundle ids and link domain.
 */
class ActionCodeSettings private constructor(
    val url: String,
    val iOSBundle: String?,
    val androidPackageName: String?,
    val androidInstallApp: Boolean,
    val androidMinimumVersion: String?,
    private val handleCodeInApp: Boolean,
    val linkDomain: String?,
    private val dynamicLinkDomain: String?
) {
    fun canHandleCodeInApp(): Boolean = handleCodeInApp

    /** The `sendOobCode` request fields these settings add. */
    internal fun toRequestFields(): Map<String, JsonElement> =
        buildMap {
            put("continueUrl", JsonPrimitive(url))
            put("canHandleCodeInApp", JsonPrimitive(handleCodeInApp))
            iOSBundle?.let { put("iOSBundleId", JsonPrimitive(it)) }
            androidPackageName?.let {
                put("androidPackageName", JsonPrimitive(it))
                put("androidInstallApp", JsonPrimitive(androidInstallApp))
                androidMinimumVersion?.let { version -> put("androidMinimumVersion", JsonPrimitive(version)) }
            }
            linkDomain?.let { put("linkDomain", JsonPrimitive(it)) }
            dynamicLinkDomain?.let { put("dynamicLinkDomain", JsonPrimitive(it)) }
        }

    class Builder internal constructor() {
        private var urlValue: String? = null
        private var iOSBundleIdValue: String? = null
        private var androidPackageName: String? = null
        private var androidInstallApp: Boolean = false
        private var androidMinimumVersion: String? = null
        private var handleCodeInAppValue: Boolean = false
        private var linkDomainValue: String? = null
        private var dynamicLinkDomainValue: String? = null

        val url: String? get() = urlValue

        val iOSBundleId: String? get() = iOSBundleIdValue

        val handleCodeInApp: Boolean get() = handleCodeInAppValue

        val linkDomain: String? get() = linkDomainValue

        @Deprecated("Use linkDomain")
        val dynamicLinkDomain: String? get() = dynamicLinkDomainValue

        fun setUrl(url: String): Builder = apply { urlValue = url }

        fun setIOSBundleId(iOSBundleId: String): Builder = apply { iOSBundleIdValue = iOSBundleId }

        fun setAndroidPackageName(androidPackageName: String, installIfNotAvailable: Boolean, minimumVersion: String?): Builder = apply {
            this.androidPackageName = androidPackageName
            this.androidInstallApp = installIfNotAvailable
            this.androidMinimumVersion = minimumVersion
        }

        fun setHandleCodeInApp(canHandleCodeInApp: Boolean): Builder = apply { handleCodeInAppValue = canHandleCodeInApp }

        fun setLinkDomain(linkDomain: String): Builder = apply { linkDomainValue = linkDomain }

        @Deprecated("Use setLinkDomain")
        fun setDynamicLinkDomain(dynamicLinkDomain: String): Builder = apply { dynamicLinkDomainValue = dynamicLinkDomain }

        fun build(): ActionCodeSettings {
            val url = requireNotNull(urlValue) { "ActionCodeSettings url must be set" }
            require(url.isNotBlank()) { "ActionCodeSettings url must not be blank" }
            return ActionCodeSettings(url, iOSBundleIdValue, androidPackageName, androidInstallApp, androidMinimumVersion, handleCodeInAppValue, linkDomainValue, dynamicLinkDomainValue)
        }
    }

    companion object {
        @JvmStatic
        fun newBuilder(): Builder = Builder()
    }
}
