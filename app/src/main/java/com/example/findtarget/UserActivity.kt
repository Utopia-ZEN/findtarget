package com.example.findtarget

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.math.Position
import kotlin.math.atan2
import kotlin.math.sqrt

class UserActivity : AppCompatActivity() {

    private lateinit var sceneView: ArSceneView
    private lateinit var ivMiniMap: ImageView
    private lateinit var tvDistance: TextView
    private lateinit var ivDirectionArrow: ImageView
    
    private var shopData = listOf<ShopNode>()
    private var targetNode: ArModelNode? = null
    private var isLocalized = false
    private var cameraPose: Pose? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        sceneView = findViewById(R.id.userSceneView)
        ivMiniMap = findViewById(R.id.ivMiniMap)
        tvDistance = findViewById(R.id.tvDistance)
        ivDirectionArrow = findViewById(R.id.ivDirectionArrow)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val btnSearch = findViewById<Button>(R.id.btnSearch)

        shopData = intent.getParcelableArrayListExtra<ShopNode>("data") ?: listOf()

        setupArSession()

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString()
            val result = shopData.find { it.name.contains(query, ignoreCase = true) }

            if (result != null) {
                navigateToShop(result)
                displayMiniMap(result.floorPlanUri)
            } else {
                Toast.makeText(this, "상점을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        sceneView.onArFrame = { arFrame ->
            cameraPose = arFrame.camera.pose
            val images = arFrame.frame.getUpdatedTrackables(AugmentedImage::class.java)
            for (image in images) {
                if (image.trackingState == TrackingState.TRACKING) {
                    if (!isLocalized) {
                        isLocalized = true
                        runOnUiThread {
                            Toast.makeText(this, "${image.name} 기준으로 위치가 보정되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            updateNavigationUI()
        }
    }

    private fun setupArSession() {
        sceneView.apply {
            onArSessionCreated = { session ->
                val config = Config(session)
                val aid = AugmentedImageDatabase(session)
                val floorFiles = listOf("b1.jpg", "1f.jpg", "2f.jpg", "3f.jpg", "4f.jpg", "5f.jpg", "6f.jpg")
                
                floorFiles.forEach { fileName ->
                    try {
                        val bitmap = BitmapFactory.decodeStream(assets.open(fileName))
                        aid.addImage(fileName.replace(".jpg", ""), bitmap)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                config.augmentedImageDatabase = aid
                config.focusMode = Config.FocusMode.AUTO
                session.configure(config)
            }
        }
    }

    private fun navigateToShop(shop: ShopNode) {
        targetNode?.let { sceneView.removeChild(it) }
        targetNode = ArModelNode(sceneView.engine).apply {
            position = Position(shop.x, shop.y, shop.z)
            loadModelGlbAsync("https://sceneview.github.io/assets/models/MaterialSuite.glb") {
                tvDistance.visibility = View.VISIBLE
            }
        }
        sceneView.addChild(targetNode!!)
    }

    private fun updateNavigationUI() {
        val node = targetNode ?: return
        val pose = cameraPose ?: return
        
        val dx = node.position.x - pose.tx()
        val dy = node.position.y - pose.ty()
        val dz = node.position.z - pose.tz()
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        val targetAngle = atan2(dx, dz)
        // Pose.zAxis[index]는 지원되지 않으므로 zAxis 프로퍼티 대신 메서드나 계산식을 사용해야 함.
        // ARCore Pose에서 방향 벡터를 얻기 위해 rotationQuaternion을 사용하거나
        // 간단하게 카메라의 forward 벡터(Z축)를 추출합니다.
        val zAxis = pose.zAxis
        val cameraAngle = atan2(zAxis[0], zAxis[2])
        var relativeAngle = Math.toDegrees((targetAngle - cameraAngle).toDouble()).toFloat()
        relativeAngle = (relativeAngle + 180) % 360 - 180

        runOnUiThread {
            tvDistance.text = String.format("목적지까지: %.1fm", distance)
            if (distance > 1.2f && (relativeAngle > 35 || relativeAngle < -35)) {
                ivDirectionArrow.visibility = View.VISIBLE
                ivDirectionArrow.rotation = relativeAngle
            } else {
                ivDirectionArrow.visibility = View.GONE
            }
            if (distance < 1.5f) tvDistance.text = "목적지에 도착했습니다!"
        }
    }

    private fun displayMiniMap(uriString: String?) {
        if (uriString != null) {
            ivMiniMap.visibility = View.VISIBLE
            ivMiniMap.setImageURI(Uri.parse(uriString))
        } else {
            ivMiniMap.visibility = View.GONE
        }
    }
}
