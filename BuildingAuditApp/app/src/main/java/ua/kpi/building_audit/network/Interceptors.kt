package ua.kpi.building_audit.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

const val SKIP_AUTH_HEADER = "X-Skip-Auth"
const val SKIP_AUTH = "$SKIP_AUTH_HEADER: true"

class NoConnectivityException : IOException("Немає підключення до мережі")

/** Зупиняє запит ще до спроби з'єднання, якщо система повідомила про відсутність мережі. */
class ConnectivityInterceptor(private val connectivity: ConnectivityStatus) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!connectivity.state.value.isOnline) throw NoConnectivityException()
        return chain.proceed(chain.request())
    }
}

/**
 * Додає заголовок Authorization до кожного запиту. Токен читається через лямбду,
 * тому інтерцептор не знає, де і як зберігається сеанс.
 * Запит із явним Authorization або з маркером [SKIP_AUTH_HEADER] не змінюється:
 * так екран ПЗ 11 перевіряє реакцію сервера на підроблений токен і запит без токена.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(SKIP_AUTH_HEADER) != null) {
            return chain.proceed(request.newBuilder().removeHeader(SKIP_AUTH_HEADER).build())
        }
        if (request.header("Authorization") != null) return chain.proceed(request)
        val token = tokenProvider() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
