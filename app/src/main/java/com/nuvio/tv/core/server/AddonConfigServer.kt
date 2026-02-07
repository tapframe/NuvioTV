package com.nuvio.tv.core.server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AddonConfigServer(
    private val currentAddonsProvider: () -> List<AddonInfo>,
    private val onChangeProposed: (PendingAddonChange) -> Unit,
    private val manifestFetcher: (String) -> AddonInfo?,
    private val logoProvider: (() -> ByteArray?)? = null,
    port: Int = 8080
) : NanoHTTPD(port) {

    data class AddonInfo(
        val url: String,
        val name: String,
        val description: String?
    )

    data class PendingAddonChange(
        val id: String = UUID.randomUUID().toString(),
        val proposedUrls: List<String>,
        var status: ChangeStatus = ChangeStatus.PENDING
    )

    enum class ChangeStatus { PENDING, CONFIRMED, REJECTED }

    private val gson = Gson()
    private val pendingChanges = ConcurrentHashMap<String, PendingAddonChange>()

    fun confirmChange(id: String) {
        pendingChanges[id]?.status = ChangeStatus.CONFIRMED
    }

    fun rejectChange(id: String) {
        pendingChanges[id]?.status = ChangeStatus.REJECTED
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            method == Method.GET && uri == "/" -> serveWebPage()
            method == Method.GET && uri == "/logo.png" -> serveLogo()
            method == Method.GET && uri == "/api/addons" -> serveAddonList()
            method == Method.POST && uri == "/api/addons" -> handleAddonUpdate(session)
            method == Method.POST && uri == "/api/validate" -> handleValidateAddon(session)
            method == Method.GET && uri.startsWith("/api/status/") -> serveChangeStatus(uri)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebPage(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/html", AddonWebPage.getHtml())
    }

    private fun serveLogo(): Response {
        val bytes = logoProvider?.invoke()
        return if (bytes != null) {
            newFixedLengthResponse(
                Response.Status.OK,
                "image/png",
                ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveAddonList(): Response {
        val addons = currentAddonsProvider()
        val json = gson.toJson(addons)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleAddonUpdate(session: IHTTPSession): Response {
        // Auto-reject any stale pending changes so a new request can proceed
        pendingChanges.values
            .filter { it.status == ChangeStatus.PENDING }
            .forEach { it.status = ChangeStatus.REJECTED }

        // Parse request body
        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val body = bodyMap["postData"] ?: ""

        val urls: List<String> = try {
            val parsed = gson.fromJson<Map<String, Any>>(body, object : TypeToken<Map<String, Any>>() {}.type)
            @Suppress("UNCHECKED_CAST")
            (parsed["urls"] as? List<String>) ?: emptyList()
        } catch (e: Exception) {
            val error = mapOf("error" to "Invalid request body")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                gson.toJson(error)
            )
        }

        val change = PendingAddonChange(proposedUrls = urls)
        pendingChanges[change.id] = change
        onChangeProposed(change)

        val response = mapOf("status" to "pending_confirmation", "id" to change.id)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(response))
    }

    private fun handleValidateAddon(session: IHTTPSession): Response {
        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val body = bodyMap["postData"] ?: ""

        val url: String = try {
            val parsed = gson.fromJson<Map<String, Any>>(body, object : TypeToken<Map<String, Any>>() {}.type)
            (parsed["url"] as? String) ?: ""
        } catch (e: Exception) {
            ""
        }

        if (url.isBlank()) {
            val error = mapOf("error" to "Missing URL")
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", gson.toJson(error))
        }

        val addonInfo = manifestFetcher(url)
        return if (addonInfo != null) {
            newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(addonInfo))
        } else {
            val error = mapOf("error" to "Could not fetch addon manifest")
            newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", gson.toJson(error))
        }
    }

    private fun serveChangeStatus(uri: String): Response {
        val id = uri.removePrefix("/api/status/")
        val change = pendingChanges[id]
        val status = change?.status?.name?.lowercase() ?: "not_found"
        val response = mapOf("status" to status)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(response))
    }

    companion object {
        fun startOnAvailablePort(
            currentAddonsProvider: () -> List<AddonInfo>,
            onChangeProposed: (PendingAddonChange) -> Unit,
            manifestFetcher: (String) -> AddonInfo?,
            logoProvider: (() -> ByteArray?)? = null,
            startPort: Int = 8080,
            maxAttempts: Int = 10
        ): AddonConfigServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    val server = AddonConfigServer(currentAddonsProvider, onChangeProposed, manifestFetcher, logoProvider, port)
                    server.start(SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: Exception) {
                    // Port in use, try next
                }
            }
            return null
        }
    }
}
