package com.dylan.glasswidget.widget

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.TintMode

/**
 * Colours for one render. Every colour carries a day and a night value so the launcher can switch
 * between them on its own when the system theme flips, without waiting for us to redraw.
 *
 * Material You dynamic schemes are always available (minSdk 31), so there is no baseline fallback.
 */
class GlassPalette(
    val scrim: GlanceColorProvider,
    val text: GlanceColorProvider,
    val textDay: Int,
    val textNight: Int,
    val secondaryDay: Int,
    val secondaryNight: Int,
) {
    companion object {
        // Scrim opacity. Deliberately above the "pure glass" 0.14–0.20 so text stays legible
        // on busy wallpapers; Clear is the more see-through of the two. Tune to taste.
        private fun scrimAlpha(variant: GlassVariant, darkScheme: Boolean): Float = when (variant) {
            GlassVariant.Clear -> if (darkScheme) 0.34f else 0.50f
            GlassVariant.Soft -> if (darkScheme) 0.48f else 0.66f
        }

        fun of(context: Context, tint: TintMode, variant: GlassVariant): GlassPalette {
            val light = dynamicLightColorScheme(context)
            val dark = dynamicDarkColorScheme(context)
            val (day, night) = when (tint) {
                TintMode.Dynamic -> light to dark
                TintMode.Light -> light to light
                TintMode.Dark -> dark to dark
            }
            val dayIsDark = tint == TintMode.Dark
            val nightIsDark = tint != TintMode.Light

            return GlassPalette(
                scrim = ColorProvider(
                    day = day.surface.copy(alpha = scrimAlpha(variant, dayIsDark)),
                    night = night.surface.copy(alpha = scrimAlpha(variant, nightIsDark)),
                ),
                text = ColorProvider(day = day.onSurface, night = night.onSurface),
                textDay = day.onSurface.toArgb(),
                textNight = night.onSurface.toArgb(),
                secondaryDay = day.onSurfaceVariant.toArgb(),
                secondaryNight = night.onSurfaceVariant.toArgb(),
            )
        }
    }
}
