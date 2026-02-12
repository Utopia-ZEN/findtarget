package com.example.findtarget

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ArSceneView

class AdminActivity : AppCompatActivity() {

    private lateinit var sceneView: ArSceneView
    private lateinit var ivFloorPlanPreview: ImageView
    private lateinit var tvStatus: TextView
    private var selectedFloorPlanUri: String? = null
    private val collectedData = mutableListOf<ShopNode>()
    private var isAnchorSet = false
    private var currentFloor = 1
    private var cameraPose: Pose? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedFloorPlanUri = it.toString()
            ivFloorPlanPreview.setImageURI(it)
            ivFloorPlanPreview.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        sceneView = findViewById(R.id.sceneView)
        ivFloorPlanPreview = findViewById(R.id.ivFloorPlanPreview)
        tvStatus = findViewById(R.id.tvStatus)
        
        val etShopName = findViewById<EditText>(R.id.etShopName)
        val etFloor = findViewById<EditText>(R.id.etFloor)

        setupArSession()

        findViewById<Button>(R.id.btnSelectFloorPlan).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btnSaveLocation).setOnClickListener {
            if (!isAnchorSet) {
                Toast.makeText(this, "먼저 해당 층 도면을 비춰서 기준점을 잡으세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = etShopName.text.toString()
            val floor = etFloor.text.toString().toIntOrNull() ?: currentFloor
            
            if (cameraPose != null && name.isNotEmpty()) {
                val p = cameraPose!!
                val newNode = ShopNode(
                    name = name,
                    floor = floor,
                    floorPlanUri = selectedFloorPlanUri,
                    x = p.tx(),
                    y = p.ty(),
                    z = p.tz()
                )
                collectedData.add(newNode)
                Toast.makeText(this, "${floor}층 [$name] 저장 완료", Toast.LENGTH_SHORT).show()
                etShopName.text.clear()
            }
        }

        findViewById<Button>(R.id.btnGoToUser).setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            intent.putParcelableArrayListExtra("data", ArrayList(collectedData))
            startActivity(intent)
        }

        // SceneView 0.10.0 API: onArFrame 사용
        sceneView.onArFrame = { arFrame ->
            cameraPose = arFrame.camera.pose
            val images = arFrame.frame.getUpdatedTrackables(AugmentedImage::class.java)
            images.forEach { image ->
                if (image.trackingState == TrackingState.TRACKING) {
                    val floorName = image.name
                    isAnchorSet = true
                    runOnUiThread {
                        tvStatus.text = "기준점 인식: $floorName (위치 저장 가능)"
                        tvStatus.setBackgroundColor(0xFF4CAF50.toInt())
                        etFloor.setText(floorName.replace("f", "").replace("b", "-"))
                    }
                }
            }
        }
    }

    private fun setupArSession() {
        sceneView.apply {
            // SceneView 0.10.0 API: onArSessionCreated 사용
            onArSessionCreated = { session ->
                val config = Config(session)
                val aid = AugmentedImageDatabase(session)
                val floorFiles = listOf("b1.jpg", "1f.jpg", "2f.jpg", "3f.jpg", "4f.jpg", "5f.jpg", "6f.jpg")
                
                floorFiles.forEach { fileName ->
                    try {
                        val bitmap = BitmapFactory.decodeStream(assets.open(fileName))
                        aid.addImage(fileName.replace(".jpg", ""), bitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                config.augmentedImageDatabase = aid
                config.focusMode = Config.FocusMode.AUTO
                session.configure(config)
            }
        }
    }
}
