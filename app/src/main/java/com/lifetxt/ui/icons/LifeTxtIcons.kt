package com.lifetxt.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LifeTxtIcons {
    val Tomato: ImageVector by lazy {
        Builder(
            name = "Tomato",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                pathFillType = NonZero
            ) {
                moveTo(12f, 6f)
                quadTo(4f, 6f, 4f, 13.5f)
                quadTo(4f, 21f, 12f, 21f)
                quadTo(20f, 21f, 20f, 13.5f)
                quadTo(20f, 6f, 12f, 6f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                pathFillType = NonZero
            ) {
                moveTo(11f, 3f)
                lineTo(9.4f, 5.1f)
                lineTo(11.4f, 5.6f)
                lineTo(12.9f, 4.1f)
                lineTo(14.4f, 5.7f)
                quadTo(14.4f, 4.2f, 13.6f, 3.4f)
                quadTo(13.0f, 2.8f, 12.2f, 2.8f)
                lineTo(10.2f, 2.8f)
                close()
            }
        }.build()
    }
}
