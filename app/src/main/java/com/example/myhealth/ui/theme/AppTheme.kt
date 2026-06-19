package com.example.myhealth.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CreamWhite = Color(0xFFFBF7EF)
private val SoftSurface = Color(0xFFFFFCF7)
private val RosePink = Color(0xFFB67A80)
private val WarmGrey = Color(0xFF827A71)
private val InkBrown = Color(0xFF332A28)
private val OutlineSoft = Color(0xFFE7DCD2)
private val Lavender = Color(0xFFAAA1D2)

private val LightScheme = lightColorScheme(
    primary = RosePink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9C2C6),
    onPrimaryContainer = InkBrown,
    secondary = WarmGrey,
    secondaryContainer = Color(0xFFECE5DA),
    tertiary = Lavender,
    tertiaryContainer = Color(0xFFE3DFF4),
    background = CreamWhite,
    surface = SoftSurface,
    surfaceVariant = Color(0xFFF2EAE0),
    onSurface = InkBrown,
    onSurfaceVariant = WarmGrey,
    outline = OutlineSoft
)

@Composable
fun IvoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp)
        ),
        content = content
    )
}
