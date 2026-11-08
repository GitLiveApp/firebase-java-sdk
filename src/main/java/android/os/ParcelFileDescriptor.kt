package android.os

import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * A file descriptor on the JVM. There is no Parcel to carry it across processes, so it simply holds the stream opened
 * on the file, with the access the `MODE_*` flags ask for: a [FileInputStream] for read-only, a [FileOutputStream] for
 * write-only (appending through the descriptor when `MODE_APPEND` is set) and a [RandomAccessFile] for read-write.
 * Firebase Storage opens one to learn the size of a file it uploads.
 */
class ParcelFileDescriptor private constructor(
    private val stream: Closeable,
    val fileDescriptor: FileDescriptor,
    private val size: () -> Long,
) : Closeable {

    /** The size of the file in bytes, or -1 when it cannot be determined. */
    val statSize: Long
        get() = try {
            size()
        } catch (e: IOException) {
            -1
        }

    @Throws(IOException::class)
    override fun close() = stream.close()

    companion object {
        const val MODE_WORLD_READABLE = 0x00000001
        const val MODE_WORLD_WRITEABLE = 0x00000002
        const val MODE_READ_ONLY = 0x10000000
        const val MODE_WRITE_ONLY = 0x20000000
        const val MODE_READ_WRITE = 0x30000000
        const val MODE_CREATE = 0x08000000
        const val MODE_TRUNCATE = 0x04000000
        const val MODE_APPEND = 0x02000000

        /** Opens [file] with the `MODE_*` flags in [mode], failing like Android when it is missing and may not be created. */
        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun open(file: File, mode: Int): ParcelFileDescriptor {
            val readable = mode and MODE_READ_ONLY != 0
            val writable = mode and MODE_WRITE_ONLY != 0
            require(readable || writable) { "Bad mode: $mode" }
            if (!file.exists() && (!writable || mode and MODE_CREATE == 0)) throw FileNotFoundException(file.path)
            val truncate = mode and MODE_TRUNCATE != 0
            val append = mode and MODE_APPEND != 0
            return when {
                !writable -> FileInputStream(file).let { ParcelFileDescriptor(it, it.fd) { it.channel.size() } }
                // The output stream truncates unless it appends, which is what the write-only modes ("w", "wt", "wa") ask for.
                !readable && (truncate || append) -> FileOutputStream(file, append).let { ParcelFileDescriptor(it, it.fd) { it.channel.size() } }
                else -> RandomAccessFile(file, "rw").let {
                    if (truncate) it.setLength(0)
                    if (append) it.seek(it.length())
                    ParcelFileDescriptor(it, it.fd) { it.length() }
                }
            }
        }

        /** The `MODE_*` flags for a mode string of `ContentResolver.openFileDescriptor`, as Android parses it. */
        @JvmStatic
        fun parseMode(mode: String): Int = when (mode) {
            "r" -> MODE_READ_ONLY
            "w", "wt" -> MODE_WRITE_ONLY or MODE_CREATE or MODE_TRUNCATE
            "wa" -> MODE_WRITE_ONLY or MODE_CREATE or MODE_APPEND
            "rw" -> MODE_READ_WRITE or MODE_CREATE
            "rwt" -> MODE_READ_WRITE or MODE_CREATE or MODE_TRUNCATE
            else -> throw IllegalArgumentException("Bad mode: $mode")
        }

        /** Unsupported: a raw descriptor number cannot be adopted on the JVM. SQLite's blob descriptors are not implemented. */
        @JvmStatic
        fun adoptFd(fd: Int): ParcelFileDescriptor = throw UnsupportedOperationException("Adopting a raw file descriptor ($fd) is not supported on the JVM")
    }
}
