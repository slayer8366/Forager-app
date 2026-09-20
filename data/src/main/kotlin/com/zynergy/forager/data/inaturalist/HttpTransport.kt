package com.zynergy.forager.data.inaturalist

/**
 * The HTTP boundary this project owns. No vendor client type crosses it.
 *
 * Kept deliberately thin: a status and a body. That is everything the catalog needs, and it means
 * every parsing and mapping rule below is tested against recorded responses with no network, no
 * sockets and no timing.
 */
interface HttpTransport {
    suspend fun get(url: String): HttpResponse
}

data class HttpResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}
