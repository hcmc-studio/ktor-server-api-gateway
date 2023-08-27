package studio.hcmc.ktor.server.data.dto

import kotlinx.serialization.Serializable
import studio.hcmc.kotlin.protocol.io.DataTransferObject
import studio.hcmc.kotlin.protocol.io.ErrorDataTransferObject

sealed class APIGatewayEndpointErrorDTO : ErrorDataTransferObject() {
    @Serializable
    data object EndpointNotFound : APIGatewayEndpointErrorDTO() {
        override val httpStatusCode = 404
        private fun readResolve(): Any = EndpointNotFound
    }
}