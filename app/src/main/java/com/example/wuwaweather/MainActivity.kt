package com.example.wuwaweather

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


//api 1c740173f7ce976296f9a5b82d75804d
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val source : ImageDecoder.Source = ImageDecoder.createSource(
            resources, R.drawable.sunnylong)

        val drawable : Drawable = ImageDecoder.decodeDrawable(source)

        val imageView : ImageView = findViewById<ImageView>(R.id.imageView)
        imageView.setImageDrawable(drawable)

        (drawable as? AnimatedImageDrawable)?.start()
        }
    }
