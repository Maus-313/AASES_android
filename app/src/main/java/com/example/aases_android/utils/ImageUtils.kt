package com.example.aases_android.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun imageProxyToBase64Jpeg(imageProxy: ImageProxy, jpegQuality: Int = 90): String {
    val jpegBytes = when (imageProxy.format) {
        ImageFormat.JPEG -> {
            // ImageCapture.OnImageCapturedCallback usually gives JPEG (1 plane)
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            // Optionally rotate to upright, because JPEG bytes may be “sideways”
            rotateJpegIfNeeded(bytes, imageProxy.imageInfo.rotationDegrees, jpegQuality)
        }
        ImageFormat.YUV_420_888 -> {
            // 3 planes → convert to NV21 → compress to JPEG
            yuv420ToJpeg(imageProxy, jpegQuality)
        }
        else -> {
            // Fallback: try to render to a Bitmap and compress
            val bmp = imageProxyToBitmapFallback(imageProxy)
            bitmapToJpeg(bmp, jpegQuality)
        }
    }
    // IMPORTANT: close only after reading planes
    imageProxy.close()
    return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
}

private fun yuv420ToJpeg(imageProxy: ImageProxy, jpegQuality: Int): ByteArray {
    val y = imageProxy.planes[0].buffer
    val u = imageProxy.planes[1].buffer
    val v = imageProxy.planes[2].buffer
    val ySize = y.remaining()
    val uSize = u.remaining()
    val vSize = v.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    y.get(nv21, 0, ySize)
    // NV21 expects V then U
    v.get(nv21, ySize, vSize)
    u.get(nv21, ySize + vSize, uSize)

    val yuv = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), jpegQuality, out)
    val jpeg = out.toByteArray()
    return rotateJpegIfNeeded(jpeg, imageProxy.imageInfo.rotationDegrees, jpegQuality)
}

private fun rotateJpegIfNeeded(inputJpeg: ByteArray, rotationDegrees: Int, jpegQuality: Int): ByteArray {
    if (rotationDegrees % 360 == 0) return inputJpeg
    val bmp = BitmapFactory.decodeByteArray(inputJpeg, 0, inputJpeg.size) ?: return inputJpeg
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    return bitmapToJpeg(rotated, jpegQuality)
}

private fun bitmapToJpeg(bmp: Bitmap, jpegQuality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
    return out.toByteArray()
}

private fun imageProxyToBitmapFallback(imageProxy: ImageProxy): Bitmap {
    // Very basic fallback: convert using YUV route even if format unexpected.
    // (We could add a proper YUV->RGB converter here if needed.)
    return BitmapFactory.decodeByteArray(
        yuv420ToJpeg(imageProxy, 90), 0,
        yuv420ToJpeg(imageProxy, 90).size
    )
}