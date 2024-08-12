package com.example.wuwaweather

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.SearchView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.wuwaweather.R.drawable.sunnylong
import com.example.wuwaweather.databinding.ActivityMainBinding
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


//api 1c740173f7ce976296f9a5b82d75804d
class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
       val imageView = findViewById<ImageView>(R.id.imageView)

        fetchWeatherData("Dhaka")
        SearchCity()

        /*val source : ImageDecoder.Source = ImageDecoder.createSource(
            resources, R.drawable.sunnylong)
        val imageView : ImageView = findViewById<ImageView>(R.id.imageView)
        imageView.setImageDrawable(drawable)

        (drawable as? AnimatedImageDrawable)?.start()*/
        }

    private fun SearchCity() {
        val searchView = binding.searchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null) {
                    fetchWeatherData(query)
                }

                return true
        }
            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })
    }

    private fun fetchWeatherData(cityName: String) {
        val retrofit = Retrofit.Builder().addConverterFactory(GsonConverterFactory.create()).baseUrl("https://api.openweathermap.org/data/2.5/").build()
            .create(ApiInterface::class.java)

        val response = retrofit.getWeatherData(cityName, "1c740173f7ce976296f9a5b82d75804d", "metric")
        response.enqueue(object : Callback<WeatherApi>{
            override fun onResponse(call: retrofit2.Call<WeatherApi>, response: retrofit2.Response<WeatherApi>) {
                val responseBody = response.body()
                if (response.isSuccessful && responseBody != null){
                    val temperature = responseBody.main.temp.toString()
                    val humidity = responseBody.main.humidity
                    val windSpeed = responseBody.wind.speed
                    val sunRise = responseBody.sys.sunrise.toLong()
                    val sunSet = responseBody.sys.sunset.toLong()
                    val seaLevel = responseBody.main.pressure
                    val condition = responseBody.weather.firstOrNull()?.main?: "unknown"
                    val maxTemp = responseBody.main.temp_max
                    val minTemp = responseBody.main.temp_min

                    binding.temp.text = "$temperature °C"
                    binding.weather.text = condition
                    binding.maxtemp.text = "Max Temp: $maxTemp °C"
                    binding.mintemp.text = "Min Temp: $minTemp °C"
                    binding.humidity.text = "$humidity %"
                    binding.windspeed.text = "$windSpeed m/s"
                    binding.sunrise.text = "${time(sunRise)}"
                    binding.sunset.text = "${time(sunSet)}"
                    binding.sea.text = "$seaLevel hPa"
                    binding.condition.text = condition
                    binding.day.text = dayName(System.currentTimeMillis())
                    binding.date.text = date()
                    binding.city.text = "$cityName"
                    binding.temp.text = "$temperature °C"

                    changeImageAccordingToWeatherCondition(condition)
                }
            }

            private fun changeImageAccordingToWeatherCondition(conditions: String) {
                val backgroundImageView: ImageView = binding.root.findViewById(R.id.backgroundImageView)
                when(conditions){
                    "Clear Sky", "Sunny", "Clear" -> {
                      //  binding.root.setBackgroundResource(sunnylong)
                        Glide.with(this@MainActivity).asGif().load(R.drawable.sunnylong).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.sun)
                    }
                    "Partly Clouds", "Clouds", "Overcast", "Mist", "Foggy" -> {
                       // binding.root.setBackgroundResource(R.drawable.cloudy_medium)
                        Glide.with(this@MainActivity).asGif().load(R.drawable.cloudy_medium).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.cloud)
                    }
                    "Light Rain", "Rain", "Drizzle", "Moderate Rain", "Showers", "Heavy Rain" -> {
                       // binding.root.setBackgroundResource(R.drawable.rainy2)
                        Glide.with(this@MainActivity).asGif().load(R.drawable.rain_medium).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.rain)
                    }
                    "Thunderstorm" -> {
                        Glide.with(this@MainActivity).asGif().load(R.drawable.thunderstorm).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.thunder)
                    }
                    "Light Snow", "Moderate Snow", "Heavy Snow", "Blizzard" -> {
                       // binding.root.setBackgroundResource(R.drawable.snow_medium)
                        Glide.with(this@MainActivity).asGif().load(R.drawable.snow_medium).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.snow)
                    }
                    else -> {
                      //  binding.root.setBackgroundResource(sunnylong)
                        Glide.with(this@MainActivity).asGif().load(R.drawable.sunnylong).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.sun)
                    }
                }
                binding.lottieAnimationView.playAnimation()
            }

            override fun onFailure(call: retrofit2.Call<WeatherApi>, t: Throwable) {
                TODO("Not yet implemented")
            }
        })
        }

    fun date(): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun dayName(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        return sdf.format(Date())

    }

    fun time(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp * 1000))
    }
}

