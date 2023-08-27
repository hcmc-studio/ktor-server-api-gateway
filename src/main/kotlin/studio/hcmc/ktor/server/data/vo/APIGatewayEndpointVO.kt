package studio.hcmc.ktor.server.data.vo

import kotlinx.serialization.Serializable
import studio.hcmc.kotlin.protocol.io.ValueObject
import studio.hcmc.ktor.server.RouteDestination

@Serializable
data class APIGatewayEndpointVO(
    val protocol: String,
    override val host: String,
    override val port: Int,
    val method: String,
    val path: String
) : ValueObject, RouteDestination
