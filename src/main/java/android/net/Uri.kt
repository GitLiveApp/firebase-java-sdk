package android.net

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Collections

class Uri(private val uri: URI) {

    companion object {
        @JvmStatic
        fun parse(uriString: String) = Uri(URI.create(uriString))

        @JvmStatic
        fun encode(s: String?): String? = encode(s, null)

        @JvmStatic
        fun encode(s: String?, allow: String?): String? {
            return URLEncoder.encode(s, StandardCharsets.UTF_8)
        }

        @JvmStatic
        fun decode(s: String?): String? {
            return URLDecoder.decode(s, StandardCharsets.UTF_8)
        }
    }

    fun getScheme(): String = uri.scheme

    fun getPort(): Int = uri.port

    fun getHost(): String = uri.host

    fun getPath(): String = uri.path

    fun getAuthority(): String = uri.authority

    fun getQuery() = uri.query

    fun getFragment() = uri.fragment

    fun getQueryParameterNames(): Set<String> {
        val query: String = uri.query ?: return emptySet()
        val names: MutableSet<String> = LinkedHashSet()
        var start = 0
        do {
            val next = query.indexOf('&', start)
            val end = if ((next == -1)) query.length else next
            var separator = query.indexOf('=', start)
            if (separator > end || separator == -1) {
                separator = end
            }
            val name = query.substring(start, separator)
            names.add(name)
            // Move start to end of name.
            start = end + 1
        } while (start < query.length)
        return Collections.unmodifiableSet(names)
    }

    fun getQueryParameter(key: String?): String? {
        if (key == null) {
            throw NullPointerException("key")
        }
        val query: String = uri.query ?: return null
        val length = query.length
        var start = 0
        do {
            val nextAmpersand = query.indexOf('&', start)
            val end = if (nextAmpersand != -1) nextAmpersand else length
            var separator = query.indexOf('=', start)
            if (separator > end || separator == -1) {
                separator = end
            }
            if (separator - start == key.length &&
                query.regionMatches(start, key, 0, key.length)
            ) {
                if (separator == end) {
                    return ""
                } else {
                    return query.substring(separator + 1, end)
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

    fun buildUpon(): Builder {
        return Builder()
            .scheme(this.getScheme())
            .authority(this.getAuthority())
            .path(this.getPath())
            .query(this.getQuery())
            .fragment(this.getFragment())
    }

    override fun toString(): String {
        return uri.toString()
    }

    class Builder {
        private var scheme: String? = null
        private var opaquePart: String? = null
        private var authority: String? = null
        private var path: String? = null
        private var query: String? = null
        private var fragment: String? = null

        fun scheme(scheme: String?): Builder {
            this.scheme = scheme
            return this
        }

        fun opaquePart(opaquePart: String?): Builder {
            this.opaquePart = opaquePart
            return this
        }

        fun encodedOpaquePart(opaquePart: String?): Builder {
            return opaquePart(URLDecoder.decode(opaquePart, StandardCharsets.UTF_8.toString()))
        }

        fun authority(authority: String?): Builder {
            this.opaquePart = null
            this.authority = authority
            return this
        }

        fun encodedAuthority(authority: String?): Builder {
            return authority(URLDecoder.decode(authority, StandardCharsets.UTF_8.toString()))
        }

        fun path(path: String?): Builder {
            this.opaquePart = null
            this.path = path
            return this
        }

        fun encodedPath(path: String?): Builder {
            return this.path(URLDecoder.decode(path, StandardCharsets.UTF_8.toString()))
        }

        fun appendPath(newSegment: String?): Builder {
           val createdPath = Paths.get(this.path.orEmpty(), newSegment).toString()
            return this.path(createdPath)
        }

        fun appendEncodedPath(newSegment: String?): Builder {
            val newDecodedSegment = URLDecoder.decode(newSegment, StandardCharsets.UTF_8.toString())
            return appendPath(newDecodedSegment)
        }

        fun query(query: String?): Builder {
            this.opaquePart = null
            this.query = query
            return this
        }

        fun encodedQuery(query: String?): Builder {
            return this.query(URLDecoder.decode(query, StandardCharsets.UTF_8.toString()))
        }

        fun fragment(fragment: String?): Builder {
            this.fragment = fragment
            return this
        }

        fun encodedFragment(fragment: String?): Builder {
            return this.query(URLDecoder.decode(fragment, StandardCharsets.UTF_8.toString()))
        }

        fun appendQueryParameter(key: String?, value: String?): Builder {
            this.opaquePart = null
            val encodedParameter = encode(key) + "=" + encode(value)
            if (this.query == null) {
                this.query = decode(encodedParameter)
                return this
            } else {
                val oldQuery: String = encode(query)!!
                if (oldQuery.isNotEmpty()) {
                    this.query = decode("$oldQuery&$encodedParameter")
                } else {
                    this.query = decode(encodedParameter)
                }

                return this
            }
        }

        fun clearQuery(): Builder {
            return this.query(null)
        }

        fun build(): Uri {
            return if (this.opaquePart != null) {
                if (this.scheme == null) {
                    throw UnsupportedOperationException("An opaque URI must have a scheme.")
                } else {
                    Uri(URI(this.scheme, this.opaquePart, this.fragment))
                }
            } else {
                Uri(URI(this.scheme, this.authority, path, this.query, this.fragment))
            }
        }

        override fun toString(): String {
            return this.build().toString()
        }
    }

}

/** Index of a component which was not found.  */
private const val NOT_FOUND = -1

private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()