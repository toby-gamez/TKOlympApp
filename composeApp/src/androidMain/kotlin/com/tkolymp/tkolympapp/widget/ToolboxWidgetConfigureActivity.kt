package com.tkolymp.tkolympapp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.launch

class ToolboxWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            com.tkolymp.tkolympapp.ui.theme.AppTheme {
                ConfigureScreen(
                    appWidgetId = appWidgetId,
                    onSave = { selection, showTitles -> saveAndFinish(selection, showTitles) }
                )
            }
        }
    }

    private fun saveAndFinish(selection: List<ToolboxItemId>, showTitles: Boolean) {
        lifecycleScope.launch {
            WidgetDataProvider.ensureInitialized(applicationContext)
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(applicationContext, glanceId) { prefs ->
                prefs[ToolboxWidgetConfig.selectedItemsKey] = ToolboxWidgetConfig.serializeSelection(selection)
                prefs[ToolboxWidgetConfig.showTitlesKey] = showTitles
            }
            ToolboxWidget().update(applicationContext, glanceId)

            val resultValue = android.content.Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun ConfigureScreen(
    appWidgetId: Int,
    onSave: (List<ToolboxItemId>, Boolean) -> Unit
) {
    var loaded by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(ToolboxWidgetConfig.defaultSelection) }
    var showTitles by remember { mutableStateOf(true) }

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(appWidgetId) {
        WidgetDataProvider.ensureInitialized(context)
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs: Preferences = try {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        } catch (_: Exception) {
            null
        } ?: androidx.datastore.preferences.core.emptyPreferences()
        selection = ToolboxWidgetConfig.parseSelection(prefs[ToolboxWidgetConfig.selectedItemsKey])
        showTitles = prefs[ToolboxWidgetConfig.showTitlesKey] ?: true
        loaded = true
    }

    val strings = AppStrings.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(strings.widget.toolboxConfigTitle) })
        }
    ) { padding ->
        if (!loaded) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val catalog = remember { ToolboxWidgetConfig.catalog() }
        val byId = remember(catalog) { catalog.associateBy { it.id } }
        val available = catalog.filter { it.id !in selection }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.widget.toolboxConfigShowLabels, style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = showTitles, onCheckedChange = { showTitles = it })
                    }
                }

                item {
                    Text(
                        text = strings.widget.toolboxConfigShown,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                items(selection, key = { it.name }) { itemId ->
                    val item = byId[itemId] ?: return@items
                    val index = selection.indexOf(itemId)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    selection = selection.toMutableList().apply {
                                        val tmp = this[index - 1]
                                        this[index - 1] = this[index]
                                        this[index] = tmp
                                    }
                                }
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                            IconButton(
                                enabled = index < selection.size - 1,
                                onClick = {
                                    selection = selection.toMutableList().apply {
                                        val tmp = this[index + 1]
                                        this[index + 1] = this[index]
                                        this[index] = tmp
                                    }
                                }
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                            IconButton(
                                enabled = selection.size > ToolboxWidgetConfig.MIN_ITEMS,
                                onClick = { selection = selection - itemId }
                            ) { Icon(Icons.Default.Close, contentDescription = null) }
                        }
                    }
                }

                if (available.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.widget.toolboxConfigAddMore,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(available, key = { it.id.name }) { item ->
                        val canAdd = selection.size < ToolboxWidgetConfig.MAX_ITEMS
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                color = if (canAdd) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                enabled = canAdd,
                                onClick = { selection = selection + item.id }
                            ) { Icon(Icons.Default.Add, contentDescription = null) }
                        }
                    }
                    if (selection.size >= ToolboxWidgetConfig.MAX_ITEMS) {
                        item {
                            Text(
                                text = strings.widget.toolboxConfigMaxItems,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onSave(selection, showTitles) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text(strings.save) }
        }
    }
}
