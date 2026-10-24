package com.cogito.camaras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

object Http {

    suspend fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        contentType: String? = null,
        bearer: String? = null,
        timeoutMs: Int = 30_000,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", Config.USER_AGENT)
            setRequestProperty("Accept-Language", "es")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded; charset=utf-8")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw HttpException(code, text.take(300))
            text
        } finally {
            conn.disconnect()
        }
    }

    fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
