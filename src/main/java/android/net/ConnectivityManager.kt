package android.net

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ConnectivityManager private constructor() {

    companion object {
        val instance = ConnectivityManager()
    }

    private val connected = FirebaseDatabase.getInstance().getReference(".info/connected")

    fun registerDefaultNetworkCallback(networkCallback: NetworkCallback) {
        connected.addValueEventListener(networkCallback)
    }

    fun unregisterNetworkCallback(networkCallback: NetworkCallback) {
        connected.removeEventListener(networkCallback)
    }

    open class NetworkCallback : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            when (data.getValue(Boolean::class.java)) {
                true -> onAvailable(null)
                else -> onLost(null)
            }
        }

        override fun onCancelled(error: DatabaseError) {
            throw error.toException()
        }

        open fun onAvailable(network: Network?) {}
        open fun onLost(network: Network?) {}
    }
}
