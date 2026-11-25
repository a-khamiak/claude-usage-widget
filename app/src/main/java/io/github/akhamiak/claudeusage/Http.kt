package io.github.akhamiak.claudeusage

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String, val retryAfterS: Long?)

/**
 * Minimal HTTP over HttpURLConnection (no networking library, §4).
 * Redirects are never followed, so a bearer token can't be forwarded to another host.
 * Throws IOException on network failure or timeout.
 */
open class Http(private val timeoutMs: Int = 10_000) {

    open fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String? = null,
    ): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = method
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.let { readCapped(it) } ?: ""
            return HttpResponse(code, text, parseRetryAfter(conn.getHeaderField("Retry-After")))
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(input: InputStream): String = input.use {
        val buf = it.readNBytesCompat(MAX_BODY)
        String(buf, Charsets.UTF_8)
    }

    private fun InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (out.size() < max) {
            val n = read(chunk, 0, minOf(chunk.size, max - out.size()))
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        if (out.size() >= max) throw IOException("response too large")
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BODY = 256 * 1024

        /** Retry-After in seconds; HTTP-date form is ignored (treated as absent). */
        fun parseRetryAfter(v: String?): Long? = v?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    }
}
