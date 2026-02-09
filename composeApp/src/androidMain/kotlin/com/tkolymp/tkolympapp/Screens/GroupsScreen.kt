package com.tkolymp.tkolympapp.Screens

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.club.Cohort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cohorts by remember { mutableStateOf<List<Cohort>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val cd = ClubService().fetchClubData()
            cohorts = cd.cohorts
        } catch (ex: Throwable) {
            error = ex.message ?: "Chyba při načítání tréninkových skupin"
        } finally {
            loading = false
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tréninkové skupiny") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            if (loading) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Načítám…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }

            cohorts.forEach { cohort ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp), verticalAlignment = Alignment.Top) {

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = cohort.name ?: "(bez názvu)", style = MaterialTheme.typography.titleMedium)

                            if (!cohort.location.isNullOrBlank()) {
                                Text(text = cohort.location ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }

                            if (!cohort.description.isNullOrBlank()) {
                                val raw = cohort.description ?: ""
                                val cleaned = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").trim()
                                val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                                val linkColor = MaterialTheme.colorScheme.primary.toArgb()
                                AndroidView(factory = { ctx ->
                                    TextView(ctx).apply {
                                        setText(Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY))
                                        setTextColor(textColor)
                                        setLinkTextColor(linkColor)
                                    }
                                }, update = { tv ->
                                    tv.text = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)
                                    tv.setTextColor(textColor)
                                    tv.setLinkTextColor(linkColor)
                                }, modifier = Modifier.padding(top = 6.dp))
                            }
                        }

                        val colorBox = runCatching {
                            val c = cohort.colorRgb ?: "#CCCCCC"
                            Color(android.graphics.Color.parseColor(c))
                        }.getOrNull() ?: MaterialTheme.colorScheme.primary

                        Box(modifier = Modifier
                            .size(12.dp)
                            .background(colorBox, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
