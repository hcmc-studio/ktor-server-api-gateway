package studio.hcmc.ktor.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializerOrNull
import studio.hcmc.kotlin.protocol.io.Response
import studio.hcmc.ktor.plugin.defaultJson
import studio.hcmc.ktor.routing.respondArray
import studio.hcmc.ktor.routing.respondError
import studio.hcmc.ktor.routing.respondObject
import studio.hcmc.ktor.server.data.dto.APIGatewayEndpointErrorDTO
import studio.hcmc.ktor.server.data.vo.APIGatewayEndpointVO
import kotlin.reflect.KSuspendFunction3

internal fun Route.get(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return get(endpoint.path) {
        if (!preFetch(endpoint)) {
            val httpResponse = fetch(endpoint, nodes, client)
            if (httpResponse.status.isSuccess()) {
                updateCache(endpoint, httpResponse)
            }
        }
    }
}

internal fun Route.post(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return post(endpoint.path) {
        val httpResponse = fetch(endpoint, nodes, client)
        if (httpResponse.status.isSuccess()) {
            updateCache(endpoint, httpResponse)
        }
    }
}

internal fun Route.put(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return put(endpoint.path) {
        val httpResponse = fetch(endpoint, nodes, client)
        if (httpResponse.status.isSuccess()) {
            updateCache(endpoint, httpResponse)
        }
    }
}

internal fun Route.patch(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return patch(endpoint.path) {
        val httpResponse = fetch(endpoint, nodes, client)
        if (httpResponse.status.isSuccess()) {
            updateCache(endpoint, httpResponse)
        }
    }
}

internal fun Route.delete(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return delete(endpoint.path) {
        val httpResponse = fetch(endpoint, nodes, client)
        if (httpResponse.status.isSuccess()) {
            deleteCache(endpoint)
        }
    }
}

internal fun Route.head(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return head(endpoint.path) {
        fetch(endpoint, nodes, client)
    }
}

internal fun Route.options(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): Route {
    return options(endpoint.path) {
        fetch(endpoint, nodes, client)
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.fetch(
    endpoint: APIGatewayEndpointVO,
    nodes: RouteNodes,
    client: EndpointClient
): HttpResponse {
    return nodes
        .selectNode { client.getOrPut(endpoint) }
        .request(HttpRequestBuilder().apply { fromApplicationCall(call) })
        .let { call.respond(it) }
}

private suspend fun ApplicationCall.respond(httpResponse: HttpResponse): HttpResponse {
    httpResponse.headers.forEach { name, values ->
        for (value in values) {
            // TODO don't know how values merged
            response.headers.append(name, value)
        }
    }

    val body = httpResponse.bodyAsText()
    respondText(body, httpResponse.contentType(), httpResponse.status)

    return httpResponse
}

private suspend fun PipelineContext<Unit, ApplicationCall>.preFetch(endpoint: APIGatewayEndpointVO): Boolean {
    val cachePolicy = endpoint.cachePolicy
    if (cachePolicy != null) {
        val value = getFromCache(cachePolicy) ?: return false
        when (cachePolicy.responseType) {
            Response.Type.OBJECT -> call.respondObject(HttpStatusCode.OK, value.jsonObject)
            Response.Type.ARRAY -> call.respondArray(HttpStatusCode.OK, value.jsonArray)
            else -> call.respondError(HttpStatusCode.BadRequest, APIGatewayEndpointErrorDTO.InvalidCacheResponseType)
        }

        return true
    }

    return false
}

@OptIn(ExperimentalLettuceCoroutinesApi::class, InternalSerializationApi::class)
private suspend fun PipelineContext<Unit, ApplicationCall>.getFromCache(
    cachePolicy: APIGatewayEndpointCachePolicy
): JsonElement? {
    val connectionSupplier = APIGatewayConfig.config.cacheConnection ?: return null
    val serializer = cachePolicy.kClass().serializerOrNull() ?: return null
    val parameter = call.parameters[cachePolicy.parameterName] ?: return null
    val key = "${cachePolicy.keyPrefix()}$parameter"
    val value = connectionSupplier().use { it.coroutines().get(key) } ?: return null

    return application.defaultJson.encodeToJsonElement(serializer, value)
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
private suspend fun updateCache(
    endpoint: APIGatewayEndpointVO,
    httpResponse: HttpResponse
) {
    val cachePolicy = endpoint.cachePolicy ?: return
    val connectionSupplier = APIGatewayConfig.config.cacheConnection ?: return
    val value = cachePolicy.resultSelector(httpResponse)
    val key = "${cachePolicy.keyPrefix()}${cachePolicy.idSelector(value)}"
    connectionSupplier().use {
        val setex = it.coroutines()::setex as KSuspendFunction3<String, Long, Any, String?>
        setex(key, cachePolicy.expireSeconds, value)
    }
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
private suspend fun PipelineContext<Unit, ApplicationCall>.deleteCache(
    endpoint: APIGatewayEndpointVO
) {
    val cachePolicy = endpoint.cachePolicy ?: return
    val connectionSupplier = APIGatewayConfig.config.cacheConnection ?: return
    val parameter = call.parameters[cachePolicy.parameterName] ?: return
    val key = "${cachePolicy.keyPrefix()}$parameter"
    connectionSupplier().use { it.coroutines().del(key) }
}

private suspend fun HttpRequestBuilder.fromApplicationCall(call: ApplicationCall) {
    method = call.request.httpMethod
    header(APIGatewayConfig.config.headerNames.remoteAddress, call.request.origin.remoteAddress)
    header(APIGatewayConfig.config.headerNames.remotePort, call.request.origin.remotePort.toString())
    header(APIGatewayConfig.config.headerNames.remoteHost, call.request.origin.remoteHost)
    headers.appendMissing(call.request.headers)
    parameters { appendAll(call.request.queryParameters) }
    setBody(call.receive(), call.receiveType)
}