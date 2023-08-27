package android.os

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class Handler(looper: Looper?, callback: Handler.Callback?) {

    constructor(looper: Looper) : this(looper, null)

    fun post(runnable: Runnable): Boolean {
        GlobalScope.launch(Dispatchers.Main) {
            runnable.run()
        }
        return true
    }

    fun postDelayed(runnable: Runnable, time: Long): Boolean {
        GlobalScope.launch(Dispatchers.Main) {
            delay(time)
            runnable.run()
        }
        return true
    }

    fun getIMessenger(): IMessenger {
        return object : IMessenger {
            override fun send(p0: Message?) {
                TODO("not implemented")
            }

            override fun asBinder(): IBinder {
                TODO("not implemented")
            }
        }
    }

    fun obtainMessage(id: Int): Message? {
        return Message(id, null)
    }

    fun obtainMessage(id: Int, params: Any): Message? {
        return Message(id, params)
    }

    fun sendMessage(message: Message): Boolean {
        return when (message.id) {
            6 -> false
            7 -> false
            else -> { // Note the block
                throw IllegalArgumentException("${message.id}")
            }
        }
    }

    companion object {
        @JvmStatic
        fun createAsync(looper: Looper) = Handler(looper)
    }

    fun removeCallbacks(r: Runnable) {
    }

    interface Callback
}
