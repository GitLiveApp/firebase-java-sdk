package com.google.firebase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

abstract class FirebasePlatform {

    open val mainDispatcher: CoroutineDispatcher
        get() = Dispatchers.Main

    internal val mainScope: CoroutineScope by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CoroutineScope(SupervisorJob() + mainDispatcher)
    }

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

    open fun getDatabasePath(name: String): File = File("${System.getProperty("java.io.tmpdir")}${File.separatorChar}$name")

    /** The directory the Firebase SDKs keep their files in, such as the DataStore of the firebase-common heartbeat store. */
    open fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"))
}
