package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.personalevents.PersonalEvent
import com.tkolymp.shared.viewmodels.PersonalEventsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalEventsScreen(onBack: () -> Unit = {}, onEdit: (String?) -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val vm = viewModel<PersonalEventsViewModel>()
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.loadAll() }

    Scaffold(topBar = { TopAppBar(title = { Text(AppStrings.current.personalEvents.myTrainings) }) }) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(bottom = bottomPadding)) {
            items(state.events) { ev: PersonalEvent ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable { onEdit(ev.id) }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = ev.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = ev.startIso, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
