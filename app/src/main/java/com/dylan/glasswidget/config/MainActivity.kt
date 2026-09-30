package com.dylan.glasswidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.CalendarRefreshWorker
import com.dylan.glasswidget.data.WeatherRefreshWorker
import com.dylan.glasswidget.widget.GlassClockWidget

/**
 * The launcher-icon entry point. Reconfigure a placed widget (straight to its settings when there is
 * exactly one), or see how to add one — for launchers that don't offer a reconfigure action.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WeatherRefreshWorker.schedule(this)
        CalendarRefreshWorker.observe(this)

        fun configure(appWidgetId: Int) = startActivity(
            Intent(this, ConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onConfigure = ::configure,
                        // Hand over to settings and drop this screen so Back exits instead of looping.
                        onSingleWidget = { id -> configure(id); finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(onConfigure: (Int) -> Unit, onSingleWidget: (Int) -> Unit) {
    val context = LocalContext.current
    var ids by remember { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(Unit) {
        val manager = GlanceAppWidgetManager(context)
        val found = manager.getGlanceIds(GlassClockWidget::class.java).map { manager.getAppWidgetId(it) }
        if (found.size == 1) onSingleWidget(found.single()) else ids = found
    }

    val list = ids ?: return
    Column(
        modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.main_title), style = MaterialTheme.typography.headlineMedium)
        if (list.isEmpty()) {
            Text(stringResource(R.string.main_no_widget_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.main_no_widget_body), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.main_pick_widget), style = MaterialTheme.typography.titleMedium)
            list.forEachIndexed { index, id ->
                Button(onClick = { onConfigure(id) }) { Text(stringResource(R.string.main_widget_n, index + 1)) }
            }
        }
        Text(stringResource(R.string.section_reliability), style = MaterialTheme.typography.titleMedium)
        ReliabilityCard()
    }
}
