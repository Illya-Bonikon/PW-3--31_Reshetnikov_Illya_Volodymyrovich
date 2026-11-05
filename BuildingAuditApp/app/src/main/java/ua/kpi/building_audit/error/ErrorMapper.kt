package ua.kpi.building_audit.error

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import retrofit2.HttpException
import ua.kpi.building_audit.auth.InvalidTokenException
import ua.kpi.building_audit.data.remote.InvalidPayloadException
import ua.kpi.building_audit.data.remote.dto.ErrorEnvelopeDto
import ua.kpi.building_audit.network.NoConnectivityException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Перетворює будь-який виняток мережевого стеку на AppError. */
class ErrorMapper(private val gson: Gson) {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is AppErrorException -> throwable.error
        is NoConnectivityException -> AppError.NoConnection
        is SocketTimeoutException -> AppError.Timeout
        is ConnectException, is UnknownHostException -> AppError.ServerUnreachable
        is HttpException -> fromHttp(throwable)
        // Обірваний JSON Gson повідомляє як IOException, тому ця гілка стоїть раніше за загальну IOException.
        is JsonParseException, is MalformedJsonException, is EOFException,
        is InvalidPayloadException, is InvalidTokenException ->
            AppError.InvalidResponse(throwable.message ?: throwable.javaClass.simpleName)
        is IOException -> AppError.NetworkFailure(throwable.message ?: throwable.javaClass.simpleName)
        else -> AppError.Unexpected(throwable.message ?: throwable.javaClass.simpleName)
    }

    private fun fromHttp(exception: HttpException): AppError {
        val response = exception.response()
        val body = runCatching { response?.errorBody()?.string() }.getOrNull()
        val error = body?.let { runCatching { gson.fromJson(it, ErrorEnvelopeDto::class.java)?.error }.getOrNull() }
        val retryAfterMs = response?.headers()?.get("Retry-After")?.toLongOrNull()?.times(1000)
        return HttpErrorClassifier.classify(exception.code(), error?.code, error?.fields.orEmpty(), retryAfterMs)
    }
}
