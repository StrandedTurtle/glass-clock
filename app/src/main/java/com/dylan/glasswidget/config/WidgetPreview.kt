package com.dylan.glasswidget.config

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.appwidget.compose
import com.dylan.glasswidget.R
import com.dylan.glasswidget.widget.GlassClockWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real widget, rendered by Glance into RemoteViews exactly as the home screen gets them, and
 * inflated here on top of the actual wallpaper (the settings window shows it through). Re-rendered
 * whenever [version] changes, i.e. after every settings edit and every new weather/calendar data.
 */
@Composable
fun WidgetPreview(glanceId: GlanceId, size: DpSize, version: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(version, size) {
        val rendered = withContext(Dispatchers.Default) {
            runCatching { GlassClockWidget().compose(context, glanceId, size = size) }.getOrNull()
        }
        failed = rendered == null
        if (rendered != null) views = rendered
    }

    Box(modifier.size(size.width, size.height), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { TouchBlockingFrame(it) },
            update = { frame ->
                val rv = views ?: return@AndroidView
                runCatching {
                    val inflated = rv.apply(frame.context, frame)
                    frame.removeAllViews()
                    frame.addView(inflated)
                }.onFailure { failed = true }
            },
            modifier = Modifier.size(size.width, size.height),
        )
        if (failed && views == null) Text(stringResource(R.string.preview_error), color = Color.White)
    }
}

/** The preview carries the widget's real tap actions; swallow touches so they don't launch apps. */
@SuppressLint("ViewConstructor")
private class TouchBlockingFrame(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = true
}

/** The size the launcher actually gave this widget (portrait), or null before it has been laid out. */
fun placedWidgetSize(context: Context, appWidgetId: Int): DpSize? {
    val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    return if (w > 0 && h > 0) DpSize(w.dp, h.dp) else null
}
