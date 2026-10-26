/*
 * Tests the android.text.format.DateUtils shim, used by Remote Config to format
 * the remaining throttle time in fetch errors.
 */
import android.text.format.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("00:00", DateUtils.formatElapsedTime(0))
        assertEquals("01:15", DateUtils.formatElapsedTime(75))
    }

    @Test
    fun `formats hours when present`() {
        assertEquals("1:02:05", DateUtils.formatElapsedTime(3725))
    }
}
