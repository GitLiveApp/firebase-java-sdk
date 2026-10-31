package com.google.firebase.auth

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the link in an action email leads and how the app receives it, mirroring the Android class; sent with
 * `sendOobCode` as its continue URL, bundle ids and link domain.
 */
class ActionCodeSettings private constructor(
    private val url: String,
    private val iOSBundleId: String?,
    private val androidPackageName: String?,
    private val androidInstallApp: Boolean,
    private val androidMinimumVersion: String?,
    private val handleCodeInApp: Boolean,
    private val linkDomain: String?,
    private val dynamicLinkDomain: String?
) {
    fun getUrl(): String = url

    fun getIOSBundle(): String? = iOSBundleId

    fun getAndroidPackageName(): String? = androidPackageName

    fun getAndroidInstallApp(): Boolean = androidInstallApp

    fun getAndroidMinimumVersion(): String? = androidMinimumVersion

    fun canHandleCodeInApp(): Boolean = handleCodeInApp

    fun getLinkDomain(): String? = linkDomain

    /** The `sendOobCode` request fields these settings add. */
    internal fun toRequestFields(): Map<String, JsonElement> =
        buildMap {
            put("continueUrl", JsonPrimitive(url))
            put("canHandleCodeInApp", JsonPrimitive(handleCodeInApp))
            iOSBundleId?.let { put("iOSBundleId", JsonPrimitive(it)) }
            androidPackageName?.let {
                put("androidPackageName", JsonPrimitive(it))
                put("androidInstallApp", JsonPrimitive(androidInstallApp))
                androidMinimumVersion?.let { version -> put("androidMinimumVersion", JsonPrimitive(version)) }
            }
            linkDomain?.let { put("linkDomain", JsonPrimitive(it)) }
            dynamicLinkDomain?.let { put("dynamicLinkDomain", JsonPrimitive(it)) }
        }

    class Builder internal constructor() {
        private var url: String? = null
        private var iOSBundleId: String? = null
        private var androidPackageName: String? = null
        private var androidInstallApp: Boolean = false
        private var androidMinimumVersion: String? = null
        private var handleCodeInApp: Boolean = false
        private var linkDomain: String? = null
        private var dynamicLinkDomain: String? = null

        fun setUrl(url: String): Builder = apply { this.url = url }

        fun getUrl(): String? = url

        fun setIOSBundleId(iOSBundleId: String): Builder = apply { this.iOSBundleId = iOSBundleId }

        fun getIOSBundleId(): String? = iOSBundleId

        fun setAndroidPackageName(androidPackageName: String, installIfNotAvailable: Boolean, minimumVersion: String?): Builder = apply {
            this.androidPackageName = androidPackageName
            this.androidInstallApp = installIfNotAvailable
            this.androidMinimumVersion = minimumVersion
        }

        fun setHandleCodeInApp(canHandleCodeInApp: Boolean): Builder = apply { this.handleCodeInApp = canHandleCodeInApp }

        fun getHandleCodeInApp(): Boolean = handleCodeInApp

        fun setLinkDomain(linkDomain: String): Builder = apply { this.linkDomain = linkDomain }

        fun getLinkDomain(): String? = linkDomain

        @Deprecated("Use setLinkDomain")
        fun setDynamicLinkDomain(dynamicLinkDomain: String): Builder = apply { this.dynamicLinkDomain = dynamicLinkDomain }

        @Deprecated("Use getLinkDomain")
        fun getDynamicLinkDomain(): String? = dynamicLinkDomain

        fun build(): ActionCodeSettings {
            val url = requireNotNull(url) { "ActionCodeSettings url must be set" }
            require(url.isNotBlank()) { "ActionCodeSettings url must not be blank" }
            return ActionCodeSettings(url, iOSBundleId, androidPackageName, androidInstallApp, androidMinimumVersion, handleCodeInApp, linkDomain, dynamicLinkDomain)
        }
    }

    companion object {
        @JvmStatic
        fun newBuilder(): Builder = Builder()
    }
}
