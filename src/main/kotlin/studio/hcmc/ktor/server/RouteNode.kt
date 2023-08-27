package studio.hcmc.ktor.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import studio.hcmc.ktor.server.data.vo.APIGatewayEndpointVO
import java.io.IOException

interface RouteNode {
    val works: Int
    val destination: RouteDestination

    suspend fun close()
    suspend fun closeAndWait()

    class BlockedNodeException(destination: RouteDestination) : IOException("Access to blocked endpoint: $destination")
}

/**
 * 연결 대상의 요청 관리
 */
internal class RouteNodeImpl(private val endpoint: APIGatewayEndpointVO) : RouteNode {
    companion object {
        private val logger get() = APIGatewayConfig.config.loggers.routeNode
    }
    private val lock = Mutex()
    private var blocked = false
    override var works = 0; private set
    override val destination: RouteDestination get() = endpoint

    /**
     * [endpoint]로 요청 후 결과 반환
     */
    suspend fun <R> execute(action: suspend (endpoint: APIGatewayEndpointVO) -> R): R {
        if (blocked) {
            throw RouteNode.BlockedNodeException(destination)
        }

        try {
            lock.withLock { works++ }

            return action(endpoint)
        } finally {
            lock.withLock { works-- }
        }
    }

    /**
     * 추가 연결 중단
     */
    override suspend fun close() {
        logger.trace("Closing RouteNode for destination {}", destination)
        lock.withLock { blocked = true }
    }

    /**
     * 추가 연결 중단, 대기
     */
    override suspend fun closeAndWait() {
        close()

        while (lock.withLock { works } > 0) {
            yield()
        }
    }
}