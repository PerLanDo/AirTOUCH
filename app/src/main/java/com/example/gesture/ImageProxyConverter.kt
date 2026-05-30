package com.example.gesture

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Converts CameraX [ImageProxy] frames to upright, front-camera-mirrored bitmaps
 * suitable for MediaPipe inference.
 */
object ImageProxyConverter {

    fun toBitmap(imageProxy: ImageProxy, mirrorForFrontCamera: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (mirrorForFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }
}
