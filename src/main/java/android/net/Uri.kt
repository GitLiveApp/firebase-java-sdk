package android.net

import java.net.URI
import java.util.*

class Uri(private val uri: URI) {

    companion object {
        @JvmStatic
        fun parse(uriString: String) = Uri(URI.create(uriString))
    }

    val scheme get() = uri.scheme
    val port get() = uri.port
    val host get() = uri.host

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
    }}
