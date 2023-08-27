package studio.hcmc.ktor.server

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import studio.hcmc.ktor.routing.respondError
import studio.hcmc.ktor.server.data.dto.APIGatewayEndpointErrorDTO
import studio.hcmc.ktor.server.routing.endpointRouting

internal typealias EndpointHttpClientConfiguration = (destination: RouteDestination) -> HttpClient

private lateinit var _config: APIGatewayConfig

internal val APIGatewayConfig.Companion.config: APIGatewayConfig get() = _config

internal class APIGatewayConfig(
    val host: String,
    val port: Int,
    val loggers: APIGatewayConfigBuilder.Loggers,
    val headerNames: APIGatewayConfigBuilder.HeaderNames,
    val coroutineDispatcherPrefixes: APIGatewayConfigBuilder.CoroutineDispatcherPrefixes,
    val routeNodeSelector: suspend (nodes: List<RouteNode>) -> Int,
    val endpointHttpClientConfiguration: EndpointHttpClientConfiguration
) {
    companion object;
}

class APIGatewayConfigBuilder {
    /**
     * Endpoint 요청을 수행할 host.
     */
    var host: String = "localhost"

    /**
     * Endpoint 요청을 수행할 port.
     */
    var port: Int = 9647

    /**
     * Endpoint 요청 관련 [Logger]
     */
    var loggers: Loggers = Loggers()

    /**
     * API gateway와 endpoint 사이에서 사용될 HTTP header 이름
     */
    var headerNames: HeaderNames = HeaderNames()

    /**
     * 단일 thread에서 수행되는 component의 thread 이름
     */
    var coroutineDispatcherPrefixes: CoroutineDispatcherPrefixes = CoroutineDispatcherPrefixes()

    /**
     * 연결 대상 선정.
     */
    var routeNodeSelector: suspend (nodes: List<RouteNode>) -> Int = { it.indices.random() }

    /**
     * 연결 대상 HttpClient 생성
     */
    var endpointHttpClientConfiguration: EndpointHttpClientConfiguration = EndpointClient.defaultHttpClientConfiguration

    class Loggers {
        var endpointClient: Logger = LoggerFactory.getLogger("EndpointClient")
        var routeNavigation: Logger = LoggerFactory.getLogger("RouteNavigation")
        var routeNodes: Logger = LoggerFactory.getLogger("RouteNodes")
        var routeNode: Logger = LoggerFactory.getLogger("RouteNode")
    }

    class HeaderNames {
        var remoteAddress: String = "x-hcmc-remote-address"
        var remotePort: String = "x-hcmc-remote-port"
        var remoteHost: String = "x-hcmc-remote-host"
    }

    class CoroutineDispatcherPrefixes {
        var routeNodes: String = "RouteNodes-"
        var routeNavigation: String = "RouteNavigation-"
        var endpointClient: String = "EndpointClient-"
    }
}

val APIGateway = createApplicationPlugin("APIGateway", ::APIGatewayConfigBuilder) {
    _config = APIGatewayConfig(
        pluginConfig.host,
        pluginConfig.port,
        pluginConfig.loggers,
        pluginConfig.headerNames,
        pluginConfig.coroutineDispatcherPrefixes,
        pluginConfig.routeNodeSelector,
        pluginConfig.endpointHttpClientConfiguration
    )

    val client = EndpointClient()
    var navigation: RouteNavigation? = null
    val routing = application.routing {
        navigation?.let { endpointRouting(it, client) } ?: RouteNavigation(this).apply {
            navigation = this
            endpointRouting(this, client)
        }
    }

    routing.intercept(ApplicationCallPipeline.Setup) {
        val serverHost = call.request.origin.serverHost
        val serverPort = call.request.origin.serverPort
        if (serverHost != APIGatewayConfig.config.host || serverPort != APIGatewayConfig.config.port) {
            call.respondError(HttpStatusCode.NotFound, APIGatewayEndpointErrorDTO.EndpointNotFound)
        }
    }
}