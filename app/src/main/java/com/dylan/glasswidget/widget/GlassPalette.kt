package com.dylan.glasswidget.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.TintMode

/**
 * Colours for one render. Each carries a day and a night value so the launcher can flip them itself
 * when the system theme changes (only "Wallpaper colours" actually differs between the two).
 *
 * [digit*] is the glass font's foreground: the frosted body takes this colour, the highlights stay white.
 */
class GlassPalette(
    val digitDay: Int,
    val digitNight: Int,
    val textDay: Int,
    val textNight: Int,
    val secondaryDay: Int,
    val secondaryNight: Int,
    val pillRes: Int,
    /** Light text reads best with a soft shadow where it sits straight on the wallpaper. */
    val lightText: Boolean,
) {
    val icon: GlanceColorProvider get() = ColorProvider(day = Color(textDay), night = Color(textNight))
    val divider: GlanceColorProvider
        get() = ColorProvider(day = Color(textDay).copy(alpha = 0.35f), night = Color(textNight).copy(alpha = 0.35f))

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val WHITE_SECONDARY = 0xD9FFFFFF.toInt()
        private const val INK = 0xFF1C1C1E.toInt()
        private const val INK_SECONDARY = 0xB31C1C1E.toInt()
        private const val SMOKE_GLASS = 0xFF141618.toInt()

        fun of(context: Context, tint: TintMode, variant: GlassVariant): GlassPalette {
            val clear = variant == GlassVariant.Clear
            // Clear glass lets more wallpaper through the digits.
            fun glass(c: Int) = if (clear) withAlpha(c, 0.78f) else c
            return when (tint) {
                TintMode.Frost -> GlassPalette(
                    glass(WHITE), glass(WHITE), WHITE, WHITE, WHITE_SECONDARY, WHITE_SECONDARY,
                    if (clear) R.drawable.pill_frost_clear else R.drawable.pill_frost_soft, lightText = true,
                )
                TintMode.Smoke -> GlassPalette(
                    glass(SMOKE_GLASS), glass(SMOKE_GLASS), WHITE, WHITE, WHITE_SECONDARY, WHITE_SECONDARY,
                    if (clear) R.drawable.pill_smoke_clear else R.drawable.pill_smoke_soft, lightText = true,
                )
                TintMode.Ink -> GlassPalette(
                    glass(SMOKE_GLASS), glass(SMOKE_GLASS), INK, INK, INK_SECONDARY, INK_SECONDARY,
                    if (clear) R.drawable.pill_ink_clear else R.drawable.pill_ink_soft, lightText = false,
                )
                TintMode.Dynamic -> {
                    val day = context.getColor(android.R.color.system_accent1_50)
                    val night = context.getColor(android.R.color.system_accent1_100)
                    GlassPalette(
                        glass(day), glass(night), WHITE, WHITE, WHITE_SECONDARY, WHITE_SECONDARY,
                        if (clear) R.drawable.pill_dynamic_clear else R.drawable.pill_dynamic_soft, lightText = true,
                    )
                }
            }
        }

        private fun withAlpha(color: Int, alpha: Float): Int =
            (((color ushr 24) * alpha).toInt() shl 24) or (color and 0x00FFFFFF)
    }
}
