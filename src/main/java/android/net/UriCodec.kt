package android.net

import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

object UriCodec {
    private const val INVALID_INPUT_CHARACTER = '�'

    private fun hexCharToValue(c: Char): Int {
        return if (c in '0'..'9') {
            c.code - 48
        } else if (c in 'a'..'f') {
            10 + c.code - 97
        } else {
            if (c in 'A'..'F') 10 + c.code - 65 else -1
        }
    }

    @JvmStatic
    private fun unexpectedCharacterException(
        uri: String, name: String?, unexpected: Char, index: Int
    ): URISyntaxException {
        val nameString = if (name == null) "" else " in [$name]"
        return URISyntaxException(uri, "Unexpected character$nameString: $unexpected", index)
    }

    @Throws(URISyntaxException::class)
    private fun getNextCharacter(uri: String, index: Int, end: Int, name: String?): Char {
        if (index >= end) {
            val nameString = if (name == null) "" else " in [$name]"
            throw URISyntaxException(uri, "Unexpected end of string$nameString", index)
        } else {
            return uri[index]
        }
    }

    @JvmStatic
    fun decode(s: String, convertPlus: Boolean, charset: Charset, throwOnFailure: Boolean): String {
        val builder = StringBuilder(s.length)
        appendDecoded(builder, s, convertPlus, charset, throwOnFailure)
        return builder.toString()
    }

    private fun appendDecoded(
        builder: StringBuilder, s: String, convertPlus: Boolean, charset: Charset, throwOnFailure: Boolean
    ) {
        val decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).replaceWith("�")
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val byteBuffer = ByteBuffer.allocate(s.length)
        var i = 0

        while (i < s.length) {
            var c = s[i]
            ++i
            when (c) {
                '%' -> {
                    var hexValue: Byte = 0

                    var j = 0
                    while (j < 2) {
                        try {
                            c = getNextCharacter(s, i, s.length, null as String?)
                        } catch (e: URISyntaxException) {
                            if (throwOnFailure) {
                                throw IllegalArgumentException(e)
                            }

                            flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure)
                            builder.append('�')
                            return
                        }

                        ++i
                        val newDigit = hexCharToValue(c)
                        if (newDigit < 0) {
                            if (throwOnFailure) {
                                throw IllegalArgumentException(
                                    unexpectedCharacterException(
                                        s, null as String?, c, i - 1
                                    )
                                )
                            }

                            flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure)
                            builder.append('�')
                            break
                        }

                        hexValue = (hexValue * 16 + newDigit).toByte()
                        ++j
                    }

                    byteBuffer.put(hexValue)
                }

                '+' -> {
                    flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure)
                    builder.append((if (convertPlus) ' ' else '+'))
                }

                else -> {
                    flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure)
                    builder.append(c)
                }
            }
        }

        flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure)
    }

    private fun flushDecodingByteAccumulator(
        builder: StringBuilder, decoder: CharsetDecoder, byteBuffer: ByteBuffer, throwOnFailure: Boolean
    ) {
        if (byteBuffer.position() != 0) {
            byteBuffer.flip()

            try {
                builder.append(decoder.decode(byteBuffer))
            } catch (e: CharacterCodingException) {
                if (throwOnFailure) {
                    throw IllegalArgumentException(e)
                }

                builder.append('�')
            } finally {
                byteBuffer.flip()
                byteBuffer.limit(byteBuffer.capacity())
            }
        }
    }
}
