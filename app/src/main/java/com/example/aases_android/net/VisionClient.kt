package com.example.aases_android.net

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object VisionClient {
    private val ok = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: VisionService = Retrofit.Builder()
        .baseUrl("https://vision.googleapis.com/")
        .client(ok)
        .addConverterFactory(GsonConverterFactory.create())  // <-- Gson
        .build()
        .create(VisionService::class.java)
}