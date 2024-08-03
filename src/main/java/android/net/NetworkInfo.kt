package android.net

class NetworkInfo {
    var type: Int = 1 // ConnectivityManager.TYPE_WIFI
    val isConnectedOrConnecting: Boolean = true
    val isConnected: Boolean = true
    val isSuspended: Boolean = false
    val isAvailable: Boolean = true
}