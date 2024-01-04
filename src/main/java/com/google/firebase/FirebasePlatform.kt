package com.google.firebase

import java.io.File

abstract class FirebasePlatform {

    companion object {

        internal lateinit var firebasePlatform: FirebasePlatform

        fun initializeFirebasePlatform(platform: FirebasePlatform) {
            firebasePlatform = platform
            // prevent coroutines from thinking its on android
            System.setProperty("kotlinx.coroutines.fast.service.loader", "false")
        }
    }

    abstract fun store(key: String, value: String)

    abstract fun retrieve(key: String): String?

    abstract fun clear(key: String)

    abstract fun log(msg: String)

    open fun getDatabasePath(name: String): File = File(System.getProperty("java.io.tmpdir"))
}
