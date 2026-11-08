/*
 * Tests for the android.content.ContentResolver shim, through which Firebase Storage reads the file behind a
 * putFile(Uri) upload: the file's size via a ParcelFileDescriptor, its content type and its bytes.
 */
import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException

class ContentResolverTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val resolver = Application().contentResolver

    private fun file(name: String, content: String): File = File(folder.root, name).apply { writeText(content) }

    @Test
    fun `file descriptor reports the size of a file uri`() {
        val file = file("upload.txt", "thirteen byte")
        resolver.openFileDescriptor(Uri.parse(file.toURI().toString()), "r")!!.use { descriptor ->
            assertEquals(13, descriptor.statSize)
        }
    }

    @Test
    fun `file descriptor accepts a triple slash file uri and a bare path`() {
        val file = file("upload.txt", "content")
        resolver.openFileDescriptor(Uri.parse("file://${file.absolutePath}"), "r")!!.use { assertEquals(7, it.statSize) }
        resolver.openFileDescriptor(Uri.parse(file.absolutePath), "r")!!.use { assertEquals(7, it.statSize) }
    }

    @Test
    fun `input stream reads the file`() {
        val file = file("upload.bin", "bytes")
        val read = resolver.openInputStream(Uri.parse(file.toURI().toString()))!!.use { it.readBytes() }
        assertArrayEquals("bytes".toByteArray(), read)
    }

    @Test
    fun `output stream writes and appends`() {
        val file = File(folder.root, "download.txt")
        val uri = Uri.parse(file.toURI().toString())
        resolver.openOutputStream(uri)!!.use { it.write("one".toByteArray()) }
        resolver.openOutputStream(uri, "wa")!!.use { it.write("two".toByteArray()) }
        assertEquals("onetwo", file.readText())
        resolver.openOutputStream(uri, "wt")!!.use { it.write("three".toByteArray()) }
        assertEquals("three", file.readText())
    }

    @Test
    fun `type is guessed from the file name`() {
        assertEquals("text/plain", resolver.getType(Uri.parse("file:///tmp/notes.txt")))
        assertEquals("image/png", resolver.getType(Uri.parse("file:///tmp/photo.png")))
        assertNull(resolver.getType(Uri.parse("file:///tmp/blob")))
    }

    @Test
    fun `missing files and other schemes are not found`() {
        assertThrows(FileNotFoundException::class.java) {
            resolver.openFileDescriptor(Uri.parse(File(folder.root, "missing.txt").toURI().toString()), "r")
        }
        assertThrows(FileNotFoundException::class.java) {
            resolver.openInputStream(Uri.parse("content://media/external/images/1"))
        }
    }

    @Test
    fun `write modes create truncate and append`() {
        val file = File(folder.root, "modes.txt")
        assertThrows(FileNotFoundException::class.java) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("w")).use { assertEquals(0, it.statSize) }
        file.writeText("longer content")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("wa")).use { assertEquals(14, it.statSize) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("wt")).use { assertEquals(0, it.statSize) }
        assertThrows(IllegalArgumentException::class.java) { ParcelFileDescriptor.parseMode("x") }
    }
}
