package android.os

interface IMessenger : IBinder {

    fun send(p0: Message?)
    fun asBinder(): IBinder
}
