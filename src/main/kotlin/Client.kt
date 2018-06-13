import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import javax.xml.bind.DatatypeConverter


class Client(
        private val domain: String,
        private val apiKey: String,
        private val apiSecret: String,
        private val language: String = "en_US"
) {
    companion object {
        val logger = LoggerFactory.getLogger(Client::class.java)
    }

    fun createHeader(path: String, data: Map<String, Object>): Map<String, String> {
        return mapOf(
                Pair("KC-API-KEY", apiKey),
                Pair("KC-API-NONCE", System.currentTimeMillis().toString()),
                Pair("KC-API-SIGNATURE", createSignature(path, data, System.currentTimeMillis()))
        )
    }
    private fun createSignature(path: String, data: Map<String, Object>, nonce: Long): String {
        val queryString = data.toSortedMap().toList().fold("", {acc, pair ->  "$acc$pair.first=$pair.second&"})
        val strForSign = "$path/$nonce/$queryString"

        val signatureStr = Base64.getEncoder().encodeToString(strForSign.toByteArray(Charset.forName("UTF-8")))
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(apiSecret.toByteArray(Charset.forName("UTF-8")), "HmacSHA256")
        sha256_HMAC.init(secretKeySpec)
        return DatatypeConverter.printHexBinary(sha256_HMAC.doFinal(signatureStr.toByteArray(charset("UTF-8"))))
    }
}