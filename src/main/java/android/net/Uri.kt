/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.net

import android.annotation.SystemApi
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.concurrent.Volatile

/**
 * Immutable URI reference. A URI reference includes a URI and a fragment, the component of the URI following a '#'.
 * Builds and parses URI references which conform to [RFC 2396](http://www.faqs.org/rfcs/rfc2396.html).
 *
 * In the interest of performance, this class performs little to no validation. Behavior is undefined for invalid input.
 * This class is very forgiving--in the face of invalid input, it will return garbage rather than throw an exception
 * unless otherwise specified.
 */
abstract class Uri private constructor() : Parcelable, Comparable<Uri> {
    /**
     * Holds a placeholder for strings which haven't been cached. This enables us to cache null. We intentionally create
     * a new String instance so we can compare its identity and there is no chance we will confuse it with user data.
     *
     * NOTE This value is held in its own Holder class is so that referring to [NotCachedHolder.NOT_CACHED] does not
     * trigger `Uri.<clinit>`. For example, `PathPart.<init>` uses `NotCachedHolder.NOT_CACHED` but must not trigger
     * `Uri.<clinit>`: Otherwise, the initialization of `Uri.EMPTY` would see a `null` value for `PathPart.EMPTY`!
     *
     * @hide
     */
    internal object NotCachedHolder {
        const val NOT_CACHED: String = "NOT CACHED"
    }

    /**
     * Returns true if this URI is hierarchical like "http://google.com". Absolute URIs are hierarchical if the
     * scheme-specific part starts with a '/'. Relative URIs are always hierarchical.
     */
    abstract val isHierarchical: Boolean

    /**
     * Returns true if this URI is opaque like "mailto:nobody@google.com". The scheme-specific part of an opaque URI
     * cannot start with a '/'.
     */
    val isOpaque: Boolean
        get() = !isHierarchical

    /**
     * Returns true if this URI is relative, i.e.&nbsp;if it doesn't contain an explicit scheme.
     *
     * @return true if this URI is relative, false if it's absolute
     */
    abstract val isRelative: Boolean

    /**
     * Returns true if this URI is absolute, i.e.&nbsp;if it contains an explicit scheme.
     *
     * @return true if this URI is absolute, false if it's relative
     */
    val isAbsolute: Boolean
        get() = !isRelative

    /**
     * Gets the scheme of this URI. Example: "http"
     *
     * @return the scheme or null if this is a relative URI
     */
    abstract val scheme: String?

    /**
     * Gets the scheme-specific part of this URI, i.e.&nbsp;everything between the scheme separator ':' and the fragment
     * separator '#'. If this is a relative URI, this method returns the entire URI. Decodes escaped octets.
     *
     * Example: "//www.google.com/search?q=android"
     *
     * @return the decoded scheme-specific-part
     */
    abstract val schemeSpecificPart: String?

    /**
     * Gets the scheme-specific part of this URI, i.e.&nbsp;everything between the scheme separator ':' and the fragment
     * separator '#'. If this is a relative URI, this method returns the entire URI. Leaves escaped octets intact.
     *
     * Example: "//www.google.com/search?q=android"
     *
     * @return the encoded scheme-specific-part
     */
    abstract val encodedSchemeSpecificPart: String?

    /**
     * Gets the decoded authority part of this URI. For server addresses, the authority is structured as follows: `[
     * userinfo '@' ] host [ ':' port ]`
     *
     * Examples: "google.com", "bob@google.com:80"
     *
     * @return the authority for this URI or null if not present
     */
    abstract val authority: String?

    /**
     * Gets the encoded authority part of this URI. For server addresses, the authority is structured as follows: `[
     * userinfo '@' ] host [ ':' port ]`
     *
     * Examples: "google.com", "bob@google.com:80"
     *
     * @return the authority for this URI or null if not present
     */
    abstract val encodedAuthority: String?

    /**
     * Gets the decoded user information from the authority. For example, if the authority is "nobody@google.com", this
     * method will return "nobody".
     *
     * @return the user info for this URI or null if not present
     */
    abstract val userInfo: String?

    /**
     * Gets the encoded user information from the authority. For example, if the authority is "nobody@google.com", this
     * method will return "nobody".
     *
     * @return the user info for this URI or null if not present
     */
    abstract val encodedUserInfo: String?

    /**
     * Gets the encoded host from the authority for this URI. For example, if the authority is "bob@google.com", this
     * method will return "google.com".
     *
     * @return the host for this URI or null if not present
     */
    abstract val host: String?

    /**
     * Gets the port from the authority for this URI. For example, if the authority is "google.com:80", this method will
     * return 80.
     *
     * @return the port for this URI or -1 if invalid or not present
     */
    abstract val port: Int

    /**
     * Gets the decoded path.
     *
     * @return the decoded path, or null if this is not a hierarchical URI (like "mailto:nobody@google.com") or the URI
     *   is invalid
     */
    abstract val path: String?

    /**
     * Gets the encoded path.
     *
     * @return the encoded path, or null if this is not a hierarchical URI (like "mailto:nobody@google.com") or the URI
     *   is invalid
     */
    abstract val encodedPath: String?

    /**
     * Gets the decoded query component from this URI. The query comes after the query separator ('?') and before the
     * fragment separator ('#'). This method would return "q=android" for "http://www.google.com/search?q=android".
     *
     * @return the decoded query or null if there isn't one
     */
    abstract val query: String?

    /**
     * Gets the encoded query component from this URI. The query comes after the query separator ('?') and before the
     * fragment separator ('#'). This method would return "q=android" for "http://www.google.com/search?q=android".
     *
     * @return the encoded query or null if there isn't one
     */
    abstract val encodedQuery: String?

    /**
     * Gets the decoded fragment part of this URI, everything after the '#'.
     *
     * @return the decoded fragment or null if there isn't one
     */
    abstract val fragment: String?

    /**
     * Gets the encoded fragment part of this URI, everything after the '#'.
     *
     * @return the encoded fragment or null if there isn't one
     */
    abstract val encodedFragment: String?

    /**
     * Gets the decoded path segments.
     *
     * @return decoded path segments, each without a leading or trailing '/'
     */
    abstract val pathSegments: List<String>

    /**
     * Gets the decoded last segment in the path.
     *
     * @return the decoded last segment or null if the path is empty
     */
    abstract val lastPathSegment: String?

    /**
     * Compares this Uri to another object for equality. Returns true if the encoded string representations of this Uri
     * and the given Uri are equal. Case counts. Paths are not normalized. If one Uri specifies a default port
     * explicitly and the other leaves it implicit, they will not be considered equal.
     */
    override fun equals(other: Any?): Boolean {
        if (other !is Uri) {
            return false
        }

        return toString() == other.toString()
    }

    /** Hashes the encoded string represention of this Uri consistently with [.equals]. */
    override fun hashCode(): Int {
        return toString().hashCode()
    }

    /** Compares the string representation of this Uri with that of another. */
    override fun compareTo(other: Uri): Int {
        return toString().compareTo(other.toString())
    }

    /** Returns the encoded string representation of this URI. Example: "http://google.com/" */
    abstract override fun toString(): String

    /**
     * Return a string representation of this URI that has common forms of PII redacted, making it safer to use for
     * logging purposes. For example, `tel:800-466-4411` is returned as `tel:xxx-xxx-xxxx` and
     * `http://example.com/path/to/item/` is returned as `http://example.com/...`. For all other uri schemes, only the
     * scheme, host and port are returned.
     *
     * @return the common forms PII redacted string of this URI
     * @hide
     */
    @SystemApi
    fun toSafeString(): String {
        val scheme = scheme
        val ssp = schemeSpecificPart
        val builder = StringBuilder(64)

        if (scheme != null) {
            builder.append(scheme)
            builder.append(":")
            if (
                scheme.equals("tel", ignoreCase = true) ||
                scheme.equals("sip", ignoreCase = true) ||
                scheme.equals("sms", ignoreCase = true) ||
                scheme.equals("smsto", ignoreCase = true) ||
                scheme.equals("mailto", ignoreCase = true) ||
                scheme.equals("nfc", ignoreCase = true)
            ) {
                if (ssp != null) {
                    for (element in ssp) {
                        val c = element
                        if (c == '-' || c == '@' || c == '.') {
                            builder.append(c)
                        } else {
                            builder.append('x')
                        }
                    }
                }
            } else {
                // For other schemes, let's be conservative about
                // the data we include -- only the host and port, not the query params, path or
                // fragment, because those can often have sensitive info.
                val host = host
                val port = port
                val path = path
                val authority = authority
                if (authority != null) builder.append("//")
                if (host != null) builder.append(host)
                if (port != -1) builder.append(":").append(port)
                if (authority != null || path != null) builder.append("/...")
            }
        }
        return builder.toString()
    }

    /** Constructs a new builder, copying the attributes from this Uri. */
    abstract fun buildUpon(): Builder

    /**
     * An implementation which wraps a String URI. This URI can be opaque or hierarchical, but we extend
     * AbstractHierarchicalUri in case we need the hierarchical functionality.
     */
    private class StringUri(uriString: String?) : AbstractHierarchicalUri() {
        /** URI string representation. */
        private val uriString: String

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(TYPE_ID)
            parcel.writeString8(uriString)
        }

        /** Cached scheme separator index. */
        @Volatile
        private var cachedSsi = NOT_CALCULATED

        /** Finds the first ':'. Returns -1 if none found. */
        fun findSchemeSeparator(): Int {
            return if (cachedSsi == NOT_CALCULATED) uriString.indexOf(':').also { cachedSsi = it } else cachedSsi
        }

        /** Cached fragment separator index. */
        @Volatile
        private var cachedFsi = NOT_CALCULATED

        /** Finds the first '#'. Returns -1 if none found. */
        fun findFragmentSeparator(): Int {
            return if (cachedFsi == NOT_CALCULATED) {
                uriString.indexOf('#', findSchemeSeparator()).also { cachedFsi = it }
            } else {
                cachedFsi
            }
        }

        override val isHierarchical: Boolean
            get() {
                val ssi = findSchemeSeparator()

                if (ssi == NOT_FOUND) {
                    // All relative URIs are hierarchical.
                    return true
                }

                if (uriString.length == ssi + 1) {
                    // No ssp.
                    return false
                }

                // If the ssp starts with a '/', this is hierarchical.
                return uriString[ssi + 1] == '/'
            }

        override val isRelative: Boolean
            get() {
                // Note: We return true if the index is 0
                return findSchemeSeparator() == NOT_FOUND
            }

        @Volatile
        override var scheme: String? = NotCachedHolder.NOT_CACHED
            get() {
                val cached = (field !== NotCachedHolder.NOT_CACHED)
                return if (cached) field else (parseScheme().also { field = it })
            }
            private set

        fun parseScheme(): String? {
            val ssi = findSchemeSeparator()
            return if (ssi == NOT_FOUND) null else uriString.substring(0, ssi)
        }

        var ssp: Part? = null
            get() = if (field == null) Part.fromEncoded(parseSsp()).also { field = it } else field
            private set

        override val encodedSchemeSpecificPart: String?
            get() = ssp!!.encoded

        override val schemeSpecificPart: String?
            get() = ssp!!.decoded

        fun parseSsp(): String {
            val ssi = findSchemeSeparator()
            val fsi = findFragmentSeparator()

            // Return everything between ssi and fsi.
            return if (fsi == NOT_FOUND) uriString.substring(ssi + 1) else uriString.substring(ssi + 1, fsi)
        }

        private var _authorityPart: Part? = null

        val authorityPart: Part
            get() {
                if (_authorityPart == null) {
                    val encodedAuthority = parseAuthority(this.uriString, findSchemeSeparator())
                    return Part.fromEncoded(encodedAuthority).also { _authorityPart = it }
                }

                return _authorityPart!!
            }

        override val encodedAuthority: String?
            get() = authorityPart.encoded

        override val authority: String?
            get() = authorityPart.decoded

        private var _pathPart: PathPart? = null

        val pathPart: PathPart
            get() = if (_pathPart == null) PathPart.fromEncoded(parsePath()).also { _pathPart = it } else _pathPart!!

        override val path: String?
            get() = pathPart.decoded

        override val encodedPath: String?
            get() = pathPart.encoded

        override val pathSegments: List<String>
            get() = pathPart.pathSegments.filterNotNull()

        fun parsePath(): String? {
            val uriString = this.uriString
            val ssi = findSchemeSeparator()

            // If the URI is absolute.
            if (ssi > -1) {
                // Is there anything after the ':'?
                val schemeOnly = ssi + 1 == uriString.length
                if (schemeOnly) {
                    // Opaque URI.
                    return null
                }

                // A '/' after the ':' means this is hierarchical.
                if (uriString[ssi + 1] != '/') {
                    // Opaque URI.
                    return null
                }
            } else {
                // All relative URIs are hierarchical.
            }

            return parsePath(uriString, ssi)
        }

        private var _queryPart: Part? = null

        val queryPart: Part
            get() = if (_queryPart == null) Part.fromEncoded(parseQuery()).also { _queryPart = it } else _queryPart!!

        override val encodedQuery: String?
            get() = queryPart.encoded

        fun parseQuery(): String? {
            // It doesn't make sense to cache this index. We only ever
            // calculate it once.
            val qsi = uriString.indexOf('?', findSchemeSeparator())
            if (qsi == NOT_FOUND) {
                return null
            }

            val fsi = findFragmentSeparator()

            if (fsi == NOT_FOUND) {
                return uriString.substring(qsi + 1)
            }

            if (fsi < qsi) {
                // Invalid.
                return null
            }

            return uriString.substring(qsi + 1, fsi)
        }

        override val query: String?
            get() = queryPart.decoded

        private var _fragmentPart: Part? = null

        init {
            if (uriString == null) {
                throw NullPointerException("uriString")
            }

            this.uriString = uriString
        }

        val fragmentPart: Part
            get() =
                if (_fragmentPart == null) {
                    Part.fromEncoded(parseFragment()).also { _fragmentPart = it }
                } else {
                    _fragmentPart!!
                }

        override val encodedFragment: String?
            get() = fragmentPart.encoded

        fun parseFragment(): String? {
            val fsi = findFragmentSeparator()
            return if (fsi == NOT_FOUND) null else uriString.substring(fsi + 1)
        }

        override val fragment: String?
            get() = fragmentPart.decoded

        override fun toString(): String {
            return uriString
        }

        override fun buildUpon(): Builder {
            return if (isHierarchical) {
                Builder().scheme(scheme).authority(authorityPart).path(pathPart).query(queryPart).fragment(fragmentPart)
            } else {
                Builder().scheme(scheme).opaquePart(ssp).fragment(fragmentPart)
            }
        }

        companion object {
            /** Used in parcelling. */
            const val TYPE_ID: Int = 1

            @JvmStatic
            fun readFrom(parcel: Parcel): Uri {
                return StringUri(parcel.readString8())
            }

            /**
             * Parses an authority out of the given URI string.
             *
             * @param uriString URI string
             * @param ssi scheme separator index, -1 for a relative URI
             * @return the authority or null if none is found
             */
            @JvmStatic
            fun parseAuthority(uriString: String, ssi: Int): String? {
                val length = uriString.length

                // If "//" follows the scheme separator, we have an authority.
                if (length > ssi + 2 && uriString[ssi + 1] == '/' && uriString[ssi + 2] == '/') {
                    // We have an authority.

                    // Look for the start of the path, query, or fragment, or the
                    // end of the string.

                    var end = ssi + 3
                    while (end < length) {
                        when (uriString[end]) {
                            '/',
                            '\\',
                            '?',
                            '#' -> break
                        }
                        end++
                    }

                    return uriString.substring(ssi + 3, end)
                } else {
                    return null
                }
            }

            /**
             * Parses a path out of this given URI string.
             *
             * @param uriString URI string
             * @param ssi scheme separator index, -1 for a relative URI
             * @return the path
             */
            @JvmStatic
            fun parsePath(uriString: String, ssi: Int): String {
                val length = uriString.length

                // Find start of path.
                var pathStart: Int
                if (length > ssi + 2 && uriString[ssi + 1] == '/' && uriString[ssi + 2] == '/') {
                    // Skip over authority to path.
                    pathStart = ssi + 3
                    while (pathStart < length) {
                        when (uriString[pathStart]) {
                            '?',
                            '#' -> return "" // Empty path.
                            '/',
                            '\\' -> // Per http://url.spec.whatwg.org/#host-state, the \ character
                                // is treated as if it were a / character when encountered in a
                                // host
                                break
                        }
                        pathStart++
                    }
                } else {
                    // Path starts immediately after scheme separator.
                    pathStart = ssi + 1
                }

                // Find end of path.
                var pathEnd = pathStart
                while (pathEnd < length) {
                    when (uriString[pathEnd]) {
                        '?',
                        '#' -> break
                    }
                    pathEnd++
                }

                return uriString.substring(pathStart, pathEnd)
            }
        }
    }

    /** Opaque URI. */
    private class OpaqueUri(override val scheme: String?, private val ssp: Part, fragment: Part?) : Uri() {
        private val _fragment: Part = fragment ?: Part.NULL

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(TYPE_ID)
            parcel.writeString8(toString())
        }

        override val isHierarchical: Boolean
            get() = false

        override val isRelative: Boolean
            get() = scheme == null

        override val encodedSchemeSpecificPart: String?
            get() = ssp.encoded

        override val schemeSpecificPart: String?
            get() = ssp.decoded

        override val authority: String?
            get() = null

        override val encodedAuthority: String?
            get() = null

        override val path: String?
            get() = null

        override val encodedPath: String?
            get() = null

        override val query: String?
            get() = null

        override val encodedQuery: String?
            get() = null

        override val fragment: String?
            get() = _fragment.decoded

        override val encodedFragment: String?
            get() = _fragment.encoded

        override val pathSegments: List<String>
            get() = emptyList()

        override val lastPathSegment: String?
            get() = null

        override val userInfo: String?
            get() = null

        override val encodedUserInfo: String?
            get() = null

        override val host: String?
            get() = null

        override val port: Int
            get() = -1

        @Volatile
        private var cachedString = NotCachedHolder.NOT_CACHED

        override fun toString(): String {
            val cached = cachedString !== NotCachedHolder.NOT_CACHED
            if (cached) {
                return cachedString
            }

            val sb = StringBuilder()

            sb.append(scheme).append(':')
            sb.append(encodedSchemeSpecificPart)

            if (!_fragment.isEmpty) {
                sb.append('#').append(_fragment.encoded)
            }

            return sb.toString().also { cachedString = it }
        }

        override fun buildUpon(): Builder {
            return Builder().scheme(this.scheme).opaquePart(this.ssp).fragment(this.fragment)
        }

        companion object {
            /** Used in parcelling. */
            const val TYPE_ID: Int = 2

            @JvmStatic
            fun readFrom(parcel: Parcel): Uri {
                val stringUri = StringUri(parcel.readString8())
                return OpaqueUri(stringUri.parseScheme(), stringUri.ssp!!, stringUri.fragmentPart)
            }
        }
    }

    /** Wrapper for path segment array. */
    class PathSegments(private val segments: Array<String?>?, override val size: Int) :
        AbstractList<String?>(), RandomAccess {
        override fun get(index: Int): String? {
            if (index >= size) {
                throw IndexOutOfBoundsException()
            }

            return segments!![index]
        }

        companion object {
            val EMPTY: PathSegments = PathSegments(null, 0)
        }
    }

    /** Builds PathSegments. */
    internal class PathSegmentsBuilder {
        private var segments: Array<String?>? = null
        var size: Int = 0

        fun add(segment: String?) {
            if (segments == null) {
                segments = arrayOfNulls(4)
            } else if (size + 1 == segments!!.size) {
                val expanded = arrayOfNulls<String>(segments!!.size * 2)
                System.arraycopy(segments!!, 0, expanded, 0, segments!!.size)
                segments = expanded
            }

            segments!![size++] = segment
        }

        fun build(): PathSegments {
            if (segments == null) {
                return PathSegments.EMPTY
            }

            try {
                return PathSegments(segments, size)
            } finally {
                // Makes sure this doesn't get reused.
                segments = null
            }
        }
    }

    /** Support for hierarchical URIs. */
    private abstract class AbstractHierarchicalUri : Uri() {
        override val lastPathSegment: String?
            get() {
                // TODO: If we haven't parsed all of the segments already, just
                // grab the last one directly so we only allocate one string.

                val segments = pathSegments
                val size = segments.size
                if (size == 0) {
                    return null
                }
                return segments[size - 1]
            }

        private var _userInfoPart: Part? = null

        val userInfoPart: Part
            get() =
                if (_userInfoPart == null) {
                    Part.fromEncoded(parseUserInfo()).also { _userInfoPart = it }
                } else {
                    _userInfoPart!!
                }

        override val encodedUserInfo: String?
            get() = userInfoPart.encoded

        fun parseUserInfo(): String? {
            val authority = encodedAuthority ?: return null

            val end = authority.lastIndexOf('@')
            return if (end == NOT_FOUND) null else authority.substring(0, end)
        }

        override val userInfo: String?
            get() = userInfoPart.decoded

        @Volatile
        override var host: String? = NotCachedHolder.NOT_CACHED
            get() {
                val cached = (field !== NotCachedHolder.NOT_CACHED)
                return if (cached) field else (parseHost().also { field = it })
            }

        fun parseHost(): String? {
            val authority = encodedAuthority ?: return null

            // Parse out user info and then port.
            val userInfoSeparator = authority.lastIndexOf('@')
            val portSeparator = findPortSeparator(authority)

            val encodedHost =
                if (portSeparator == NOT_FOUND) {
                    authority.substring(userInfoSeparator + 1)
                } else {
                    authority.substring(userInfoSeparator + 1, portSeparator)
                }

            return decode(encodedHost)
        }

        @Volatile
        override var port: Int = NOT_CALCULATED
            get() = if (field == NOT_CALCULATED) parsePort().also { field = it } else field

        fun parsePort(): Int {
            val authority = encodedAuthority
            val portSeparator = findPortSeparator(authority)
            if (portSeparator == NOT_FOUND) {
                return -1
            }

            val portString = decode(authority!!.substring(portSeparator + 1))!!
            try {
                return portString.toInt()
            } catch (e: NumberFormatException) {
                Log.w(LOG, "Error parsing port string.", e)
                return -1
            }
        }

        fun findPortSeparator(authority: String?): Int {
            if (authority == null) {
                return NOT_FOUND
            }

            // Reverse search for the ':' character that breaks as soon as a char that is neither
            // a colon nor an ascii digit is encountered. Thanks to the goodness of UTF-16 encoding,
            // it's not possible that a surrogate matches one of these, so this loop can just
            // look for characters rather than care about code points.
            for (i in authority.length - 1 downTo 0) {
                val character = authority[i].code
                if (':'.code == character) return i
                // Character.isDigit would include non-ascii digits
                if (character < '0'.code || character > '9'.code) return NOT_FOUND
            }
            return NOT_FOUND
        }
    }

    /** Hierarchical Uri. */
    private class HierarchicalUri( // can be null
        override val scheme: String?,
        authority: Part?,
        path: PathPart,
        query: Part?,
        fragment: Part?
    ) : AbstractHierarchicalUri() {
        private val _authority: Part = Part.nonNull(authority)
        private val _path: PathPart = generatePath(path)
        private val _query: Part = Part.nonNull(query)
        private val _fragment: Part = Part.nonNull(fragment)

        fun generatePath(originalPath: PathPart): PathPart {
            // In RFC3986 the path should be determined based on whether there is a scheme or
            // authority present (https://www.rfc-editor.org/rfc/rfc3986.html#section-3.3).
            val hasSchemeOrAuthority = (scheme != null && scheme.length > 0) || !_authority.isEmpty
            return if (hasSchemeOrAuthority) {
                PathPart.makeAbsolute(originalPath)
            } else {
                originalPath
            }
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(TYPE_ID)
            parcel.writeString8(toString())
        }

        override val isHierarchical: Boolean
            get() = true

        override val isRelative: Boolean
            get() = scheme == null

        var ssp: Part? = null
            get() = if (field == null) Part.fromEncoded(makeSchemeSpecificPart()).also { field = it } else field
            private set

        override val encodedSchemeSpecificPart: String?
            get() = ssp!!.encoded

        override val schemeSpecificPart: String?
            get() = ssp!!.decoded

        /** Creates the encoded scheme-specific part from its sub parts. */
        fun makeSchemeSpecificPart(): String {
            val builder = StringBuilder()
            appendSspTo(builder)
            return builder.toString()
        }

        fun appendSspTo(builder: StringBuilder) {
            val encodedAuthority = _authority.encoded
            if (encodedAuthority != null) {
                // Even if the authority is "", we still want to append "//".
                builder.append("//").append(encodedAuthority)
            }

            val encodedPath = _path.encoded
            if (encodedPath != null) {
                builder.append(encodedPath)
            }

            if (!_query.isEmpty) {
                builder.append('?').append(_query.encoded)
            }
        }

        override val authority: String?
            get() = _authority.decoded

        override val encodedAuthority: String?
            get() = _authority.encoded

        override val encodedPath: String?
            get() = _path.encoded

        override val path: String?
            get() = _path.decoded

        override val query: String?
            get() = _query.decoded

        override val encodedQuery: String?
            get() = _query.encoded

        override val fragment: String?
            get() = _fragment.decoded

        override val encodedFragment: String?
            get() = _fragment.encoded

        override val pathSegments: List<String>
            get() = _path.pathSegments.filterNotNull()

        @Volatile
        private var uriString = NotCachedHolder.NOT_CACHED

        override fun toString(): String {
            val cached = (uriString !== NotCachedHolder.NOT_CACHED)
            return if (cached) uriString else (makeUriString().also { uriString = it })
        }

        fun makeUriString(): String {
            val builder = StringBuilder()

            if (scheme != null) {
                builder.append(scheme).append(':')
            }

            appendSspTo(builder)

            if (!_fragment.isEmpty) {
                builder.append('#').append(_fragment.encoded)
            }

            return builder.toString()
        }

        override fun buildUpon(): Builder {
            return Builder().scheme(scheme).authority(_authority).path(_path).query(_query).fragment(_fragment)
        }

        companion object {
            /** Used in parcelling. */
            const val TYPE_ID: Int = 3

            @JvmStatic
            fun readFrom(parcel: Parcel): Uri {
                val stringUri = StringUri(parcel.readString8())
                return HierarchicalUri(
                    stringUri.scheme,
                    stringUri.authorityPart,
                    stringUri.pathPart,
                    stringUri.queryPart,
                    stringUri.fragmentPart
                )
            }
        }
    }

    /**
     * Helper class for building or manipulating URI references. Not safe for concurrent use.
     *
     * An absolute hierarchical URI reference follows the pattern: `<scheme>://<authority><absolute
     * path>?<query>#<fragment>`
     *
     * Relative URI references (which are always hierarchical) follow one of two patterns: `<relative or absolute
     * path>?<query>#<fragment>` or `//<authority><absolute path>?<query>#<fragment>`
     *
     * An opaque URI follows this pattern: `<scheme>:<opaque part>#<fragment>`
     *
     * Use [Uri.buildUpon] to obtain a builder representing an existing URI.
     */
    class Builder {
        private var scheme: String? = null
        private var opaquePart: Part? = null
        private var authority: Part? = null
        private var path: PathPart? = null
        private var query: Part? = null
        private var fragment: Part? = null

        /**
         * Sets the scheme.
         *
         * @param scheme name or `null` if this is a relative Uri
         */
        fun scheme(scheme: String?): Builder {
            if (scheme != null) {
                this.scheme = scheme.replace("://".toRegex(), "")
            } else {
                this.scheme = null
            }
            return this
        }

        fun opaquePart(opaquePart: Part?): Builder {
            this.opaquePart = opaquePart
            return this
        }

        /**
         * Encodes and sets the given opaque scheme-specific-part.
         *
         * @param opaquePart decoded opaque part
         */
        fun opaquePart(opaquePart: String?): Builder {
            return opaquePart(Part.fromDecoded(opaquePart))
        }

        /**
         * Sets the previously encoded opaque scheme-specific-part.
         *
         * @param opaquePart encoded opaque part
         */
        fun encodedOpaquePart(opaquePart: String?): Builder {
            return opaquePart(Part.fromEncoded(opaquePart))
        }

        fun authority(authority: Part?): Builder {
            // This URI will be hierarchical.
            this.opaquePart = null

            this.authority = authority
            return this
        }

        /** Encodes and sets the authority. */
        fun authority(authority: String?): Builder {
            return authority(Part.fromDecoded(authority))
        }

        /** Sets the previously encoded authority. */
        fun encodedAuthority(authority: String?): Builder {
            return authority(Part.fromEncoded(authority))
        }

        fun path(path: PathPart?): Builder {
            // This URI will be hierarchical.
            this.opaquePart = null

            this.path = path
            return this
        }

        /**
         * Sets the path. Leaves '/' characters intact but encodes others as necessary.
         *
         * If the path is not null and doesn't start with a '/', and if you specify a scheme and/or authority, the
         * builder will prepend the given path with a '/'.
         */
        fun path(path: String?): Builder {
            return path(PathPart.fromDecoded(path))
        }

        /**
         * Sets the previously encoded path.
         *
         * If the path is not null and doesn't start with a '/', and if you specify a scheme and/or authority, the
         * builder will prepend the given path with a '/'.
         */
        fun encodedPath(path: String?): Builder {
            return path(PathPart.fromEncoded(path))
        }

        /** Encodes the given segment and appends it to the path. */
        fun appendPath(newSegment: String?): Builder {
            return path(PathPart.appendDecodedSegment(path, newSegment))
        }

        /** Appends the given segment to the path. */
        fun appendEncodedPath(newSegment: String?): Builder {
            return path(PathPart.appendEncodedSegment(path, newSegment))
        }

        fun query(query: Part?): Builder {
            // This URI will be hierarchical.
            this.opaquePart = null

            this.query = query
            return this
        }

        /** Encodes and sets the query. */
        fun query(query: String?): Builder {
            return query(Part.fromDecoded(query))
        }

        /** Sets the previously encoded query. */
        fun encodedQuery(query: String?): Builder {
            return query(Part.fromEncoded(query))
        }

        fun fragment(fragment: Part?): Builder {
            this.fragment = fragment
            return this
        }

        /** Encodes and sets the fragment. */
        fun fragment(fragment: String?): Builder {
            return fragment(Part.fromDecoded(fragment))
        }

        /** Sets the previously encoded fragment. */
        fun encodedFragment(fragment: String?): Builder {
            return fragment(Part.fromEncoded(fragment))
        }

        /**
         * Encodes the key and value and then appends the parameter to the query string.
         *
         * @param key which will be encoded
         * @param value which will be encoded
         */
        fun appendQueryParameter(key: String?, value: String?): Builder {
            // This URI will be hierarchical.
            this.opaquePart = null

            val encodedParameter = (encode(key, null) + "=" + encode(value, null))

            if (query == null) {
                query = Part.fromEncoded(encodedParameter)
                return this
            }

            val oldQuery = query!!.encoded
            query =
                if (oldQuery.isNullOrEmpty()) {
                    Part.fromEncoded(encodedParameter)
                } else {
                    Part.fromEncoded("$oldQuery&$encodedParameter")
                }

            return this
        }

        /** Clears the the previously set query. */
        fun clearQuery(): Builder {
            return query(null as Part?)
        }

        /**
         * Constructs a Uri with the current attributes.
         *
         * @throws UnsupportedOperationException if the URI is opaque and the scheme is null
         */
        fun build(): Uri {
            if (opaquePart != null) {
                if (this.scheme == null) {
                    throw UnsupportedOperationException("An opaque URI must have a scheme.")
                }

                return OpaqueUri(scheme, opaquePart!!, fragment)
            } else {
                // Hierarchical URIs should not return null for getPath().
                var path = this.path
                if (path == null || path === PathPart.NULL) {
                    path = PathPart.EMPTY
                } else {
                    // If we have a scheme and/or authority, the path must
                    // be absolute. Prepend it with a '/' if necessary.
                    if (hasSchemeOrAuthority()) {
                        path = PathPart.makeAbsolute(path)
                    }
                }

                return HierarchicalUri(scheme, authority, path, query, fragment)
            }
        }

        private fun hasSchemeOrAuthority(): Boolean {
            return scheme != null || (authority != null && authority !== Part.NULL)
        }

        override fun toString(): String {
            return build().toString()
        }
    }

    /**
     * Returns a set of the unique names of all query parameters. Iterating over the set will return the names in
     * order of their first occurrence.
     *
     * @return a set of decoded names
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     */
    val queryParameterNames: Set<String>
        get() {
            if (isOpaque) {
                throw UnsupportedOperationException(NOT_HIERARCHICAL)
            }

            val query = encodedQuery ?: return emptySet()

            val names: MutableSet<String> = LinkedHashSet()
            var start = 0
            do {
                val next = query.indexOf('&', start)
                val end = if (next == -1) query.length else next

                var separator = query.indexOf('=', start)
                if (separator > end || separator == -1) {
                    separator = end
                }

                val name = query.substring(start, separator)
                names.add(decode(name)!!)

                // Move start to end of name.
                start = end + 1
            } while (start < query.length)

            return Collections.unmodifiableSet(names)
        }

    /**
     * Searches the query string for parameter values with the given key.
     *
     * @param key which will be encoded
     * @return a list of decoded values
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     * @throws NullPointerException if key is null
     */
    fun getQueryParameters(key: String?): List<String> {
        if (isOpaque) {
            throw UnsupportedOperationException(NOT_HIERARCHICAL)
        }
        if (key == null) {
            throw NullPointerException("key")
        }

        val query = encodedQuery ?: return emptyList()

        val encodedKey: String
        try {
            encodedKey = URLEncoder.encode(key, DEFAULT_ENCODING)
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError(e)
        }

        val values = ArrayList<String>()

        var start = 0
        do {
            val nextAmpersand = query.indexOf('&', start)
            val end = if (nextAmpersand != -1) nextAmpersand else query.length

            var separator = query.indexOf('=', start)
            if (separator > end || separator == -1) {
                separator = end
            }

            if (
                separator - start == encodedKey.length && query.regionMatches(start, encodedKey, 0, encodedKey.length)
            ) {
                if (separator == end) {
                    values.add("")
                } else {
                    values.add(decode(query.substring(separator + 1, end))!!)
                }
            }

            // Move start to end of name.
            if (nextAmpersand != -1) {
                start = nextAmpersand + 1
            } else {
                break
            }
        } while (true)

        return Collections.unmodifiableList(values)
    }

    /**
     * Searches the query string for the first value with the given key.
     *
     * **Warning:** Prior to Jelly Bean, this decoded the '+' character as '+' rather than ' '.
     *
     * @param key which will be encoded
     * @return the decoded value or null if no parameter is found
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     * @throws NullPointerException if key is null
     */
    fun getQueryParameter(key: String?): String? {
        if (isOpaque) {
            throw UnsupportedOperationException(NOT_HIERARCHICAL)
        }
        if (key == null) {
            throw NullPointerException("key")
        }

        val query = encodedQuery ?: return null

        val encodedKey = encode(key, null)
        val length = query.length
        var start = 0
        do {
            val nextAmpersand = query.indexOf('&', start)
            val end = if (nextAmpersand != -1) nextAmpersand else length

            var separator = query.indexOf('=', start)
            if (separator > end || separator == -1) {
                separator = end
            }

            if (
                separator - start == encodedKey!!.length && query.regionMatches(start, encodedKey, 0, encodedKey.length)
            ) {
                if (separator == end) {
                    return ""
                } else {
                    val encodedValue = query.substring(separator + 1, end)
                    return UriCodec.decode(encodedValue, true, StandardCharsets.UTF_8, false)
                }
            }

            // Move start to end of name.
            if (nextAmpersand != -1) {
                start = nextAmpersand + 1
            } else {
                break
            }
        } while (true)
        return null
    }

    /**
     * Searches the query string for the first value with the given key and interprets it as a boolean value. "false"
     * and "0" are interpreted as `false`, everything else is interpreted as `true`.
     *
     * @param key which will be decoded
     * @param defaultValue the default value to return if there is no query parameter for key
     * @return the boolean interpretation of the query parameter key
     */
    fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        var flag = getQueryParameter(key) ?: return defaultValue
        flag = flag.lowercase()
        return ("false" != flag && "0" != flag)
    }

    /**
     * Return an equivalent URI with a lowercase scheme component. This aligns the Uri with Android best practices for
     * intent filtering.
     *
     * For example, "HTTP://www.android.com" becomes "http://www.android.com"
     *
     * All URIs received from outside Android (such as user input, or external sources like Bluetooth, NFC, or the
     * Internet) should be normalized before they are used to create an Intent.
     *
     * This method does *not* validate bad URIs, or 'fix' poorly formatted URIs - so do not use it for input validation.
     * A Uri will always be returned, even if the Uri is badly formatted to begin with and a scheme component cannot be
     * found.
     *
     * @return normalized Uri (never null)
     */
    fun normalizeScheme(): Uri {
        val scheme = scheme ?: return this
        // give up

        val lowerScheme = scheme.lowercase()
        if (scheme == lowerScheme) return this // no change

        return buildUpon().scheme(lowerScheme).build()
    }

    /** Support for part implementations. */
    abstract class AbstractPart(encoded: String?, decoded: String?) {
        @Volatile
        protected var _encoded: String? = null

        @Volatile
        protected var _decoded: String? = null

        init {
            if (encoded !== NotCachedHolder.NOT_CACHED) {
                this._encoded = encoded
                this._decoded = NotCachedHolder.NOT_CACHED
            } else if (decoded !== NotCachedHolder.NOT_CACHED) {
                this._encoded = NotCachedHolder.NOT_CACHED
                this._decoded = decoded
            } else {
                throw IllegalArgumentException("Neither encoded nor decoded")
            }
        }

        abstract val encoded: String?

        val decoded: String?
            get() {
                val hasDecoded = _decoded !== NotCachedHolder.NOT_CACHED
                return if (hasDecoded) _decoded else (decode(_encoded).also { _decoded = it })
            }
    }

    /**
     * Immutable wrapper of encoded and decoded versions of a URI part. Lazily creates the encoded or decoded version
     * from the other.
     */
    open class Part private constructor(encoded: String?, decoded: String?) : AbstractPart(encoded, decoded) {
        open val isEmpty: Boolean
            get() = false

        override val encoded: String?
            get() {
                val hasEncoded = _encoded !== NotCachedHolder.NOT_CACHED
                return if (hasEncoded) _encoded else (encode(_decoded).also { _encoded = it })
            }

        private class EmptyPart(value: String?) : Part(value, value) {
            init {
                require(value.isNullOrEmpty()) { "Expected empty value, got: $value" }
                // Avoid having to re-calculate the non-canonical value.
                _decoded = value
                _encoded = _decoded
            }

            override val isEmpty: Boolean
                get() = true
        }

        companion object {
            /** A part with null values. */
            val NULL: Part = EmptyPart(null)

            /** A part with empty strings for values. */
            val EMPTY: Part = EmptyPart("")

            /** Returns given part or [.NULL] if the given part is null. */
            @JvmStatic
            fun nonNull(part: Part?): Part {
                return part ?: NULL
            }

            /**
             * Creates a part from the encoded string.
             *
             * @param encoded part string
             */
            @JvmStatic
            fun fromEncoded(encoded: String?): Part {
                return from(encoded, NotCachedHolder.NOT_CACHED)
            }

            /**
             * Creates a part from the decoded string.
             *
             * @param decoded part string
             */
            @JvmStatic
            fun fromDecoded(decoded: String?): Part {
                return from(NotCachedHolder.NOT_CACHED, decoded)
            }

            /**
             * Creates a part from the encoded and decoded strings.
             *
             * @param encoded part string
             * @param decoded part string
             */
            @JvmStatic
            fun from(encoded: String?, decoded: String?): Part {
                // We have to check both encoded and decoded in case one is
                // NotCachedHolder.NOT_CACHED.

                if (encoded == null) {
                    return NULL
                }
                if (encoded.isEmpty()) {
                    return EMPTY
                }

                if (decoded == null) {
                    return NULL
                }
                if (decoded.isEmpty()) {
                    return EMPTY
                }

                return Part(encoded, decoded)
            }
        }
    }

    /**
     * Immutable wrapper of encoded and decoded versions of a path part. Lazily creates the encoded or decoded version
     * from the other.
     */
    class PathPart private constructor(encoded: String?, decoded: String?) : AbstractPart(encoded, decoded) {
        override val encoded: String?
            get() {
                val hasEncoded = _encoded !== NotCachedHolder.NOT_CACHED

                // Don't encode '/'.
                return if (hasEncoded) _encoded else (encode(_decoded, "/").also { _encoded = it })
            }

        /** Cached path segments. This doesn't need to be volatile--we don't care if other threads see the result. */
        private var _pathSegments: PathSegments? = null

        /**
         * Gets the individual path segments. Parses them if necessary.
         *
         * @return parsed path segments or null if this isn't a hierarchical URI
         */
        val pathSegments: PathSegments
            get() {
                if (_pathSegments != null) {
                    return _pathSegments!!
                }

                val path = encoded ?: return PathSegments.EMPTY.also { _pathSegments = it }

                val segmentBuilder = PathSegmentsBuilder()

                var previous = 0
                var current: Int
                while ((path.indexOf('/', previous).also { current = it }) > -1) {
                    // This check keeps us from adding a segment if the path starts
                    // '/' and an empty segment for "//".
                    if (previous < current) {
                        val decodedSegment = decode(path.substring(previous, current))
                        segmentBuilder.add(decodedSegment)
                    }
                    previous = current + 1
                }

                // Add in the final path segment.
                if (previous < path.length) {
                    segmentBuilder.add(decode(path.substring(previous)))
                }

                return segmentBuilder.build().also { _pathSegments = it }
            }

        companion object {
            /** A part with null values. */
            val NULL: PathPart = PathPart(null, null)

            /** A part with empty strings for values. */
            val EMPTY: PathPart = PathPart("", "")

            @JvmStatic
            fun appendEncodedSegment(oldPart: PathPart?, newSegment: String?): PathPart {
                // If there is no old path, should we make the new path relative
                // or absolute? I pick absolute.

                if (oldPart == null) {
                    // No old path.
                    return fromEncoded("/$newSegment")
                }

                var oldPath = oldPart.encoded

                if (oldPath == null) {
                    oldPath = ""
                }

                val oldPathLength = oldPath.length
                val newPath =
                    if (oldPathLength == 0) {
                        // No old path.
                        "/$newSegment"
                    } else if (oldPath.get(oldPathLength - 1) == '/') {
                        oldPath + newSegment
                    } else {
                        "$oldPath/$newSegment"
                    }

                return fromEncoded(newPath)
            }

            @JvmStatic
            fun appendDecodedSegment(oldPart: PathPart?, decoded: String?): PathPart {
                val encoded = encode(decoded)

                // TODO: Should we reuse old PathSegments? Probably not.
                return appendEncodedSegment(oldPart, encoded)
            }

            /**
             * Creates a path from the encoded string.
             *
             * @param encoded part string
             */
            @JvmStatic
            fun fromEncoded(encoded: String?): PathPart {
                return from(encoded, NotCachedHolder.NOT_CACHED)
            }

            /**
             * Creates a path from the decoded string.
             *
             * @param decoded part string
             */
            @JvmStatic
            fun fromDecoded(decoded: String?): PathPart {
                return from(NotCachedHolder.NOT_CACHED, decoded)
            }

            /**
             * Creates a path from the encoded and decoded strings.
             *
             * @param encoded part string
             * @param decoded part string
             */
            @JvmStatic
            fun from(encoded: String?, decoded: String?): PathPart {
                if (encoded == null) {
                    return NULL
                }

                if (encoded.length == 0) {
                    return EMPTY
                }

                return PathPart(encoded, decoded)
            }

            /** Prepends path values with "/" if they're present, not empty, and they don't already start with "/". */
            @JvmStatic
            fun makeAbsolute(oldPart: PathPart): PathPart {
                val encodedCached = oldPart._encoded !== NotCachedHolder.NOT_CACHED

                // We don't care which version we use, and we don't want to force
                // unneccessary encoding/decoding.
                val oldPath = if (encodedCached) oldPart._encoded else oldPart._decoded

                if (oldPath == null || oldPath.length == 0 || oldPath.startsWith("/")) {
                    return oldPart
                }

                // Prepend encoded string if present.
                val newEncoded = if (encodedCached) "/" + oldPart._encoded else NotCachedHolder.NOT_CACHED

                // Prepend decoded string if present.
                val decodedCached = oldPart._decoded !== NotCachedHolder.NOT_CACHED
                val newDecoded = if (decodedCached) "/" + oldPart._decoded else NotCachedHolder.NOT_CACHED

                return PathPart(newEncoded, newDecoded)
            }
        }
    }

    /**
     * If this [Uri] is `file://`, then resolve and return its canonical path. Also fixes legacy emulated storage paths
     * so they are usable across user boundaries. Should always be called from the app process before sending elsewhere.
     *
     * @hide
     */
    val canonicalUri: Uri
        get() {
            if ("file" == scheme) {
                val canonicalPath: String
                try {
                    canonicalPath = File(path!!).canonicalPath
                } catch (e: IOException) {
                    return this
                }

                if (Environment.isExternalStorageEmulated()) {
                    val legacyPath = Environment.getLegacyExternalStorageDirectory().toString()

                    // Splice in user-specific path when legacy path is found
                    if (canonicalPath.startsWith(legacyPath)) {
                        return fromFile(
                            File(
                                Environment.getExternalStorageDirectory().toString(),
                                canonicalPath.substring(legacyPath.length + 1)
                            )
                        )
                    }
                }

                return fromFile(File(canonicalPath))
            } else {
                return this
            }
        }

    /**
     * Test if this is a path prefix match against the given Uri. Verifies that scheme, authority, and atomic path
     * segments match.
     *
     * @hide
     */
    fun isPathPrefixMatch(prefix: Uri): Boolean {
        if (scheme != prefix.scheme) return false
        if (authority != prefix.authority) return false

        val seg = pathSegments
        val prefixSeg = prefix.pathSegments

        val prefixSize = prefixSeg.size
        if (seg.size < prefixSize) return false

        for (i in 0 until prefixSize) {
            if (seg[i] != prefixSeg[i]) {
                return false
            }
        }

        return true
    }

    companion object {
        /** Log tag. */
        private val LOG: String = Uri::class.java.simpleName

        /** The empty URI, equivalent to "". */
        val EMPTY: Uri = HierarchicalUri(null, Part.NULL, PathPart.EMPTY, Part.NULL, Part.NULL)

        /** Index of a component which was not found. */
        private const val NOT_FOUND = -1

        /** Placeholder value for an index which hasn't been calculated yet. */
        private const val NOT_CALCULATED = -2

        /** Error message presented when a user tries to treat an opaque URI as hierarchical. */
        private const val NOT_HIERARCHICAL = "This isn't a hierarchical URI."

        /** Default encoding. */
        private const val DEFAULT_ENCODING = "UTF-8"

        /**
         * Creates a Uri which parses the given encoded URI string.
         *
         * @param uriString an RFC 2396-compliant, encoded URI
         * @return Uri for this given uri string
         * @throws NullPointerException if uriString is null
         */
        @JvmStatic
        fun parse(uriString: String): Uri {
            return StringUri(uriString)
        }

        /**
         * Creates a Uri from a file. The URI has the form "file://<absolute path>". Encodes path characters with the
         * exception of '/'.
         *
         * Example: "file:///tmp/android.txt"
         *
         * @return a Uri for the given file
         * @throws NullPointerException if file is null
         */
        @JvmStatic
        fun fromFile(file: File?): Uri {
            if (file == null) {
                throw NullPointerException("file")
            }

            val path = PathPart.fromDecoded(file.absolutePath)
            return HierarchicalUri("file", Part.EMPTY, path, Part.NULL, Part.NULL)
        }

        /**
         * Creates an opaque Uri from the given components. Encodes the ssp which means this method cannot be used to
         * create hierarchical URIs.
         *
         * @param scheme of the URI
         * @param ssp scheme-specific-part, everything between the scheme separator (':') and the fragment separator
         *   ('#'), which will get encoded
         * @param fragment fragment, everything after the '#', null if undefined, will get encoded
         * @return Uri composed of the given scheme, ssp, and fragment
         * @throws NullPointerException if scheme or ssp is null
         * @see Builder if you don't want the ssp and fragment to be encoded
         */
        @JvmStatic
        fun fromParts(scheme: String?, ssp: String?, fragment: String?): Uri {
            if (scheme == null) {
                throw NullPointerException("scheme")
            }
            if (ssp == null) {
                throw NullPointerException("ssp")
            }

            return OpaqueUri(scheme, Part.fromDecoded(ssp), Part.fromDecoded(fragment))
        }

        /** Identifies a null parcelled Uri. */
        private const val NULL_TYPE_ID = 0

        /** Reads Uris from Parcels. */
        @JvmStatic
        val CREATOR: Parcelable.Creator<Uri?> =
            object : Parcelable.Creator<Uri?> {
                override fun createFromParcel(`in`: Parcel): Uri? {
                    val type = `in`.readInt()
                    when (type) {
                        NULL_TYPE_ID -> return null
                        StringUri.TYPE_ID -> return StringUri.readFrom(`in`)
                        OpaqueUri.TYPE_ID -> return OpaqueUri.readFrom(`in`)
                        HierarchicalUri.TYPE_ID -> return HierarchicalUri.readFrom(`in`)
                    }

                    throw IllegalArgumentException("Unknown URI type: $type")
                }

                override fun newArray(size: Int): Array<Uri?> {
                    return arrayOfNulls(size)
                }
            }

        /**
         * Writes a Uri to a Parcel.
         *
         * @param out parcel to write to
         * @param uri to write, can be null
         */
        @JvmStatic
        fun writeToParcel(out: Parcel, uri: Uri?) {
            if (uri == null) {
                out.writeInt(NULL_TYPE_ID)
            } else {
                uri.writeToParcel(out, 0)
            }
        }

        private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

        /**
         * Encodes characters in the given string as '%'-escaped octets using the UTF-8 scheme. Leaves letters ("A-Z",
         * "a-z"), numbers ("0-9"), and unreserved characters ("_-!.~'()*") intact. Encodes all other characters with
         * the exception of those specified in the allow argument.
         *
         * @param s string to encode
         * @param allow set of additional characters to allow in the encoded form, null if no characters should be
         *   skipped
         * @return an encoded version of s suitable for use as a URI component, or null if s is null
         */
        @JvmOverloads
        @JvmStatic
        fun encode(s: String?, allow: String? = null): String? {
            if (s == null) {
                return null
            }

            // Lazily-initialized buffers.
            var encoded: StringBuilder? = null

            val oldLength = s.length

            // This loop alternates between copying over allowed characters and
            // encoding in chunks. This results in fewer method calls and
            // allocations than encoding one character at a time.
            var current = 0
            while (current < oldLength) {
                // Start in "copying" mode where we copy over allowed chars.

                // Find the next character which needs to be encoded.

                var nextToEncode = current
                while (nextToEncode < oldLength && isAllowed(s[nextToEncode], allow)) {
                    nextToEncode++
                }

                // If there's nothing more to encode...
                if (nextToEncode == oldLength) {
                    if (current == 0) {
                        // We didn't need to encode anything!
                        return s
                    } else {
                        // Presumably, we've already done some encoding.
                        encoded!!.append(s, current, oldLength)
                        return encoded.toString()
                    }
                }

                if (encoded == null) {
                    encoded = StringBuilder()
                }

                if (nextToEncode > current) {
                    // Append allowed characters leading up to this point.
                    encoded.append(s, current, nextToEncode)
                } else {
                    // assert nextToEncode == current
                }

                // Switch to "encoding" mode.

                // Find the next allowed character.
                current = nextToEncode
                var nextAllowed = current + 1
                while (nextAllowed < oldLength && !isAllowed(s[nextAllowed], allow)) {
                    nextAllowed++
                }

                // Convert the substring to bytes and encode the bytes as
                // '%'-escaped octets.
                val toEncode = s.substring(current, nextAllowed)
                try {
                    val bytes = toEncode.toByteArray(charset(DEFAULT_ENCODING))
                    val bytesLength = bytes.size
                    for (i in 0 until bytesLength) {
                        encoded.append('%')
                        encoded.append(HEX_DIGITS[(bytes[i].toInt() and 0xf0) shr 4])
                        encoded.append(HEX_DIGITS[bytes[i].toInt() and 0xf])
                    }
                } catch (e: UnsupportedEncodingException) {
                    throw AssertionError(e)
                }

                current = nextAllowed
            }

            // Encoded could still be null at this point if s is empty.
            return encoded?.toString() ?: s
        }

        /**
         * Returns true if the given character is allowed.
         *
         * @param c character to check
         * @param allow characters to allow
         * @return true if the character is allowed or false if it should be encoded
         */
        private fun isAllowed(c: Char, allow: String?): Boolean {
            return (c in 'A'..'Z') ||
                (c in 'a'..'z') ||
                (c in '0'..'9') ||
                "_-!.~'()*".indexOf(c) != NOT_FOUND ||
                (allow != null && allow.indexOf(c) != NOT_FOUND)
        }

        /**
         * Encodes a value it wasn't already encoded.
         *
         * @param value string to encode
         * @param allow characters to allow
         * @return encoded value
         * @hide
         */
        @JvmStatic
        fun encodeIfNotEncoded(value: String?, allow: String?): String? {
            if (value == null) return null
            if (isEncoded(value, allow)) return value
            return encode(value, allow)
        }

        /**
         * Returns true if the given string is already encoded to safe characters.
         *
         * @param value string to check
         * @param allow characters to allow
         * @return true if the string is already encoded or false if it should be encoded
         */
        @JvmStatic
        private fun isEncoded(value: String?, allow: String?): Boolean {
            if (value == null) return true
            for (c in value) {
                // Allow % because that's the prefix for an encoded character. This method will fail
                // for decoded strings whose onlyinvalid character is %, but it's assumed that %
                // alone cannot cause malicious behavior in the framework.
                if (!isAllowed(c, allow) && c != '%') {
                    return false
                }
            }
            return true
        }

        /**
         * Decodes '%'-escaped octets in the given string using the UTF-8 scheme. Replaces invalid octets with the
         * unicode replacement character ("\\uFFFD").
         *
         * @param s encoded string to decode
         * @return the given string with escaped octets decoded, or null if s is null
         */
        @JvmStatic
        fun decode(s: String?): String? {
            if (s == null) {
                return null
            }
            return UriCodec.decode(s, false, /* convertPlus */ StandardCharsets.UTF_8, false /* throwOnFailure */)
        }

        /**
         * Decodes a string if it was encoded, indicated by containing a %.
         *
         * @param value encoded string to decode
         * @return decoded value
         * @hide
         */
        @JvmStatic
        fun decodeIfNeeded(value: String?): String? {
            if (value == null) return null
            if (value.contains("%")) return decode(value)
            return value
        }

        /**
         * Creates a new Uri by appending an already-encoded path segment to a base Uri.
         *
         * @param baseUri Uri to append path segment to
         * @param pathSegment encoded path segment to append
         * @return a new Uri based on baseUri with the given segment appended to the path
         * @throws NullPointerException if baseUri is null
         */
        @JvmStatic
        fun withAppendedPath(baseUri: Uri, pathSegment: String?): Uri {
            var builder = baseUri.buildUpon()
            builder = builder.appendEncodedPath(pathSegment)
            return builder.build()
        }
    }
}
