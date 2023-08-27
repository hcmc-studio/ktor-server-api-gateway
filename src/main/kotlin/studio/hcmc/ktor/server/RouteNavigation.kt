package studio.hcmc.ktor.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import studio.hcmc.ktor.server.data.vo.APIGatewayEndpointVO
import java.util.concurrent.atomic.AtomicInteger

internal class RouteNavigation(
    private val root: Route
) {
    companion object {
        private val logger get() = APIGatewayConfig.config.loggers.routeNavigation
        private val dispatcherNamePrefix get() = APIGatewayConfig.config.coroutineDispatcherPrefixes.routeNavigation
        private var dispatcherId = AtomicInteger(0)
    }

    private val dispatcher = newSingleThreadContext("$dispatcherNamePrefix${dispatcherId.getAndIncrement()}")
    private val paths = HashMap<String /* RouteSelector */, Pair<RouteNodes, Route>>()

    /**
     * 새 연결 대상 추가. 단일 thread 실행.
     * @param endpoint 추가할 연결 대상
     * @param client 연결을 수행할 클라이언트
     */
    suspend fun add(endpoint: APIGatewayEndpointVO, client: EndpointClient) {
        withContext(dispatcher) {
            paths.computeIfAbsent("${endpoint.path}(method:${endpoint.method})") {
                logger.trace("Adding RouteNodes for endpoint {}", endpoint)
                val nodes = RouteNodes(endpoint)
                nodes to root.route(endpoint, nodes, client)
            }
        }
    }

    /**
     * 모든 연결, [dispatcher] 종료. 단일 thread 실행.
     */
    suspend fun close() {
        withContext(dispatcher) {
            for (entry in paths) {
                val (nodes, route) = entry.value
                nodes.close()
                route.remove()
            }
        }

        dispatcher.close()
    }

    /**
     * 연결 대상으로의 모든 연결 종료. 종료 후 다른 연결 대상이 없을 때 [dispatcher] 종료. 단일 thread 실행.
     */
    suspend fun close(destination: RouteDestination) {
        withContext(dispatcher) {
            val iterator = paths.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val (nodes, route) = entry.value
                if (nodes.close(destination)) {
                    // 연결 대상과 일치하는 node가 없음: route 삭제
                    logger.trace("Removing RouteNodes for destination {}", destination)
                    iterator.remove()
                    route.remove()
                }
            }
        }

        if (paths.isEmpty()) {
            dispatcher.close()
        }
    }

    private fun Route.remove() {
        val parent = parent
        if (parent != null) {
            logger.trace("Removing Route for selector {}", selector)
            (parent.children as MutableList).remove(this)
        } else {
            logger.error("Parent for Route {} is null.", selector)
        }
    }

    private fun Route.route(
        endpoint: APIGatewayEndpointVO,
        nodes: RouteNodes,
        client: EndpointClient
    ): Route {
        val method = HttpMethod(endpoint.method)
        return route(endpoint.path, method) {
            handle {
                nodes
                    .selectNode { client.getOrPut(endpoint) }
                    .request(HttpRequestBuilder().apply { fromApplicationCall(call) })
                    .let { call.respond(it) }
            }
        }
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

    private suspend fun ApplicationCall.respond(httpResponse: HttpResponse) {
        httpResponse.headers.forEach { name, values ->
            for (value in values) {
                // TODO don't know how values merged
                response.headers.append(name, value)
            }
        }
        respondText(httpResponse.bodyAsText(), httpResponse.contentType(), httpResponse.status)
    }
}