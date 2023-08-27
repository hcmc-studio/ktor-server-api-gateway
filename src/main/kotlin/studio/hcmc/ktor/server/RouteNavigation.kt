package studio.hcmc.ktor.server

import io.ktor.http.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
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
        return when (val method = HttpMethod(endpoint.method)) {
            HttpMethod.Get -> get(endpoint, nodes, client)
            HttpMethod.Post -> post(endpoint, nodes, client)
            HttpMethod.Put -> put(endpoint, nodes, client)
            HttpMethod.Patch -> patch(endpoint, nodes, client)
            HttpMethod.Delete -> delete(endpoint, nodes, client)
            HttpMethod.Head -> head(endpoint, nodes, client)
            HttpMethod.Options -> options(endpoint, nodes, client)
            else -> throw AssertionError("Unknown method: $method")
        }
    }
}