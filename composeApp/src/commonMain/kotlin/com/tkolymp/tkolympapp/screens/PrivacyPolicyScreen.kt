package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tkolymp.shared.language.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit = {}) {
    val scrollState = rememberScrollState()
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "privacyFade"
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.otherScreen.privacyPolicy) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            Text(AppStrings.current.privacy.effectiveDate, style = MaterialTheme.typography.bodySmall)
            Text(AppStrings.current.privacy.summaryTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(AppStrings.current.privacy.summaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.section1Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section1Bullet1, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text(AppStrings.current.privacy.section1Bullet2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(AppStrings.current.privacy.section1Bullet3, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))

            Text(AppStrings.current.privacy.section2Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section2Bullet1, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text(AppStrings.current.privacy.section2Bullet2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))

            Text(AppStrings.current.privacy.section3Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section3Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.section4Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section4Bullet1, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text(AppStrings.current.privacy.section4Bullet2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))

            Text(AppStrings.current.privacy.section5Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section5Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.section6Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section6Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.section7Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section7Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.section8Title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.section8Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.contactTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text(AppStrings.current.privacy.contactText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text(AppStrings.current.privacy.technicalNote, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
