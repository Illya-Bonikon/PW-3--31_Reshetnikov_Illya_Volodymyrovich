package ua.kpi.building_audit.error

/** Чисте відображення HTTP-коду та тіла помилки на AppError; не залежить від Retrofit. */
object HttpErrorClassifier {
    fun classify(
        code: Int,
        serverCode: String?,
        fields: Map<String, String>,
        retryAfterMs: Long?
    ): AppError = when (code) {
        400, 422 -> AppError.Validation(code, fields)
        401 -> AppError.Unauthorized(AuthFailure.fromCode(serverCode))
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        408 -> AppError.Timeout
        429 -> AppError.RateLimited(retryAfterMs)
        502, 503, 504 -> AppError.ServiceUnavailable(code, retryAfterMs)
        in 500..599 -> AppError.ServerFailure(code)
        else -> AppError.Unexpected("HTTP $code")
    }
}
