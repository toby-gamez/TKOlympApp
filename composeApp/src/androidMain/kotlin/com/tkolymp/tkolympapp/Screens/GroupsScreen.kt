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
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.tkolymp.shared.viewmodels.GroupsViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.club.Cohort
import com.tkolymp.tkolympapp.SwipeToReload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val vm = remember { GroupsViewModel() }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

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
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { vm.load() } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
            // Loading state is represented by the SwipeToReload indicator,
            // so we no longer show an additional inline progress row here.

            state.error?.let { err -> Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }

            state.cohorts.forEach { cohort ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp), verticalAlignment = Alignment.Top) {

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = cohort.name ?: "(bez názvu)", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

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
}
