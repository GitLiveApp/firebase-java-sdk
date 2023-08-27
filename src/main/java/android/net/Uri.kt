package android.net

import io.netty.handler.codec.http.QueryStringDecoder
import java.net.URI

class Uri(private val uri: URI) {

    companion object {
        @JvmStatic
        fun parse(uriString: String) = Uri(URI.create(uriString))
    }

    private val parameters by lazy {
        QueryStringDecoder(uri).parameters()
    }

    val scheme get() = uri.scheme
    val port get() = uri.port
    val host get() = uri.host
    fun getQueryParameter(name: String) = parameters[name]?.first()
}
