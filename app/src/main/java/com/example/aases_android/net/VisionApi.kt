package com.example.aases_android.net

import com.google.gson.annotations.SerializedName

data class AnnotateImageRequest(
    val requests: List<RequestItem>
)

data class RequestItem(
    val image: ImagePayload,
    val features: List<Feature>,
    val imageContext: ImageContext? = null
)

data class ImagePayload(val content: String)

data class Feature(val type: String, val maxResults: Int = 1)

data class ImageContext(val languageHints: List<String>? = null)

data class AnnotateImageResponse(
    @SerializedName("responses") val responses: List<SingleResponse>
)

data class SingleResponse(
    @SerializedName("fullTextAnnotation") val fullTextAnnotation: FullTextAnnotation?
)

data class FullTextAnnotation(val text: String?)
