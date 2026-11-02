package com.example.wuwaweather

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeather endpoints. Pass either a city name (`q`) or coordinates (`lat` + `lon`);
 * Retrofit leaves out any query parameter that is null.
 */
interface ApiInterface {

    /** Current conditions. */
    @GET("data/2.5/weather")
    suspend fun getWeatherData(
        @Query("q") city: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<WeatherApi>

    /** 5-day forecast in 3-hour steps (free tier). */
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("q") city: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<ForecastResponse>

    /** City search suggestions (max 5). */
    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
        @Query("appid") apiKey: String
    ): Response<List<GeoResult>>

    /** Air quality (index 1–5 plus pollutant concentrations). */
    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): Response<AirPollutionResponse>
}

/**
 * Open-Meteo (free, no API key): UV index, 15-minute rain nowcast and hourly storm signals,
 * which OpenWeather's free tier lacks.
 */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getExtras(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "uv_index,precipitation",
        @Query("daily") daily: String = "uv_index_max",
        @Query("minutely_15") minutely15: String = "precipitation",
        @Query("hourly") hourly: String = "precipitation,precipitation_probability,weather_code,wind_gusts_10m",
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("forecast_days") days: Int = 2
    ): Response<OpenMeteoUvResponse>
}

/** Shared Retrofit instances. */
object WeatherClient {
    val api: ApiInterface by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiInterface::class.java)
    }

    val openMeteo: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApi::class.java)
    }
}
