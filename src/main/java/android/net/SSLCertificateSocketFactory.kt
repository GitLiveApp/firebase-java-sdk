package android.net

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class SSLCertificateSocketFactory() : SSLSocketFactory() {

    companion object {

        init {
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        }
        @JvmStatic
        fun getDefault(timeout: Int, cache: SSLSessionCache): SSLSocketFactory = SSLCertificateSocketFactory()
    }

    private val instance = SSLSocketFactory.getDefault() as SSLSocketFactory

    override fun getDefaultCipherSuites(): Array<String> = instance.defaultCipherSuites

    override fun createSocket(p0: Socket?, p1: String?, p2: Int, p3: Boolean): Socket = instance.createSocket(p0, p1, p2, p3)

    override fun createSocket(p0: String?, p1: Int): Socket = instance.createSocket(p0, p1)

    override fun createSocket(p0: String?, p1: Int, p2: InetAddress?, p3: Int): Socket = instance.createSocket(p0, p1)

    override fun createSocket(p0: InetAddress?, p1: Int): Socket = instance.createSocket(p0, p1)

    override fun createSocket(p0: InetAddress?, p1: Int, p2: InetAddress?, p3: Int): Socket = instance.createSocket(p0, p1, p2, p3)

    override fun getSupportedCipherSuites(): Array<String> = instance.supportedCipherSuites
}
