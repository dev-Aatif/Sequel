package dev.sequel.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pixel-perfect Google 'G' brand mark drawn using vector path coordinates.
 * Features official Google colors:
 * - Blue:   #4285F4
 * - Green:  #34A853
 * - Yellow: #FBBC05
 * - Red:    #EA4335
 */
@Composable
fun GoogleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    // Official Google G vector SVG path segments (24x24 viewBox)
    val bluePathData = "M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"
    val greenPathData = "M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.24v3.15C3.26 21.36 7.33 24 12 24z"
    val yellowPathData = "M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.24C.45 8.15 0 9.92 0 12s.45 3.85 1.24 5.42l4.04-3.15z"
    val redPathData = "M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.24 6.58l4.04 3.15c.95-2.83 3.6-4.98 6.72-4.98z"

    val bluePath: Path = remember { PathParser().parsePathString(bluePathData).toPath() }
    val greenPath: Path = remember { PathParser().parsePathString(greenPathData).toPath() }
    val yellowPath: Path = remember { PathParser().parsePathString(yellowPathData).toPath() }
    val redPath: Path = remember { PathParser().parsePathString(redPathData).toPath() }

    val blueColor = remember { Color(0xFF4285F4) }
    val greenColor = remember { Color(0xFF34A853) }
    val yellowColor = remember { Color(0xFFFBBC05) }
    val redColor = remember { Color(0xFFEA4335) }

    Canvas(modifier = modifier.size(size)) {
        val scaleFactor = this.size.width / 24f
        scale(scaleFactor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path = bluePath, color = blueColor)
            drawPath(path = greenPath, color = greenColor)
            drawPath(path = yellowPath, color = yellowColor)
            drawPath(path = redPath, color = redColor)
        }
    }
}
