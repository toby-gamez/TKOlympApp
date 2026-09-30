package com.tkolymp.tkolympapp.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.res.R

enum class ToolboxItemId { OVERVIEW, CALENDAR, BOARD, EVENTS, COMPETITIONS, PROFILE }

data class ToolboxItem(val id: ToolboxItemId, val iconRes: Int, val label: String, val route: String)

object ToolboxWidgetConfig {
    const val MAX_ITEMS = 5
    const val MIN_ITEMS = 1

    val selectedItemsKey = stringPreferencesKey("selected_items")
    val showTitlesKey = booleanPreferencesKey("show_titles")

    // What the widget has always shown — kept as the default so existing/new widgets that
    // haven't been reconfigured render exactly as before.
    val defaultSelection = listOf(
        ToolboxItemId.CALENDAR,
        ToolboxItemId.BOARD,
        ToolboxItemId.EVENTS,
        ToolboxItemId.COMPETITIONS,
    )

    fun catalog(): List<ToolboxItem> {
        val nav = AppStrings.current.navigation
        return listOf(
            ToolboxItem(ToolboxItemId.OVERVIEW, R.drawable.ic_widget_home, nav.overview, "overview"),
            ToolboxItem(ToolboxItemId.CALENDAR, R.drawable.ic_widget_calendar, nav.calendar, "calendar"),
            ToolboxItem(ToolboxItemId.BOARD, R.drawable.ic_widget_board, nav.board, "board"),
            ToolboxItem(ToolboxItemId.EVENTS, R.drawable.ic_widget_events, nav.events, "events"),
            ToolboxItem(ToolboxItemId.COMPETITIONS, R.drawable.ic_widget_competitions, AppStrings.current.competition.competitions, "competitions"),
            ToolboxItem(ToolboxItemId.PROFILE, R.drawable.ic_widget_profile, nav.profile, "other"),
        )
    }

    fun parseSelection(raw: String?): List<ToolboxItemId> {
        if (raw.isNullOrBlank()) return defaultSelection
        val ids = raw.split(",").mapNotNull { token ->
            try { ToolboxItemId.valueOf(token) } catch (_: Exception) { null }
        }
        return ids.ifEmpty { defaultSelection }
    }

    fun serializeSelection(ids: List<ToolboxItemId>): String = ids.joinToString(",") { it.name }

    fun resolveItems(selection: List<ToolboxItemId>): List<ToolboxItem> {
        val byId = catalog().associateBy { it.id }
        val resolved = selection.mapNotNull { byId[it] }
        return resolved.ifEmpty { catalog().filter { it.id in defaultSelection } }
    }
}
