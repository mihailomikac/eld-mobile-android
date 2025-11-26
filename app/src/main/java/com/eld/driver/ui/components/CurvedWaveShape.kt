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
            // Start from top-left corner
            moveTo(0f, 0f)

            // Create smooth downward curve from left to right (valley/dip effect)
            // Control point pushed DOWN to create concave curve
            quadraticBezierTo(
                x1 = size.width / 2f,      // Control point at horizontal center
                y1 = size.height,           // Control point at bottom (creates dip)
                x2 = size.width,            // End at top-right
                y2 = 0f
            )

            // Draw down to bottom-right
            lineTo(size.width, size.height)

            // Draw to bottom-left
            lineTo(0f, size.height)

            close()
        }

        return Outline.Generic(path)
    }
}
