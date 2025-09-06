package com.example.aases_android.net

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface VisionService {
    @POST("v1/images:annotate")
    suspend fun annotate(
        @Body body: AnnotateImageRequest,
        @Query("key") apiKey: String
    ): AnnotateImageResponse
}
