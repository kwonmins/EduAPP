package com.example.myhealth.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ivory     = Color(0xFFF8F5EE)
private val SurfaceIv = Color(0xFFFCFAF5)
private val Primary   = Color(0xFF6C5CE7)
private val OnPrimary = Color(0xFFFFFFFF)
private val Secondary = Color(0xFF8C7A6B)
private val OnSurface = Color(0xFF333333)
private val OutlineIv = Color(0xFFE8E2D9)

private val LightScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    background = Ivory,
    surface = SurfaceIv,
    onSurface = OnSurface,
    outline = OutlineIv
)

/** 기존 MyHealthTheme와 이름이 겹치지 않도록 변경 */
@Composable
fun IvoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        shapes = Shapes(
            small  = RoundedCornerShape(14),
            medium = RoundedCornerShape(18),
            large  = RoundedCornerShape(24)
        ),
        content = content
    )
}
