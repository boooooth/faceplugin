package com.faceplugin.facerecognitionsdk.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.faceplugin.facerecognitionsdk.R

class VerificationSuccessActivity : AppCompatActivity() {

    companion object {
        /**
         * Face crop and liveness score from the passed check. Not shown on screen, but
         * kept so the next step (e.g. OCR / ID match) can pick them up.
         */
        @JvmField
        var verifiedFace: Bitmap? = null
        const val EXTRA_LIVENESS_SCORE = "extra_liveness_score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_success)
        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }
    }
}
