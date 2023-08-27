package studio.hcmc.ktor.server

import kotlinx.coroutines.*
import studio.hcmc.kotlin.coroutines.launch
import studio.hcmc.ktor.server.data.vo.APIGatewayEndpointVO
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * 한 요청 경로에 대한 여러 연결 대상을 관리
 */
internal class RouteNodes(endpoint: APIGatewayEndpointVO) {
    companion object {
        private val logger get() = APIGatewayConfig.config.loggers.routeNodes
        private val dispatcherPrefix get() = APIGatewayConfig.config.coroutineDispatcherPrefixes.routeNodes
        private val dispatcherId = AtomicInteger(0)
        private val nodeSelector get() = APIGatewayConfig.config.routeNodeSelector
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("$dispatcherPrefix${dispatcherId.getAndIncrement()}")
    private val nodes = ArrayList<RouteNodeImpl>()

    init {
        nodes.add(RouteNodeImpl(endpoint))
    }

    /**
     * 새 연결 대상 추가. 단일 thread 실행.
     */
    suspend fun add(endpoint: APIGatewayEndpointVO) {
        withContext(dispatcher) {
            logger.trace("Adding RouteNode for endpoint {}", endpoint)
            nodes.add(RouteNodeImpl(endpoint))
        }
    }

    /**
     * 연결 대상 삭제. 단일 thread 실행.
     * @param destination 삭제할 연결 대상
     * @return 삭제 후 다른 연결 대상이 없으면 true
     */
    suspend fun close(destination: RouteDestination): Boolean {
        return withContext(dispatcher) {
            val iterator = nodes.listIterator()
            while (iterator.hasNext()) {
                val node = iterator.next()
                if (node.destination.host == destination.host && node.destination.port == destination.port) {
                    logger.trace("Removing RouteNode for destination {}", destination)
                    iterator.remove()
                    node.close()
                }
            }

            nodes.isEmpty()
        }
    }

    /**
     * 모든 연결 대상 종료, 대기. 단일 thread 실행.
     */
    suspend fun close() {
        withContext(dispatcher) {
            val iterator = nodes.listIterator()
            while (iterator.hasNext()) {
                val node = iterator.next()
                node.close()
                iterator.remove()
            }
        }

        dispatcher.close()
    }

    /**
     * 모든 연결 대상 반환. 단일 thread 실행.
     */
    suspend fun destinations(): List<RouteDestination> {
        return withContext(dispatcher) {
            nodes.map { it.destination }
        }
    }

    /**
     * 연결 대상 선정. 현재 thread 실행.
     * @param action 선정된 연결 대상에서 수행할 작업
     */
    suspend fun <R> selectNode(action: suspend (endpoint: APIGatewayEndpointVO) -> R): R {
        while (true) {
            try {
                val index = nodeSelector(nodes)
                val node = nodes[index]
                logger.trace("Selected node {}", node)

                return node.execute(action)
            } catch (_: RouteNode.BlockedNodeException) {
                continue
            }
        }
    }
}