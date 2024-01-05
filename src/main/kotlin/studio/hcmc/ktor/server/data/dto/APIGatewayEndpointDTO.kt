package studio.hcmc.ktor.server.data.dto

import kotlinx.serialization.Serializable
import studio.hcmc.kotlin.protocol.io.RequestDataTransferObject
import studio.hcmc.ktor.server.APIGatewayEndpointCachePolicy

sealed interface APIGatewayEndpointDTO : RequestDataTransferObject {
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