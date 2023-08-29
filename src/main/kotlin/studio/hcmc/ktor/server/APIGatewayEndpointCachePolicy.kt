package studio.hcmc.ktor.server

import io.ktor.client.statement.*
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.Serializable
import studio.hcmc.kotlin.protocol.io.Response
import studio.hcmc.kotlin.serialization.JSerializer
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@OptIn(ExperimentalEncodingApi::class)
@Serializable
data class APIGatewayEndpointCachePolicy(
    /**
     * 캐시에 저장하는 데이터의 클래스 이름
     * @see KClass.simpleName
     */
    val valueClassName: String,
    /**
     * 캐시에 저장할 키로 사용되는 요청의 parameter
     * @see ApplicationCall.parameters
     */
    val parameterName: String,
    /**
     * 캐시에 저장할 시간(초)
     */
    val expireSeconds: Long,
    /**
     * 응답 유형
     */
    val responseType: Response.Type,
    /**
     * 응답으로부터 캐시의 키로 사용할 값을 선택. 일반적으로 ID(primitive). 런타임에 함수가 존재해야 함.
     */
    @Serializable(with = JSerializer::class)
    val idSelector: suspend (value: @Serializable(with = JSerializer::class) Any) -> String,
    /**
     * 응답으로부터 캐시의 데이터로 사용할 값을 선택. 일반적으로 VO. 런타임에 함수가 존재해야 함.
     */
    @Serializable(with = JSerializer::class)
    val resultSelector: suspend (httpResponse: @Serializable(with = JSerializer::class) HttpResponse) -> @Serializable(with = JSerializer::class) Any
)

internal fun APIGatewayEndpointCachePolicy.kClass(): KClass<Any> {
    return Reflection.createKotlinClass(Class.forName(valueClassName))
}