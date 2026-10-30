package com.faceplugin.facerecognitionsdk.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.FaceRecognitionClient

class MainActivity : AppCompatActivity() {

    companion object {
        /** `FP1.…` from FacePlugin for `applicationId` com.faceplugin.facerecognitionsdk. */
        private const val LICENSE_KEY =
            "FP1.RlBMMQMAAQDJx5NBpyjuxrTSuz8MAgAASPNMFWCS7q6vchdPDy23dWUgdnJm6T5LgA4HdMSGYv1yOd+oLCfrwRrNPknfqxACqbYHDTxu1SIMVNekHZW0lBnrkLNZvHNprQnTA/8GJ0XBhcT3+kUpasAT9GMzdBT5g7gM+FV8KTT2P71rWtYOIpDszqjhTpXBh0ZkSpRHFVftBlT+W94+4gLQmSg2VF6hMoPMazski4Llav+mfpgnoRDR67tcWhUyuf+wOnOS/v3uyhoE8AqXdpJKqhB4aVEXC/ZtjnQFuJwdANDSWRtzYBYX/Yb5rhzBc0c/p71ZcbP+ye054XUniZokjA/ZrQ+r4tftcAtF69sFiU60km2q6CtRu4tbQpL34YC2ztJPaLywBlwQz0lmEwXbKHmCHOc9SzdaYDWHt7lIgkxaEYztKGDjFzP0coB4R2MzKhh9vcZQePxo8TSwbZqBLb7Sk3BpZ6NYQKbM6iEwEd6VB8of2Meqc4/IJtBP9Yp6Q4P3FoIiZ+m5+Ik/P9O/Cb12zleqNLMU5qTibURBUGB0P/LCo3ueMUhlRBGz4cmbSw/OZifKrfw96axxo1uDyCi2qYIG0mTyXpQX0KW+r/aJ+3CosIukmabvPU3M/PO8Bbnem5P9udecXHsbmfgShAXQ5mDkMIhJQaB3zPwAIl5Agp2T/YRY+11bb5jN3zF7pLGQbgVc3VnDZcwv/hnsLviKADCBhwJCAIO0bZgO621nFz0Yup29vR45IcsQ7MdRHZkMs8LQk2EGwrR6gEn0Peu9Inxc25JXYUBPBN1rjWPyGW1dE4VTWQi6AkFnsguhXcZi1rnPz5et2fWhhzHPMk5wmt2cDrS62JkV0gXYFI4E8O+b2PVFHhkBO92m/kwXy2VsUk0VAQWItd///w=="
    }

    private lateinit var btnStartDemo: Button
    private lateinit var progressLoading: ProgressBar
    private lateinit var textStatus: TextView
    private var sdkReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsActivity.applyEngineDefaults(this)
        setContentView(R.layout.activity_main)

        btnStartDemo = findViewById(R.id.btnStartDemo)
        progressLoading = findViewById(R.id.progressLoading)
        textStatus = findViewById(R.id.textStatus)

        btnStartDemo.setOnClickListener {
            if (!sdkReady) {
                Toast.makeText(this, "Engine is still initializing…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CaptureActivity::class.java))
        }

        initSdk()
    }

    private fun initSdk() {
        val client = FaceRecognitionClient.get(this)
        if (client.isEngineReady) {
            onSdkReady()
            return
        }

        btnStartDemo.isEnabled = false
        progressLoading.visibility = View.VISIBLE
        textStatus.text = "Initializing AI Engine…"

        client.activate(LICENSE_KEY) { code ->
            runOnUiThread {
                progressLoading.visibility = View.GONE
                if (code == FaceRecognitionSDK.SDK_SUCCESS) {
                    onSdkReady()
                } else {
                    sdkReady = false
                    btnStartDemo.isEnabled = false
                    textStatus.text = when (code) {
                        FaceRecognitionSDK.SDK_LICENSE_INVALID -> "Invalid license key"
                        FaceRecognitionSDK.SDK_LICENSE_EXPIRED -> "License key expired"
                        FaceRecognitionSDK.SDK_NOT_ACTIVATED -> "SDK not activated"
                        else -> "Failed to initialize engine"
                    }
                    Toast.makeText(this, textStatus.text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onSdkReady() {
        sdkReady = true
        progressLoading.visibility = View.GONE
        textStatus.visibility = View.GONE
        btnStartDemo.isEnabled = true
    }
}
