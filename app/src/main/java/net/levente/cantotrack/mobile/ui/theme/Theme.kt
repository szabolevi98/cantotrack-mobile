package net.levente.cantotrack.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The web app's design tokens (cantotrack/src/View/Css/base/variables.css),
 * light and dark, so the phone looks like the same product. Material's
 * scheme takes the ones it has a slot for; the rest are here.
 */
@Immutable
data class CtColors(
    val primary: Color,
    val primaryDark: Color,
    val primarySoft: Color,
    val sidebarFrom: Color,
    val sidebarTo: Color,
    val sidebarText: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val violet: Color,
    val violetSoft: Color,
    val slate: Color,
    val slateSoft: Color,
    val dark: Boolean,
) {
    /** The sidebar gradient of the web app, behind every screen's header. */
    val header: Brush get() = Brush.linearGradient(listOf(sidebarFrom, sidebarTo))
}

val LightCtColors = CtColors(
    primary = Color(0xFF1A5FBF), primaryDark = Color(0xFF154E9D), primarySoft = Color(0xFFEEF4FD),
    sidebarFrom = Color(0xFF0F2038), sidebarTo = Color(0xFF1C4B86), sidebarText = Color(0xFFCDD8E8),
    background = Color(0xFFF4F6FB), surface = Color(0xFFFFFFFF), text = Color(0xFF16202F),
    muted = Color(0xFF5A6474), border = Color(0xFFE2E8F3),
    success = Color(0xFF2E8B64), successSoft = Color(0xFFE9F6F0),
    warning = Color(0xFFA4701D), warningSoft = Color(0xFFFDF4E6),
    danger = Color(0xFFC14A5F), dangerSoft = Color(0xFFFDEEF1),
    violet = Color(0xFF6F4CBE), violetSoft = Color(0xFFF3EFFC),
    slate = Color(0xFF44506A), slateSoft = Color(0xFFEEF1F7),
    dark = false,
)

val DarkCtColors = CtColors(
    primary = Color(0xFF6AA5F5), primaryDark = Color(0xFF1F5BB5), primarySoft = Color(0xFF1A2A44),
    sidebarFrom = Color(0xFF0A111D), sidebarTo = Color(0xFF14305A), sidebarText = Color(0xFFCDD8E8),
    background = Color(0xFF0E1522), surface = Color(0xFF161F2E), text = Color(0xFFE3E9F3),
    muted = Color(0xFF9AA6B8), border = Color(0xFF273246),
    success = Color(0xFF4CC08D), successSoft = Color(0xFF13291F),
    warning = Color(0xFFE0A44A), warningSoft = Color(0xFF2C2212),
    danger = Color(0xFFEF7A8E), dangerSoft = Color(0xFF331A20),
    violet = Color(0xFFA88CF0), violetSoft = Color(0xFF231C3A),
    slate = Color(0xFFB8C3D6), slateSoft = Color(0xFF222C3D),
    dark = true,
)

private val LocalCtColors = staticCompositionLocalOf { LightCtColors }

object CtTheme {
    val colors: CtColors
        @Composable @ReadOnlyComposable get() = LocalCtColors.current
}

private fun scheme(c: CtColors) = if (c.dark) {
    darkColorScheme(
        primary = c.primary, onPrimary = Color(0xFF0B1628),
        primaryContainer = c.primarySoft, onPrimaryContainer = c.primary,
        secondaryContainer = c.primarySoft, onSecondaryContainer = c.primary,
        background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.background, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.background,
        outline = c.border, outlineVariant = c.border,
        error = c.danger, onError = Color.White, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    )
} else {
    lightColorScheme(
        primary = c.primary, onPrimary = Color.White,
        primaryContainer = c.primarySoft, onPrimaryContainer = c.primaryDark,
        secondaryContainer = c.primarySoft, onSecondaryContainer = c.primaryDark,
        background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.background, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.background,
        outline = c.border, outlineVariant = c.border,
        error = c.danger, onError = Color.White, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    )
}

// The web's --ct-radius: 12px and the small 8px.
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val base = Typography()

private val typography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun CantoTrackTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkCtColors else LightCtColors
    CompositionLocalProvider(LocalCtColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors), shapes = shapes, typography = typography, content = content)
    }
}
