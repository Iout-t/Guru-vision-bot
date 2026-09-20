package com.guruvision.bot

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.guruvision.bot.overlay.SignalOverlay
import com.guruvision.bot.vision.CandleDetector
import com.guruvision.bot.vision.PatternClassifier
import java.util.Locale

class ScreenCaptureService : Service() {
    private lateinit var projection: MediaProjection
    private lateinit var reader: ImageReader
    private var display: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val engine = SignalEngine()
    private val overlay by lazy { SignalOverlay(this) }
    private val candleDetector = CandleDetector()
    private val patternClassifier = PatternClassifier()

    private var lastProcess = 0L
    private var lastUiUpdate = 0L
    private var frameCount = 0
    private var processing = false
    private var assetVerified = false
    private var lastPrice: Double? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            42,
            notification("CAPTURE: starting…"),
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

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        display = projection.createVirtualDisplay(
            "GuruVisionCapture",
            w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )

        publish("CAPTURE: OK • waiting for EUR/USD OCR")

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastProcess < 350L || processing) return@setOnImageAvailableListener
                lastProcess = now
                frameCount++
                processing = true
                processImage(image, now)
            } finally {
                image.close()
            }
        }, handler)

        return START_STICKY
    }

    private fun processImage(image: Image, now: Long) {
        val bitmap = imageToBitmap(image)
        if (bitmap == null) {
            processing = false
            publish("CAPTURE: OK • frame conversion failed")
            return
        }

        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val parsed = PriceParser.parse(result.text)
                assetVerified = parsed.assetDetected
                lastPrice = parsed.price

                if (!parsed.assetDetected) {
                    publish("CAPTURE: OK • OCR: ${if (result.text.isBlank()) "EMPTY" else "no EUR/USD"}")
                    overlay.show("WAIT • select EUR/USD OTC", 0.0)
                    return@addOnSuccessListener
                }

                val price = parsed.price
                if (price == null) {
                    publish("CAPTURE: OK • EUR/USD found • price not found")
                    overlay.show("WAIT • reading price", 0.0)
                    return@addOnSuccessListener
                }

                val chart = cropChart(bitmap)
                val candles = candleDetector.detect(chart)
                val pattern = patternClassifier.classify(candles)
                chart.recycle()

                val signal = engine.addPrice(price, now)
                updateSignal(signal, price, pattern.name, candles.size)
            }
            .addOnFailureListener {
                publish("CAPTURE: OK • OCR ERROR")
                overlay.show("WAIT • OCR error", 0.0)
            }
            .addOnCompleteListener {
                bitmap.recycle()
                processing = false
            }
    }

    private fun cropChart(bitmap: Bitmap): Bitmap {
        val left = (bitmap.width * 0.02f).toInt().coerceAtLeast(0)
        val top = (bitmap.height * 0.24f).toInt().coerceAtLeast(0)
        val right = (bitmap.width * 0.98f).toInt().coerceAtMost(bitmap.width)
        val bottom = (bitmap.height * 0.78f).toInt().coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            padded.copyPixelsFromBuffer(buffer)
            if (paddedWidth == image.width) padded
            else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun updateSignal(signal: SignalResult, price: Double, pattern: String, candleCount: Int) {
        val pct = String.format(Locale.US, "%.1f%%", signal.confidence * 100.0)
        publish("$signal • $pct • price ${String.format(Locale.US, "%.6f", price)} • candles $candleCount • $pattern")
        overlay.show(signal.signal, signal.confidence)
    }

    private fun publish(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < 700L && text.startsWith("CAPTURE: OK")) return
        lastUiUpdate = now
        getSystemService(NotificationManager::class.java).notify(42, notification(text))
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
        try { if (::reader.isInitialized) reader.close() } catch (_: Throwable) {}
        display?.release()
        display = null
        try { if (::projection.isInitialized) projection.stop() } catch (_: Throwable) {}
        recognizer.close()
        try { overlay.hide() } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
