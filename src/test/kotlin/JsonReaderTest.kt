/*
 * Tests the android.util.JsonReader port on the response shape Firebase Installations parses.
 */
import android.util.JsonReader
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class JsonReaderTest {

    @Test
    fun `parses an installations response`() {
        val json = """{"name":"projects/1/installations/fid-123","fid":"fid-123","refreshToken":"refresh","authToken":{"token":"auth","expiresIn":"604800s"},"unknown":[1,{"a":true}]}"""
        val values = mutableMapOf<String, String>()

        JsonReader(StringReader(json)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (val name = reader.nextName()) {
                    "fid", "refreshToken" -> values[name] = reader.nextString()
                    "authToken" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            values["authToken." + reader.nextName()] = reader.nextString()
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        assertEquals(
            mapOf(
                "fid" to "fid-123",
                "refreshToken" to "refresh",
                "authToken.token" to "auth",
                "authToken.expiresIn" to "604800s"
            ),
            values
        )
    }
}
