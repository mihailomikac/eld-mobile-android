package com.eld.driver.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Curved Wave Shape - Creates smooth wave transition
 * Matches iOS CurvedWaveShape implementation
 */
class CurvedWaveShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            // Start from top-left
            moveTo(0f, 0f)

            // Create smooth curve using cubic bezier
            cubicTo(
                x1 = size.width * 0.3f,
                y1 = size.height * 1.2f,
                x2 = size.width * 0.7f,
                y2 = size.height * 1.2f,
                x3 = size.width,
                y3 = 0f
            )

            // Complete the shape
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        return Outline.Generic(path)
    }
}
