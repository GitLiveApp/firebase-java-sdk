import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebasePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePlatformDispatcherTest {
    @Test
    fun `default handler callbacks retain main thread and posting order`() {
        FirebasePlatform.initializeFirebasePlatform(
            object : FirebasePlatform() {
                override fun store(key: String, value: String) = Unit
                override fun retrieve(key: String): String? = null
                override fun clear(key: String) = Unit
                override fun log(msg: String) = Unit
            }
        )
        lateinit var uiThread: Thread
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-ui-main").also { uiThread = it } }
            .asCoroutineDispatcher().use { dispatcher ->
                Dispatchers.setMain(dispatcher)
                try {
                    val values = Collections.synchronizedList(mutableListOf<Int>())
                    val threads = Collections.synchronizedList(mutableListOf<Thread>())
                    val completed = CountDownLatch(100)
                    val handler = Handler(Looper.getMainLooper())
                    repeat(100) { value ->
                        handler.post {
                            values.add(value)
                            threads.add(Thread.currentThread())
                            completed.countDown()
                        }
                    }
                    assertTrue(completed.await(5, TimeUnit.SECONDS))
                    assertEquals((0 until 100).toList(), values)
                    threads.forEach { assertSame(uiThread, it) }
                } finally {
                    Dispatchers.resetMain()
                }
            }
    }
}
