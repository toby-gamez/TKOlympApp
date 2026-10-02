package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.user.LinkedPerson

/** Shown once after login when the account is linked to more than one person. */
@Composable
fun PersonSelectionScreen(
    persons: List<LinkedPerson>,
    onSelect: (LinkedPerson) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp)
    ) {
        Text(
            AppStrings.current.otherScreen.switchPerson,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            AppStrings.current.otherScreen.switchPersonHint,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
        )
        persons.forEach { person ->
            ProfileCard(
                name = person.name,
                dob = person.birthDate,
                showQr = false,
                onClick = { onSelect(person) },
                onQrClick = {},
                cardModifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }
    }
}
