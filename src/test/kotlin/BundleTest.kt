/*
 * Tests the android.os.Bundle shim against the Android class's semantics for the members the ported
 * Firebase libraries and firebase-kotlin-sdk's Analytics use.
 */
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleTest {

    @Test
    fun `stores and reads typed values`() {
        val bundle = Bundle()
        bundle.putString("string", "value")
        bundle.putInt("int", 42)
        bundle.putLong("long", 1L shl 40)
        bundle.putDouble("double", 42.5)
        bundle.putFloat("float", 1.5f)
        bundle.putBoolean("boolean", true)
        bundle.putBundle("bundle", Bundle().apply { putString("nested", "x") })

        assertEquals("value", bundle.getString("string"))
        assertEquals(42, bundle.getInt("int"))
        assertEquals(1L shl 40, bundle.getLong("long"))
        assertEquals(42.5, bundle.getDouble("double"), 0.0)
        assertEquals(1.5f, bundle.getFloat("float"), 0.0f)
        assertTrue(bundle.getBoolean("boolean"))
        assertEquals("x", bundle.getBundle("bundle")?.getString("nested"))
        assertEquals(setOf("string", "int", "long", "double", "float", "boolean", "bundle"), bundle.keySet())
        assertEquals(7, bundle.size())
        assertFalse(bundle.isEmpty())
    }

    @Test
    fun `returns the default for a missing key or another type`() {
        val bundle = Bundle()
        bundle.putString("string", "value")
        bundle.putString("null", null)

        assertFalse(bundle.containsKey("missing"))
        assertTrue(bundle.containsKey("null"))
        assertNull(bundle.get("missing"))
        assertNull(bundle.getString("missing"))
        assertEquals("default", bundle.getString("missing", "default"))
        assertEquals("default", bundle.getString("null", "default"))
        assertEquals(0, bundle.getInt("missing"))
        assertEquals(7, bundle.getInt("missing", 7))
        assertEquals(0, bundle.getInt("string"))
        assertEquals(0L, bundle.getLong("string"))
        assertEquals(0.0, bundle.getDouble("string"), 0.0)
        assertFalse(bundle.getBoolean("string"))
        assertTrue(bundle.getBoolean("missing", true))
        assertNull(bundle.getBundle("string"))
    }

    @Test
    fun `copies, merges, removes and clears`() {
        val source = Bundle(mapOf<String, Any>("a" to 1, "b" to "two"))
        val copy = Bundle(source)
        copy.putInt("c", 3)
        assertEquals(2, source.size())
        assertEquals(3, copy.size())

        val target = Bundle()
        target.putAll(copy)
        assertEquals("two", target.getString("b"))
        target.remove("b")
        assertFalse(target.containsKey("b"))
        target.clear()
        assertTrue(target.isEmpty())
    }
}
