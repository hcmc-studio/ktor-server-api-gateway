package studio.hcmc.ktor.server

interface RouteDestination {
    val host: String
    val port: Int
}

internal data class RouteDestinationImpl(
    override val host: String,
    override val port: Int
) : RouteDestination {
    private val url by lazy { "$host:$port" }

    override fun toString(): String {
        return url
    }
}