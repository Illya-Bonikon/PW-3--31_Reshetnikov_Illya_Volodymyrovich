package ua.kpi.building_audit.network

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ua.kpi.building_audit.data.remote.EnergyAuditApi
import java.util.concurrent.TimeUnit

object RetrofitFactory {
    fun create(
        baseUrl: String,
        gson: Gson,
        connectivity: ConnectivityStatus,
        tokenProvider: () -> String?,
        logBodies: Boolean
    ): EnergyAuditApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            // Повтори виконує ErrorCenter із паузами, тож вбудований повтор OkHttp вимкнено.
            .retryOnConnectionFailure(false)
            .addInterceptor(ConnectivityInterceptor(connectivity))
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (logBodies) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EnergyAuditApi::class.java)
    }
}
