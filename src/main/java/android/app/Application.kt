package android.app

import android.content.ComponentCallbacks
import android.content.Context

class Application : Context() {
    var minSdkVersion: Int = 0
    var targetSdkVersion: Int = 0

    fun registerActivityLifecycleCallbacks(callbacks: ActivityLifecycleCallbacks) {
    }

    fun registerComponentCallbacks(callbacks: ComponentCallbacks) {
    }

    interface ActivityLifecycleCallbacks
}
