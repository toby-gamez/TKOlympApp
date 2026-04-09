package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.language.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit = {}, appVersionName: String? = null, appVersionCode: Long? = null) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.otherScreen.aboutApp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("TK Olymp", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Text("${AppStrings.current.misc.appVersion} ${appVersionName ?: "?"} (Build ${appVersionCode ?: "?"})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = AppStrings.current.about.appDescriptionTitle, style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = AppStrings.current.about.appDescriptionText, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.misc.licenseInfo, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = AppStrings.current.about.appLicenseText, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = AppStrings.current.about.authorsAndContributors, style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Tobias Heneman") }
                            append(AppStrings.current.about.leadDeveloperRole)
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Filip Cani Canibal") }
                            append(AppStrings.current.about.translatorRole)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Přemysl Křížan") }
                            append(AppStrings.current.about.bugReporterRole)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text(
                text = AppStrings.current.about.brainrotDisclaimer,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = "© 2026 TK Olymp Olomouc, z. s.",
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
