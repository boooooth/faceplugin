package com.faceplugin.facerecognitionsdk.kit

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CameraFrameUtils {
    private const val ENGINE_MAX_SIDE = 1280
    private const val PREVIEW_MAX_SIDE = 640

    /** Live Identify/Capture frames: ImageProxy -> Bitmap -> rotate -> scale. */
    @JvmStatic
    @SuppressLint("UnsafeOptInUsageError")
    fun fromImageProxy(imageProxy: ImageProxy, backCamera: Boolean): Bitmap? {
        val rawBitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            null
        } ?: return null

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = android.graphics.Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        if (!backCamera) {
            matrix.postScale(-1f, 1f)
        }

        val rotatedBitmap = if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(
                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
            )
            if (rotated !== rawBitmap && !rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
            rotated
        } else {
            rawBitmap
        }

        val frame = prepareForEngine(rotatedBitmap, PREVIEW_MAX_SIDE)
        if (frame !== rotatedBitmap && !rotatedBitmap.isRecycled) {
            rotatedBitmap.recycle()
        }
        return frame
    }

    /** Downscale for live preview / VideoWorker frames (long side ≤ 640). */
    fun prepareForEngine(src: Bitmap, maxSide: Int = PREVIEW_MAX_SIDE): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val longSide = max(w, h)
        if (longSide <= maxSide) return src
        val scale = maxSide.toFloat() / longSide
        val tw = max(1, (w * scale).roundToInt())
        val th = max(1, (h * scale).roundToInt())
        return Bitmap.createScaledBitmap(src, tw, th, true)
    }

    /** Same downscale as FaceRecognitionSDK bitmapToRgb (long side ≤ 1280) for gallery crop coords. */
    fun enginePreparedImage(src: Bitmap, maxSide: Int = ENGINE_MAX_SIDE): Bitmap =
        prepareForEngine(src, maxSide)

    fun cropFace(
        fromEngineImage: Bitmap,
        region: RectF,
        paddingFraction: Float = 0.12f,
    ): Bitmap? {
        val iw = fromEngineImage.width
        val ih = fromEngineImage.height
        if (iw <= 0 || ih <= 0 || region.width() <= 1f || region.height() <= 1f) return null
        val side = max(region.width(), region.height()) * (1f + paddingFraction * 2f)
        var left = region.centerX() - side * 0.5f
        var top = region.centerY() - side * 0.5f
        var size = side
        if (size > iw || size > ih) {
            size = min(iw.toFloat(), ih.toFloat())
            left = region.centerX() - size * 0.5f
            top = region.centerY() - size * 0.5f
        }
        left = left.coerceIn(0f, max(0f, iw - size))
        top = top.coerceIn(0f, max(0f, ih - size))
        val rect = Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + size).roundToInt().coerceAtMost(iw),
            (top + size).roundToInt().coerceAtMost(ih),
        )
        if (rect.width() <= 1 || rect.height() <= 1) return null
        return try {
            Bitmap.createBitmap(fromEngineImage, rect.left, rect.top, rect.width(), rect.height())
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun copyArgb(src: Bitmap): Bitmap {
        if (src.config == Bitmap.Config.ARGB_8888 && !src.isRecycled) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, null)
        return out
    }
}
