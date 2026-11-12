package com.faceplugin.facerecognitionsdk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.ActiveLivenessCheckKind
import com.faceplugin.facerecognitionsdk.kit.ActiveLivenessParams
import com.faceplugin.facerecognitionsdk.kit.CameraFrameUtils
import com.faceplugin.facerecognitionsdk.kit.CameraPreview
import com.faceplugin.facerecognitionsdk.kit.FaceJson
import com.faceplugin.facerecognitionsdk.kit.FaceRecognitionClient
import com.faceplugin.facerecognitionsdk.kit.IdentityLivenessType
import com.faceplugin.facerecognitionsdk.kit.LiveDetect
import com.faceplugin.facerecognitionsdk.kit.VideoWorkerEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Active liveness: the user follows random prompts (turn left / turn right / blink).
 * Driven by the SDK's VideoWorker active-liveness engine. Once every check passes,
 * one passive anti-spoof check runs on the last frame as a final gate.
 */
class LivenessActivity : AppCompatActivity() {

    companion object {
        /** Checks to run. Order is shuffled every attempt so a recorded video can't replay it. */
        private val CHECKS = listOf(
            ActiveLivenessCheckKind.TURN_LEFT,
            ActiveLivenessCheckKind.TURN_RIGHT,
            ActiveLivenessCheckKind.BLINK,
        )

        private val PROMPTS = mapOf(
            ActiveLivenessCheckKind.TURN_LEFT to "Turn your head left",
            ActiveLivenessCheckKind.TURN_RIGHT to "Turn your head right",
            ActiveLivenessCheckKind.TURN_UP to "Look up",
            ActiveLivenessCheckKind.TURN_DOWN to "Look down",
            ActiveLivenessCheckKind.BLINK to "Blink your eyes",
            ActiveLivenessCheckKind.SMILE to "Smile",
            ActiveLivenessCheckKind.PERSPECTIVE to "Move closer",
        )

        private const val LENS = CameraSelector.LENS_FACING_FRONT
        private const val YAW_THRESHOLD = 25f

        /** Shows raw engine output (verdict / check / yaw) at the bottom. Turn off for release. */
        private const val DEBUG_OVERLAY = false
        private val COLOR_DEFAULT = Color.parseColor("#111827")
        private val COLOR_OK = Color.parseColor("#16A34A")
        private val COLOR_FAIL = Color.parseColor("#DC2626")
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: LivenessOverlayView
    private lateinit var txtStep: TextView
    private lateinit var txtInstruction: TextView
    private lateinit var txtHint: TextView
    private lateinit var txtDebug: TextView

    private val client by lazy { FaceRecognitionClient.get(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile private var workerReady = false
    private val finished = AtomicBoolean(false)
    private val restarting = AtomicBoolean(false)

    private val frameLock = Any()
    private var lastFrame: Bitmap? = null

    // Per-attempt progress (UI thread only).
    private var totalChecks = CHECKS.size
    private val seenChecks = LinkedHashSet<String>()

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) previewView.post { startCamera() }
            else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveness)
        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        txtStep = findViewById(R.id.txtStep)
        txtInstruction = findViewById(R.id.txtInstruction)
        txtHint = findViewById(R.id.txtHint)
        txtDebug = findViewById(R.id.txtDebug)
        txtDebug.visibility = if (DEBUG_OVERLAY) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            previewView.post { startCamera() }
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!finished.get()) startAttempt()
    }

    override fun onPause() {
        super.onPause()
        stopWorker()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        stopWorker()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    // ---------------------------------------------------------------- worker

    private fun startAttempt() {
        resetUi()
        val checks = CHECKS.shuffled()
        totalChecks = checks.size
        val threshold = SettingsActivity.getIdentifyThreshold(this)
        client.setVideoWorkerEventHandler { json: String -> onWorkerEvent(json) }
        client.async {
            val config = client.makeIdentityConfig(
                matchThreshold = threshold,
                livenessType = IdentityLivenessType.ACTIVE,
                checks = checks,
                // 40° default is a very large head turn; 25° is comfortable but still deliberate.
                params = ActiveLivenessParams(yawThreshold = YAW_THRESHOLD),
                frontCamera = LENS == CameraSelector.LENS_FACING_FRONT,
            )
            val started = client.startVideoWorker(config)
            if (isFinishing || isDestroyed) {
                client.stopVideoWorker()
                return@async
            }
            workerReady = started == 0
            restarting.set(false)
            if (!workerReady) {
                runOnUiThread { showError("Could not start liveness engine ($started)") }
            }
        }
    }

    /** Never call stopVideoWorker on the UI thread (see CameraActivity.leaveIdentify). */
    private fun stopWorker() {
        workerReady = false
        client.setVideoWorkerEventHandler(null as ((String) -> Unit)?)
        client.async { client.stopVideoWorker() }
    }

    private fun onWorkerEvent(json: String) {
        if (finished.get() || restarting.get()) return
        val event = FaceJson.parseVideoWorkerEvent(json) as? VideoWorkerEvent.Tracking ?: return
        val faces = event.faces
        runOnUiThread {
            if (finished.get() || restarting.get()) return@runOnUiThread
            if (DEBUG_OVERLAY) {
                val f = faces.firstOrNull()
                txtDebug.text = if (f == null) "no face" else String.format(
                    java.util.Locale.US,
                    "%s · %s · p=%.2f\nyaw=%.0f° pitch=%.0f° (need %.0f°)",
                    f.activeLiveness?.verdict ?: "-",
                    f.activeLiveness?.checkType ?: "-",
                    f.activeLiveness?.progress ?: 0.0,
                    f.yaw, f.pitch, YAW_THRESHOLD,
                )
            }
            when {
                faces.isEmpty() -> showPrompt("Position your face in the circle", null)
                faces.size > 1 -> showPrompt("Only one face, please", null)
                else -> {
                    val al = faces[0].activeLiveness
                    if (al == null) {
                        showPrompt("Hold still…", null)
                    } else {
                        handleActiveLiveness(al.verdict, al.checkType, al.progress)
                    }
                }
            }
        }
    }

    private fun handleActiveLiveness(verdict: String, checkType: String, rawProgress: Double) {
        when (verdict) {
            "waiting_face_align" -> {
                showPrompt("Look straight at the camera", "Keep your face inside the circle")
            }
            "check_fail" -> onAttemptFailed("Let's try that again")
            "all_checks_passed" -> onAllChecksPassed()
            else -> {
                if (checkType.isBlank() || checkType == "none") {
                    showPrompt("Hold still…", null)
                    return
                }
                seenChecks.add(checkType)
                val step = seenChecks.size.coerceAtMost(totalChecks)
                val kind = ActiveLivenessCheckKind.fromSdkCheckType(checkType)
                val prompt = kind?.let { PROMPTS[it] ?: it.defaultPrompt } ?: "Follow the prompt"
                txtStep.text = "Step $step of $totalChecks"
                showPrompt(prompt, "Then look back at the camera")

                // SDK progress may be 0..1 or 0..100; normalise.
                val p = if (rawProgress > 1.0) rawProgress / 100.0 else rawProgress
                overlay.progress = ((step - 1) + p.toFloat().coerceIn(0f, 1f)) / totalChecks
            }
        }
    }

    private fun onAllChecksPassed() {
        if (!finished.compareAndSet(false, true)) return
        workerReady = false
        overlay.ringColor = COLOR_OK
        overlay.progress = 1f
        txtStep.text = "Almost done"
        showPrompt("Verifying…", null)

        val frame = synchronized(frameLock) { lastFrame?.copy(Bitmap.Config.ARGB_8888, false) }
        stopWorker()
        if (frame == null) {
            finished.set(false)
            onAttemptFailed("Let's try that again")
            return
        }

        // Final passive anti-spoof gate on the last frame.
        val livenessThreshold = SettingsActivity.getLivenessThreshold(this)
        client.async {
            val boxes = try {
                client.faceDetection(frame, LiveDetect.livenessOnly(0))
            } catch (_: Throwable) {
                emptyList()
            }
            val box = boxes.maxByOrNull { (it.x2 - it.x1) * (it.y2 - it.y1) }
            val label = box?.livenessLabel?.lowercase().orEmpty()
            val isSpoof = label.contains("spoof") || label.contains("fake")
            val passed = box != null && !isSpoof && box.liveness >= livenessThreshold

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (passed && box != null) {
                    VerificationSuccessActivity.verifiedFace = Utils.cropFace(frame, box) ?: frame
                    startActivity(
                        Intent(this, VerificationSuccessActivity::class.java)
                            .putExtra(VerificationSuccessActivity.EXTRA_LIVENESS_SCORE, box.liveness),
                    )
                    finish()
                } else {
                    finished.set(false)
                    onAttemptFailed("Couldn't verify you're real. Try again")
                }
            }
        }
    }

    private fun onAttemptFailed(message: String) {
        if (!restarting.compareAndSet(false, true)) return
        workerReady = false
        overlay.ringColor = COLOR_FAIL
        txtStep.text = ""
        showPrompt(message, "Follow each prompt slowly")
        stopWorker()
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed && !finished.get()) startAttempt()
        }, 1500)
    }

    // ---------------------------------------------------------------- camera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                CameraPreview.bind(
                    this, cameraProvider!!, previewView, LENS, cameraExecutor, ::analyze,
                )
            } catch (e: Exception) {
                showError("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val frame = try {
            // Pass backCamera=true so the frame is only rotated upright, never mirrored.
            // The SDK's turn_left / turn_right checks expect the true (unmirrored) camera
            // image; a selfie-mirrored frame makes every head turn read as the wrong way.
            if (!workerReady || finished.get()) null
            else CameraFrameUtils.fromImageProxy(image, true)
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
        if (frame == null || !workerReady) return
        synchronized(frameLock) { lastFrame = frame }
        client.addFrame(frame)
    }

    // ---------------------------------------------------------------- ui

    private fun resetUi() {
        seenChecks.clear()
        overlay.ringColor = COLOR_DEFAULT
        overlay.progress = 0f
        txtStep.text = "Getting ready"
        showPrompt("Position your face in the circle", "Good lighting, no glasses or mask")
    }

    private fun showPrompt(main: String, hint: String?) {
        if (txtInstruction.text != main) txtInstruction.text = main
        txtHint.text = hint.orEmpty()
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
