package android.content

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLConnection

/**
 * Resolves content URIs on the JVM, where there are no content providers: only `file:` URIs (and URIs with no scheme,
 * taken as paths) name anything. Firebase Storage's `putFile(Uri)` reads the uploaded file through this.
 */
open class ContentResolver {

    /** The file descriptor for [uri] opened with [mode] (`"r"`, `"w"`, `"wt"`, `"wa"`, `"rw"` or `"rwt"`). */
    @Throws(FileNotFoundException::class)
    open fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? =
        ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.parseMode(mode))

    @Throws(FileNotFoundException::class)
    open fun openInputStream(uri: Uri): InputStream? = FileInputStream(fileFor(uri))

    @Throws(FileNotFoundException::class)
    open fun openOutputStream(uri: Uri): OutputStream? = openOutputStream(uri, "w")

    /** An output stream on [uri] opened with [mode] (`"w"`, `"wt"`, `"wa"`, `"rw"` or `"rwt"`). */
    @Throws(FileNotFoundException::class)
    open fun openOutputStream(uri: Uri, mode: String): OutputStream? =
        FileOutputStream(fileFor(uri), ParcelFileDescriptor.parseMode(mode) and ParcelFileDescriptor.MODE_APPEND != 0)

    /** The MIME type guessed from the file name, or null when there is no guess. */
    open fun getType(uri: Uri): String? = uri.lastPathSegment?.let(URLConnection::guessContentTypeFromName)

    private fun fileFor(uri: Uri): File = when (uri.scheme) {
        null, SCHEME_FILE -> File(uri.path ?: throw FileNotFoundException("No path in $uri"))
        else -> throw FileNotFoundException("Cannot open ${uri.scheme}: URIs on the JVM: $uri")
    }

    companion object {
        const val SCHEME_CONTENT = "content"
        const val SCHEME_ANDROID_RESOURCE = "android.resource"
        const val SCHEME_FILE = "file"
    }
}
