/*
 * Remote Config tests ported one-to-one from firebase-kotlin-sdk's common tests
 * (firebase-config/src/commonTest/.../FirebaseRemoteConfig.kt) onto the Android API,
 * so the Kotlin SDK's JVM target behaves the same when it runs on this SDK.
 */
import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.initialize
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.get
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.Collections

class FirebaseRemoteConfigTest : FirebaseTest() {

    // Firebase logs from background threads, so the list is synchronized
    private val logs: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun log(message: String) {
        logs.add(message)
    }

    // On the JVM there is no Android package to fingerprint and no Analytics SDK for A/B testing
    private val packageNotFound = "FirebaseRemoteConfig No such package: app.teamhub.TeamHub"
    private val analyticsMissing = "FirebaseRemoteConfig Could not update ABT experiments."

    // Every test fails in tearDown if it logs anything else
    private val expectedLogs = listOf(
        "FirebaseApp Device unlocked",
        packageNotFound,
        analyticsMissing,
        // Logged by Installations only when it registers a new installation
        "ContentValues No such package: app.teamhub.TeamHub"
    )

    private val defaults = mapOf<String, Any>(
        "test_default_boolean" to true,
        "test_default_double" to 42.0,
        "test_default_long" to 42L,
        "test_default_string" to "Hello World"
    )

    private val remoteConfig: FirebaseRemoteConfig by lazy { Firebase.remoteConfig(app) }

    @After
    fun tearDown(): Unit = runTest {
        remoteConfig.reset().await()

        assertEquals(emptyList<String>(), logs.toList().filterNot { log -> expectedLogs.any { log.startsWith(it) } })
    }

    @Test
    fun testGettingValues(): Unit = runTest {
        remoteConfig.setDefaultsAsync(defaults).await()

        assertEquals(true, remoteConfig.getBoolean("test_default_boolean"))
        assertEquals(42.0, remoteConfig.getDouble("test_default_double"), 0.0)
        assertEquals(42L, remoteConfig.getLong("test_default_long"))
        assertEquals("Hello World", remoteConfig.getString("test_default_string"))
        assertEquals("Hello World", remoteConfig.getString("test_default_string"))

        val value: FirebaseRemoteConfigValue = remoteConfig["test_default_string"]
        assertEquals("Hello World", value.asString())
        assertEquals(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, value.source)
        assertEquals("Hello World", value.asByteArray().decodeToString())
    }

    @Test
    fun testNamedApp(): Unit = runTest {
        // Android keys Remote Config's local storage by app ID, so the named app needs its own
        val options = FirebaseOptions.Builder(app.options).setApplicationId("1:341458593155:web:bf8e1aa37efe01f32d42b7").build()
        val namedApp = Firebase.initialize(Application(), options, "named")
        val namedRemoteConfig = Firebase.remoteConfig(namedApp)
        namedRemoteConfig.setDefaultsAsync(mapOf("named_app_only" to "named")).await()

        assertEquals("named", namedRemoteConfig.getValue("named_app_only").asString())
        assertFalse(remoteConfig.all.containsKey("named_app_only"))
        namedRemoteConfig.reset().await()
    }

    @Test
    fun testGetAll(): Unit = runTest {
        remoteConfig.setDefaultsAsync(defaults).await()
        val all = remoteConfig.all
        assertEquals(true, all["test_default_boolean"]?.asBoolean())
        assertEquals(42.0, all["test_default_double"]?.asDouble())
        assertEquals(42L, all["test_default_long"]?.asLong())
        assertEquals("Hello World", all["test_default_string"]?.asString())
        assertEquals("Hello World", all["test_default_string"]?.asByteArray()?.decodeToString())
    }

    @Test
    fun testGetKeysByPrefix(): Unit = runTest {
        remoteConfig.setDefaultsAsync(defaults).await()
        val keys = remoteConfig.getKeysByPrefix("test_default")
        assertEquals(
            setOf(
                "test_default_boolean",
                "test_default_double",
                "test_default_long",
                "test_default_string"
            ),
            keys
        )
    }

    @Test
    fun testGetInfo(): Unit = runTest {
        val info = remoteConfig.info
        val defaultSettings = FirebaseRemoteConfigSettings.Builder().build()
        assertEquals(defaultSettings.fetchTimeoutInSeconds, info.configSettings.fetchTimeoutInSeconds)
        assertEquals(defaultSettings.minimumFetchIntervalInSeconds, info.configSettings.minimumFetchIntervalInSeconds)
        assertEquals(-1L, info.fetchTimeMillis)
        assertEquals(FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET, info.lastFetchStatus)
    }

    @Test
    fun testSetConfigSettings(): Unit = runTest {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setFetchTimeoutInSeconds(42)
                .setMinimumFetchIntervalInSeconds(42)
                .build()
        ).await()
        val info = remoteConfig.info
        assertEquals(42L, info.configSettings.fetchTimeoutInSeconds)
        assertEquals(42L, info.configSettings.minimumFetchIntervalInSeconds)
    }

    @Test
    fun `fetchAndActivate succeeds`(): Unit = runTest {
        remoteConfig.fetchAndActivate().await()

        assertEquals(FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS, remoteConfig.info.lastFetchStatus)

        val capturedLogs = logs.toList()
        assertTrue(capturedLogs.any { it.startsWith(packageNotFound) })
        assertTrue(capturedLogs.any { it.startsWith(analyticsMissing) })
    }

    // Unfortunately Firebase Remote Config is not implemented by Firebase emulator so it may be
    // tested against a real project only. Add "test_remote_string": "Hello from remote!" config
    // value in Firebase console for enabling this test case.
    @Test
    @Ignore
    fun testFetch(): Unit = runTest {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(60).build()
        ).await()

        remoteConfig.fetch().await()
        remoteConfig.activate().await()

        val value: FirebaseRemoteConfigValue = remoteConfig["test_remote_string"]
        assertEquals("Hello from remote!", value.asString())
        assertEquals(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, value.source)
    }

    @Test
    @Ignore
    fun testFetchAndActivate(): Unit = runTest {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(60).build()
        ).await()

        remoteConfig.fetchAndActivate().await()

        val value: FirebaseRemoteConfigValue = remoteConfig["test_remote_string"]
        assertEquals("Hello from remote!", value.asString())
        assertEquals(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, value.source)
    }
}
