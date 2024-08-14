package com.example.wuwaweather


import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColor
import com.bumptech.glide.Glide
import com.example.wuwaweather.databinding.ActivityMainBinding
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
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

        fetchWeatherData("Dhaka")
        SearchCity()


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
                    val calendar = Calendar.getInstance()
                    val currentTime = calendar.time

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

                val cityname = findViewById<TextView>(R.id.city)
                val today = findViewById<TextView>(R.id.today)
                val temp = findViewById<TextView>(R.id.temp)
                val weather = findViewById<TextView>(R.id.weather)
                val maxtemp = findViewById<TextView>(R.id.maxtemp)
                val mintemp = findViewById<TextView>(R.id.mintemp)
                val day = findViewById<TextView>(R.id.day)
                val date = findViewById<TextView>(R.id.date)

                val backgroundImageView: ImageView = binding.root.findViewById(R.id.backgroundImageView)
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isDay = currentHour in 6..18 // 6 AM to 6 PM is considered day
                val isNight = currentHour > 18 || currentHour < 6 // 6 PM to 6 AM is considered night

                // Change text color based on day or night
                if (isNight) {
                    cityname.setTextColor(Color.parseColor("#FFFFFF"))
                    today.setTextColor(Color.parseColor("#FFFFFF"))
                    temp.setTextColor(Color.parseColor("#FFFFFF"))
                    weather.setTextColor(Color.parseColor("#FFFFFF"))
                    maxtemp.setTextColor(Color.parseColor("#FFFFFF"))
                    mintemp.setTextColor(Color.parseColor("#FFFFFF"))
                    day.setTextColor(Color.parseColor("#FFFFFF"))
                    date.setTextColor(Color.parseColor("#FFFFFF"))
                } else{
                    cityname.setTextColor(Color.parseColor("#000000"))
                    today.setTextColor(Color.parseColor("#000000"))
                    temp.setTextColor(Color.parseColor("#000000"))
                    weather.setTextColor(Color.parseColor("#000000"))
                    maxtemp.setTextColor(Color.parseColor("#000000"))
                    mintemp.setTextColor(Color.parseColor("#000000"))
                    day.setTextColor(Color.parseColor("#000000"))
                    date.setTextColor(Color.parseColor("#000000"))
                }

                when(conditions){
                    "Clear Sky", "Sunny", "Clear" -> {
                        if(isNight){
                            Glide.with(this@MainActivity).asGif().load(R.drawable.night_sky).into(backgroundImageView)
                            binding.lottieAnimationView.setAnimation(R.raw.moon)
                        } else if(isDay) {
                            Glide.with(this@MainActivity).asGif().load(R.drawable.sunnylong)
                                .into(backgroundImageView)
                            binding.lottieAnimationView.setAnimation(R.raw.sun)
                        }
                    }
                    "Partly Clouds", "Clouds", "Overcast", "Mist", "Foggy" -> {
                       if(isDay) {
                           Glide.with(this@MainActivity).asGif().load(R.drawable.cloudy_medium)
                               .into(backgroundImageView)
                           binding.lottieAnimationView.setAnimation(R.raw.cloud)
                       }else if(isNight) {
                           Glide.with(this@MainActivity).asGif().load(R.drawable.night_cloud)
                               .into(backgroundImageView)
                           binding.lottieAnimationView.setAnimation(R.raw.cloud)
                       }
                    }
                    "Light Rain", "Drizzle" -> {
                        if(isDay) {
                            Glide.with(this@MainActivity).asGif().load(R.drawable.rain_medium)
                                .into(backgroundImageView)
                            binding.lottieAnimationView.setAnimation(R.raw.rain)
                        }else if(isNight) {
                            Glide.with(this@MainActivity).asGif().load(R.drawable.rainy2)
                                .into(backgroundImageView)
                            binding.lottieAnimationView.setAnimation(R.raw.rain)
                        }
                    }

                    "Rain","Moderate Rain", "Showers", "Heavy Rain" -> {
                       if(isNight) {
                           Glide.with(this@MainActivity).asGif().load(R.drawable.rain_heavy)
                               .into(backgroundImageView)
                           binding.lottieAnimationView.setAnimation(R.raw.rain)
                       }else if(isDay) {
                           Glide.with(this@MainActivity).asGif().load(R.drawable.rain_morning)
                               .into(backgroundImageView)
                           binding.lottieAnimationView.setAnimation(R.raw.rain)
                       }
                    }

                    "Thunderstorm" -> {
                        Glide.with(this@MainActivity).asGif().load(R.drawable.thunderstorm).into(backgroundImageView)
                        binding.lottieAnimationView.setAnimation(R.raw.thunder)
                    }
                    "Light Snow", "Moderate Snow", "Heavy Snow", "Blizzard" -> {
                       if(isDay) {
                           Glide.with(this@MainActivity).asGif().load(R.drawable.snow_medium)
                               .into(backgroundImageView)
                           binding.lottieAnimationView.setAnimation(R.raw.snow)
                       }else if(isNight){
                           Glide.with(this@MainActivity).asGif().load(R.drawable.snow_night)
                               .into(backgroundImageView)
                           binding.lottieAnimationView.setAnimation(R.raw.snow)
                       }
                    }

                    else -> {
                        if (isNight) {
                            Glide.with(this@MainActivity).asGif().load(R.drawable.night_sky).into(backgroundImageView)
                            binding.lottieAnimationView.setAnimation(R.raw.moon)
                        } else if (isDay) {
                            Glide.with(this@MainActivity).asGif().load(R.drawable.sunnylong).into(backgroundImageView)
                            binding.lottieAnimationView.setAnimation(R.raw.sun)
                        }
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

