// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.device

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

/** One frame of the panel, as the device reports it. */
class DeviceFrame(val image: BufferedImage, val number: Int, val isRefreshPending: Boolean)

/** A part of the device log. `end` is the position to pass to the next call. */
class DeviceLog(val text: String, val end: Long)

enum class DeviceButton(val number: Int) { UP(1), OK(2), DOWN(3), BACK(4) }

/**
 * HTTP client for the development API of a device (`/api/dev`, `/api/status`, `/api/log`).
 * The API is present in firmware built with APP_DEV_CONSOLE (the default).
 * All calls block; call them from a background thread.
 *
 * One connection per request (`Connection: close`): the device closes idle
 * connections, and a reused one fails with "header parser received no bytes".
 */
class DeviceClient(host: String) {
    private val base = "http://" + host.trim().removePrefix("http://").trimEnd('/')

    private class Response(val body: ByteArray, private val connection: HttpURLConnection) {
        fun header(name: String): String? = connection.getHeaderField(name)
    }

    private fun send(path: String, method: String = "GET", body: ByteArray? = null,
                     timeoutMs: Int = 10_000): Response {
        // 503: the host loop was busy, with the panel most likely: asked again a few times.
        for (attempt in 0 until BUSY_RETRIES) {
            try {
                return sendOnce(path, method, body, timeoutMs)
            } catch (busy: BusyException) {
                Thread.sleep(1_000)
            }
        }
        return sendOnce(path, method, body, timeoutMs)
    }

    private class BusyException : IOException("busy")

    private fun sendOnce(path: String, method: String, body: ByteArray?, timeoutMs: Int): Response {
        val connection = URI.create(base + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 3_000
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Connection", "close")
            if (method != "GET") {
                val bytes = body ?: ByteArray(0)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            if (code == 503) throw BusyException()
            if (code !in 200..299) {
                val said = connection.errorStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) }.orEmpty()
                throw IOException("HTTP $code from $path: ${said.take(200)}")
            }
            return Response(connection.inputStream.use { it.readBytes() }, connection)
        } finally {
            connection.disconnect()
        }
    }

    /** The JSON text of `/api/status`. */
    fun status(): String = String(send("/api/status").body, StandardCharsets.UTF_8)

    /** The frame currently shown on the panel. */
    fun frame(): DeviceFrame {
        val response = send("/api/dev/screen")
        val image = ImageIO.read(ByteArrayInputStream(response.body))
            ?: throw IOException("the device returned no image")
        val number = response.header("X-Frame")?.toIntOrNull() ?: 0
        return DeviceFrame(image, number, response.header("X-Pending") == "1")
    }

    fun press(button: DeviceButton, isLong: Boolean) {
        val press = if (isLong) "long" else "short"
        send("/api/dev/button?button=${button.number}&press=$press", "POST")
    }

    fun open(application: String) {
        send("/api/dev/open?app=" + URLEncoder.encode(application, StandardCharsets.UTF_8), "POST")
    }

    /** Log text written after position `since`; the whole buffer when `since` is null. */
    fun log(since: Long?): DeviceLog {
        val response = send("/api/log" + (if (since != null) "?since=$since" else ""))
        return DeviceLog(String(response.body, StandardCharsets.UTF_8), response.header("X-Log-End")?.toLongOrNull() ?: 0L)
    }

    /** A request of the simulator's own (`/api/sim/...`): POST, the answer ignored. */
    fun simulator(path: String) {
        send("/api/sim/$path", "POST")
    }

    /** Puts one file of an application's package on the device. */
    fun uploadAppFile(application: String, file: String, bytes: ByteArray) {
        send("/api/dev/app?name=${encode(application)}&file=${encode(file)}", "POST", bytes, UPLOAD_TIMEOUT_MS)
    }

    /** Restarts the device, the application to start into named. */
    fun restartInto(application: String) {
        send("/api/dev/run?app=${encode(application)}", "POST")
    }

    /** After a restart: the device is gone for a moment, then answers again. Returns its status. */
    fun waitUntilBack(isCancelled: () -> Boolean): String {
        Thread.sleep(3_000)
        val deadline = System.currentTimeMillis() + RESTART_WAIT_MS
        while (System.currentTimeMillis() < deadline && !isCancelled()) {
            try {
                return status()
            } catch (error: IOException) {
                Thread.sleep(1_000)
            }
        }
        throw IOException("the device did not come back in ${RESTART_WAIT_MS / 1000} seconds")
    }

    private fun encode(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8)

    /** Uploads a firmware image; the device installs it and restarts. */
    fun flash(image: ByteArray): String =
        String(send("/api/dev/flash", "POST", image, 180_000).body, StandardCharsets.UTF_8)

    companion object {
        private const val BUSY_RETRIES = 3
        private const val UPLOAD_TIMEOUT_MS = 120_000
        private const val RESTART_WAIT_MS = 60_000L
    }
}
