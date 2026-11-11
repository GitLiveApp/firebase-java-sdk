/*
 * Tests the android.os.Parcel shim: the repackaged Firebase classes expose writeToParcel(Parcel, int) and a CREATOR,
 * and this checks that values written to a parcel read back the way they do on Android.
 */
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.ArrayMap
import com.google.firebase.Timestamp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelTest {

    @Test
    fun `reads back primitives and strings in order`() {
        val parcel = Parcel.obtain()
        parcel.writeInt(42)
        parcel.writeLong(1L shl 40)
        parcel.writeDouble(42.5)
        parcel.writeFloat(1.5f)
        parcel.writeBoolean(true)
        parcel.writeByte(7)
        parcel.writeString("value")
        parcel.writeString(null)
        parcel.writeByteArray(byteArrayOf(1, 2, 3))
        parcel.writeByteArray(null)
        assertEquals(10, parcel.dataSize())
        assertEquals(10, parcel.dataPosition())

        parcel.setDataPosition(0)
        assertEquals(42, parcel.readInt())
        assertEquals(1L shl 40, parcel.readLong())
        assertEquals(42.5, parcel.readDouble(), 0.0)
        assertEquals(1.5f, parcel.readFloat(), 0.0f)
        assertTrue(parcel.readBoolean())
        assertEquals(7.toByte(), parcel.readByte())
        assertEquals("value", parcel.readString())
        assertNull(parcel.readString())
        assertArrayEquals(byteArrayOf(1, 2, 3), parcel.createByteArray())
        assertNull(parcel.createByteArray())
        assertEquals(0, parcel.dataAvail())
        parcel.recycle()
        assertEquals(0, parcel.dataSize())
    }

    @Test
    fun `round trips a Firebase Parcelable through its CREATOR`() {
        val parcel = Parcel.obtain()
        parcel.writeParcelable(Timestamp(1_700_000_000L, 123_000), 0)
        parcel.writeParcelable(null, 0)
        parcel.setDataPosition(0)

        assertEquals(Timestamp(1_700_000_000L, 123_000), parcel.readParcelable<Timestamp>(Timestamp::class.java.classLoader))
        assertNull(parcel.readParcelable<Parcelable>(null))
    }

    @Test
    fun `round trips collections, bundles and tagged values`() {
        val parcel = Parcel.obtain()
        parcel.writeStringList(listOf("a", "b"))
        parcel.writeStringList(null)
        parcel.writeList(listOf(1, "two", 3L, null))
        parcel.writeMap(mapOf("int" to 1, "list" to listOf(true)))
        parcel.writeBundle(Bundle().apply { putString("key", "value"); putInt("int", 2) })
        parcel.writeBundle(null)
        parcel.writeArrayMap(ArrayMap<String, Any>().apply { put("k", 1.5) })
        parcel.writeValue(Timestamp(1L, 2))
        parcel.writeValue(arrayOf("x", "y"))
        parcel.writeValue(java.util.Date(5L))
        parcel.setDataPosition(0)

        assertEquals(listOf("a", "b"), parcel.createStringArrayList())
        assertNull(parcel.createStringArrayList())
        assertEquals(listOf(1, "two", 3L, null), parcel.readArrayList(null))
        assertEquals(mapOf("int" to 1, "list" to listOf(true)), parcel.readHashMap(null))
        val bundle = parcel.readBundle()!!
        assertEquals("value", bundle.getString("key"))
        assertEquals(2, bundle.getInt("int"))
        assertNull(parcel.readBundle())
        val arrayMap = ArrayMap<String, Any>()
        parcel.readArrayMap(arrayMap, null)
        assertEquals(1.5, arrayMap["k"])
        assertEquals(Timestamp(1L, 2), parcel.readValue(null))
        assertArrayEquals(arrayOf("x", "y"), parcel.readValue(null) as Array<*>)
        assertEquals(java.util.Date(5L), parcel.readValue(null))
        assertEquals(0, parcel.dataAvail())
    }

    @Test
    fun `marshalls and unmarshalls`() {
        val parcel = Parcel.obtain()
        parcel.writeInt(1)
        parcel.writeString("one")
        parcel.writeParcelable(Timestamp(3L, 4), 0)
        val bytes = parcel.marshall()

        val copy = Parcel.obtain()
        copy.unmarshall(bytes, 0, bytes.size)
        assertEquals(copy.dataSize(), copy.dataPosition())
        copy.setDataPosition(0)
        assertEquals(1, copy.readInt())
        assertEquals("one", copy.readString())
        assertEquals(Timestamp(3L, 4), copy.readParcelable<Timestamp>(null))
    }

    @Test
    fun `transports exceptions`() {
        val parcel = Parcel.obtain()
        parcel.writeNoException()
        parcel.writeException(IllegalArgumentException("bad"))
        parcel.setDataPosition(0)

        parcel.readException()
        val e = assertThrows(IllegalArgumentException::class.java) { parcel.readException() }
        assertEquals("bad", e.message)
        assertThrows(RuntimeException::class.java) { parcel.writeException(java.io.IOException("not transported")) }
    }

    @Test
    fun `fails clearly on misuse`() {
        val parcel = Parcel.obtain()
        parcel.writeString("text")
        assertThrows(IllegalStateException::class.java) { parcel.readInt() }
        parcel.setDataPosition(0)
        assertThrows(IllegalStateException::class.java) { parcel.readInt() }
        assertThrows(IllegalArgumentException::class.java) { parcel.setDataPosition(2) }
        parcel.setDataPosition(0)
        parcel.writeInt(5) // overwrites the string, like writing at a position does on Android
        assertEquals(1, parcel.dataSize())
        parcel.setDataPosition(0)
        assertEquals(5, parcel.readInt())
        assertFalse(parcel.dataAvail() > 0)
    }
}
