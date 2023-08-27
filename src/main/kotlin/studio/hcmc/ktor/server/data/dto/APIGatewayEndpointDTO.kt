package studio.hcmc.ktor.server.data.dto

import kotlinx.serialization.Serializable
import studio.hcmc.kotlin.protocol.io.DataTransferObject
import studio.hcmc.ktor.server.APIGatewayEndpointCachePolicy

sealed interface APIGatewayEndpointDTO : DataTransferObject {
    @Serializable
    data class Post(
        val protocol: String,
        val host: String,
        val port: Int,
        val method: String,
        val path: String,
        val cachePolicy: APIGatewayEndpointCachePolicy? = null
    ) : APIGatewayEndpointDTO
}