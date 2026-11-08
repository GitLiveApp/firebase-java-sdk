package android.os

import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

/**
 * A file descriptor on the JVM. There is no Parcel to carry it across processes, so it simply holds the
 * [RandomAccessFile] opened on the file; Firebase Storage opens one to learn the size of a file it uploads.
 */
class ParcelFileDescriptor private constructor(private val file: RandomAccessFile) : Closeable {

    val fileDescriptor: FileDescriptor
        get() = file.fd

    /** The size of the file in bytes, or -1 when it cannot be determined. */
    val statSize: Long
        get() = try {
            file.length()
        } catch (e: IOException) {
            -1
        }

    @Throws(IOException::class)
    override fun close() = file.close()

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
            val writable = mode and MODE_WRITE_ONLY != 0
            if (!file.exists() && (!writable || mode and MODE_CREATE == 0)) throw FileNotFoundException(file.path)
            val opened = RandomAccessFile(file, if (writable) "rw" else "r")
            if (writable && mode and MODE_TRUNCATE != 0) opened.setLength(0)
            if (writable && mode and MODE_APPEND != 0) opened.seek(opened.length())
            return ParcelFileDescriptor(opened)
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
    }
}
