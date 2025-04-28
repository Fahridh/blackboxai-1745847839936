package com.example.arphotobooth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private var faceNodeMap = mutableMapOf<AugmentedFace, AnchorNode>()
    private var modelRenderable: ModelRenderable? = null

    private val TAG = "ARPhotobooth"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment_container) as? ArFragment
            ?: createArFragment()

        loadModel()

        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            onUpdateFrame()
        }

        val captureButton = findViewById<Button>(R.id.capture_button)
        captureButton.setOnClickListener {
            takePhoto()
        }
    }

    private fun createArFragment(): ArFragment {
        val fragment = ArFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.ar_fragment_container, fragment)
            .commitNow()
        return fragment
    }

    private fun loadModel() {
        val modelUri = "model.glb" // The 3D model file should be placed in assets folder
        ModelRenderable.builder()
            .setSource(
                this,
                RenderableSource.builder()
                    .setSource(this, android.net.Uri.parse(modelUri), RenderableSource.SourceType.GLB)
                    .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                    .build()
            )
            .setRegistryId(modelUri)
            .build()
            .thenAccept { renderable ->
                modelRenderable = renderable
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Unable to load model", throwable)
                null
            }
    }

    private fun onUpdateFrame() {
        val faceList = arFragment.arSceneView.session?.getAllTrackables(AugmentedFace::class.java) ?: return

        // Remove nodes for faces that are no longer tracked
        val iter = faceNodeMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (!faceList.contains(entry.key)) {
                arFragment.arSceneView.scene.removeChild(entry.value)
                entry.value.anchor?.detach()
                iter.remove()
            }
        }

        // Add nodes for new faces
        for (face in faceList) {
            if (!faceNodeMap.containsKey(face)) {
                val faceNode = AnchorNode(face.createAnchor(face.centerPose))
                faceNode.setParent(arFragment.arSceneView.scene)
                modelRenderable?.let {
                    val faceRegionNode = com.google.ar.sceneform.rendering.FaceRegionRenderable(it)
                    faceNode.addChild(faceRegionNode)
                }
                faceNodeMap[face] = faceNode
            }
        }
    }

    private fun takePhoto() {
        val sceneView = arFragment.arSceneView
        val bitmap = Bitmap.createBitmap(sceneView.width, sceneView.height, Bitmap.Config.ARGB_8888)
        val handler = android.os.Handler(mainLooper)

        PixelCopy.request(sceneView, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                saveBitmap(bitmap)
            } else {
                Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        }, handler)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val filename = "ARPhoto_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
        val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(path, filename)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Photo saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}
