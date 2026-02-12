package com.example.findtarget

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShopNode(
    val name: String,
    val floor: Int,
    val floorPlanUri: String?, // 도면 이미지 경로
    val x: Float,
    val y: Float,
    val z: Float
) : Parcelable
