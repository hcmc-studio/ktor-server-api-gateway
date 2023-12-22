package studio.hcmc.ktor.server

import Engine
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import studio.hcmc.ktor.plugin.defaultJson
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

internal class EndpointClient {
    companion object {
        private val logger get() = APIGatewayConfig.config.loggers.endpointClient
        private val dispatcherNamePrefix get() = APIGatewayConfig.config.coroutineDispatcherPrefixes.endpointClient
        private var dispatcherId = AtomicInteger(0)
        private val RouteDestination.key: String get() = "$host:$port"
        private val RouteDestination.url: URL get() = URL(key)
        private val httpClientConfiguration: EndpointHttpClientConfiguration get() = APIGatewayConfig.config.endpointHttpClientConfiguration
        internal val defaultHttpClientConfiguration: EndpointHttpClientConfiguration = { destination ->
            HttpClient(CIO) {
                defaultRequest {
                    url {
                        host = destination.host
                        port = destination.port
                    }
                }

                install(ContentNegotiation) {
                    json(Engine.application.defaultJson)
                }
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("$dispatcherNamePrefix${dispatcherId.getAndIncrement()}")
    private val clients = HashMap<String /* (host:port) */, HttpClient>()

    /**
     * 연결 대상의 [HttpClient]를 반환, 존재하지 않으면 null. 단일 thread 실행.
     */
    suspend fun getOrNull(destination: RouteDestination): HttpClient? {
        return withContext(dispatcher) {
            clients[destination.key]
        }
    }

    /**
     * 연결 대상의 [HttpClient] 반환, 존재하지 않으면 새 연결 생성. 단일 thread 실행.
     */
    suspend fun getOrPut(destination: RouteDestination): HttpClient {
        return withContext(dispatcher) {
            val key = destination.key
            clients.computeIfAbsent(key) {
                logger.trace("Openning client for key {}", key)
                httpClientConfiguration(destination)
            }
        }
    }

    /**
     * 모든 연결 대상 [HttpClient] 종료. 단일 thread 실행.
     */
    suspend fun close() {
        withContext(dispatcher) {
            for ((key, client) in clients) {
                logger.trace("Closing client for key {}", key)
                client.close()
            }

            clients.clear()
        }

        dispatcher.close()
    }

    /**
     * 연결 대상의 [HttpClient] 종료. 단일 thread 실행.
     */
    suspend fun close(destination: RouteDestination) {
        val key = destination.key
        withContext(dispatcher) {
            val removal = clients.remove(key)
            if (removal == null) {
                logger.error("Closing error: client for key {} is not found.", key)
                return@withContext
            }

            logger.trace("Closing client for key {}", key)
            removal.close()
        }
    }
}