package ua.kpi.building_audit.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import ua.kpi.building_audit.network.SKIP_AUTH

interface EnergyAuditApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @GET("auth/me")
    suspend fun me(): UserDto

    @GET("auth/me")
    @Headers(SKIP_AUTH)
    suspend fun meWithoutToken(): UserDto

    @GET("auth/me")
    suspend fun meWithToken(@Header("Authorization") authorization: String): UserDto

    @GET("admin/users")
    suspend fun users(): List<UserDto>

    @GET("buildings")
    suspend fun buildings(): List<BuildingDto>

    @GET("buildings/{id}")
    suspend fun building(@Path("id") id: Long): BuildingDto

    @POST("buildings")
    suspend fun create(@Body body: BuildingDto): BuildingDto

    @PUT("buildings/{id}")
    suspend fun update(@Path("id") id: Long, @Body body: BuildingDto): BuildingDto

    @DELETE("buildings/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>

    @GET("tariffs")
    suspend fun tariffs(): TariffsDto

    @POST("debug/chaos")
    suspend fun configureChaos(@Body body: ChaosRequestDto): ChaosStateDto
}
