// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.device

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
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
 */
class DeviceClient(host: String) {
    private val base = "http://" + host.trim().removePrefix("http://").trimEnd('/')
    // HTTP/1.1 only: the device's server refuses the upgrade headers of HTTP/2.
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3)).build()

    private fun send(path: String, method: String = "GET", body: ByteArray? = null,
                     timeout: Duration = Duration.ofSeconds(10)): HttpResponse<ByteArray> {
        val publisher = if (body != null) HttpRequest.BodyPublishers.ofByteArray(body)
                        else HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout)
            .method(method, publisher).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            val said = String(response.body(), StandardCharsets.UTF_8).take(200)
            throw IOException("HTTP ${response.statusCode()} from $path: $said")
        }
        return response
    }

    /** The JSON text of `/api/status`. */
    fun status(): String = String(send("/api/status").body(), StandardCharsets.UTF_8)

    /** The frame currently shown on the panel. */
    fun frame(): DeviceFrame {
        val response = send("/api/dev/screen")
        val image = ImageIO.read(ByteArrayInputStream(response.body()))
            ?: throw IOException("the device returned no image")
        val number = response.headers().firstValue("X-Frame").map { it.toInt() }.orElse(0)
        val pending = response.headers().firstValue("X-Pending").orElse("0") == "1"
        return DeviceFrame(image, number, pending)
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
        val end = response.headers().firstValue("X-Log-End").map { it.toLong() }.orElse(0L)
        return DeviceLog(String(response.body(), StandardCharsets.UTF_8), end)
    }

    /** Uploads a firmware image; the device installs it and restarts. */
    fun flash(image: ByteArray): String =
        String(send("/api/dev/flash", "POST", image, Duration.ofMinutes(3)).body(), StandardCharsets.UTF_8)
}
