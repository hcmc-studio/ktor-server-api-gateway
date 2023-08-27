package studio.hcmc.ktor.server.routing

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Routing
import studio.hcmc.ktor.routing.respondEmpty
import studio.hcmc.ktor.routing.respondError
import studio.hcmc.ktor.routing.respondObject
import studio.hcmc.ktor.server.EndpointClient
import studio.hcmc.ktor.server.RouteDestinationImpl
import studio.hcmc.ktor.server.RouteNavigation
import studio.hcmc.ktor.server.data.dto.APIGatewayEndpointDTO
import studio.hcmc.ktor.server.data.vo.APIGatewayEndpointVO

internal fun Routing.endpointRouting(
    navigation: RouteNavigation,
    client: EndpointClient
) {
    @Resource("/api/gateway/endpoints")
    class Post

    @Resource("/api/gateway/endpoints")
    class List

    @Resource("/api/gateway/endpoints")
    data class Delete(val host: String, val port: Int)

    post<Post, APIGatewayEndpointDTO.Post> { _, dto ->
        val endpoint = APIGatewayEndpointVO(
            protocol = dto.protocol,
            host = dto.host,
            port = dto.port,
            method = dto.method,
            path = dto.path,
            cachePolicy = dto.cachePolicy
        )

        client.getOrPut(endpoint)
        navigation.add(endpoint, client)
        call.respondObject(HttpStatusCode.Created, endpoint)
    }

    get<List> {
        // TODO implementation
        call.respondError(HttpStatusCode.InternalServerError, NotImplementedError())
    }

    delete<Delete> { (host, port) ->
        val destination = RouteDestinationImpl(host, port)
        client.close(destination)
        navigation.close(destination)
        call.respondEmpty(HttpStatusCode.OK)
    }
}