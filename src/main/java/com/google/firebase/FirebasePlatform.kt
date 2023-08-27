package com.google.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.remote.GrpcCallProvider
import com.google.firebase.firestore.util.Supplier
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.NettyChannelBuilder

abstract class FirebasePlatform {

    companion object {

        internal lateinit var firebasePlatform: FirebasePlatform

        fun initializeFirebasePlatform(platform: FirebasePlatform) {
            firebasePlatform = platform
            // prevent coroutines from thinking its on android
            System.setProperty("kotlinx.coroutines.fast.service.loader", "false")

            GrpcCallProvider::class.java
                .getDeclaredField("overrideChannelBuilderSupplier")
                .apply { trySetAccessible() }
                .set(
                    null,
                    object : Supplier<ManagedChannelBuilder<*>> {
                        override fun get(): ManagedChannelBuilder<*> = FirebaseFirestore.getInstance(FirebaseApp.INSTANCES.values.first()).firestoreSettings.run {
                            NettyChannelBuilder.forTarget(host).also { it.takeUnless { isSslEnabled }?.usePlaintext() }
                        }
                    }
                )
        }
    }

    abstract fun store(key: String, value: String)

    abstract fun retrieve(key: String): String?

    abstract fun clear(key: String)

    abstract fun log(msg: String)
}
