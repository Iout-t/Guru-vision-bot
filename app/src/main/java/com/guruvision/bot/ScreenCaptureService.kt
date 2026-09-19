package com.guruvision.bot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class ScreenCaptureService : Service() {
    private lateinit var projection: MediaProjection
    private lateinit var reader: ImageReader
    private var display: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val engine = SignalEngine()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            42,
            notification("GuruVision: starting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("projectionData", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra("projectionData")

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
                stopSelf()
            }
        }, handler)

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val dpi = dm.densityDpi

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection.createVirtualDisplay(
            "GuruVisionCapture",
            w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Process at most about 4 frames/sec to avoid hammering OCR.
                processImage(image.width, image.height, image.planes[0].buffer)
            } finally {
                image.close()
            }
        }, handler)

        return START_STICKY
    }

    private var lastProcess = 0L

    private fun processImage(width: Int, height: Int, buffer: java.nio.ByteBuffer) {
        val now = System.currentTimeMillis()
        if (now - lastProcess < 250) return
        lastProcess = now

        // Copy the current frame. The first prototype sends the full frame to OCR.
        // A later calibration screen can define exact EUR/USD price/chart crop rectangles.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text
                val price = PriceParser.findEurUsdPrice(text)
                if (price != null) {
                    val signal = engine.add(price, now)
                    updateNotification(signal.signal, signal.confidence, price)
                }
                bitmap.recycle()
            }
            .addOnFailureListener {
                bitmap.recycle()
            }
    }

    private fun updateNotification(signal: String, confidence: Double, price: Double) {
        val pct = String.format(Locale.US, "%.1f%%", confidence * 100.0)
        val text = "$signal • confidence $pct • price $price"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(42, notification(text))
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, "vision")
            .setContentTitle("GuruVision — EUR/USD OTC")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("vision", "GuruVision", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun stopCapture() {
        if (::reader.isInitialized) reader.close()
        display?.release()
        if (::projection.isInitialized) projection.stop()
        recognizer.close()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
