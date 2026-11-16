package com.taras.autoflow.service

import android.graphics.Path
import android.graphics.PointF
import kotlin.random.Random

object HumanizedMotionEngine {
    fun generateCurvedPath(start: PointF, end: PointF): Path {
        val path = Path().apply { moveTo(start.x, start.y) }
        
        val jitterX = (Random.nextFloat() - 0.5f) * 60f
        val jitterY = (Random.nextFloat() - 0.5f) * 60f

        val ctrl1 = PointF(
            start.x + (end.x - start.x) * 0.25f + jitterX,
            start.y + (end.y - start.y) * 0.1f + jitterY
        )
        val ctrl2 = PointF(
            start.x + (end.x - start.x) * 0.75f - jitterX,
            start.y + (end.y - start.y) * 0.9f - jitterY
        )

        path.cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, end.x, end.y)
        return path
    }
}
