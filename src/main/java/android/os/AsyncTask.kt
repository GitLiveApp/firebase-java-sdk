package android.os

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

abstract class AsyncTask {

    companion object {
        @JvmField
        val THREAD_POOL_EXECUTOR = Executor {
            GlobalScope.launch {
                it.run()
            }
        }
    }

    fun execute(vararg params: Any): AsyncTask {
        GlobalScope.launch {
            withContext(Dispatchers.Main) { onPreExecute() }
            val result = doInBackground(*params)
            withContext(Dispatchers.Main) { onPostExecute(result) }
        }
        return this
    }

    protected abstract fun doInBackground(vararg params: Any): Any

    protected open fun onPreExecute() {}

    protected open fun onPostExecute(result: Any) {}
}
