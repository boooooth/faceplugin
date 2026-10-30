package com.faceplugin.facerecognitionsdk.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faceplugin.facerecognitionsdk.R
import java.util.Locale

class VerificationSuccessActivity : AppCompatActivity() {

    companion object {
        @JvmField
        var verifiedFace: Bitmap? = null
        const val EXTRA_LIVENESS_SCORE = "extra_liveness_score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_success)

        val faceBitmap = verifiedFace
        verifiedFace = null

        faceBitmap?.let {
            findViewById<ImageView>(R.id.imgVerifiedFace).setImageBitmap(it)
        }

        val livenessScore = intent.getFloatExtra(EXTRA_LIVENESS_SCORE, 0f)
        findViewById<TextView>(R.id.txtLivenessScore).text =
            String.format(Locale.US, "Real Person: %.1f%%", livenessScore * 100)

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            finish()
        }
    }
}
