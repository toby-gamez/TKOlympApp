package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.tkolymp.tkolympapp.components.MenuRow
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
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
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import com.tkolymp.tkolympapp.util.StaggeredItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import androidx.compose.foundation.layout.Box

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    appVersionName: String? = null,
    appVersionCode: Long? = null,
    onPrivacyClick: () -> Unit = {},
    onTermsClick: () -> Unit = {}
) {

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
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { contentVisible = true }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = padding.calculateTopPadding(), start = Spacing.default, end = Spacing.default, bottom = Spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            Row(modifier = Modifier.padding(top = Spacing.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary),
                    contentAlignment = Alignment.Center
                ) { com.tkolymp.tkolympapp.platform.AppLogo(size = 52.dp) }
                Column {
                    Text("TK Olymp", fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${AppStrings.current.misc.appVersion} ${appVersionName ?: "?"} (Build ${appVersionCode ?: "?"})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            StaggeredItem(index = 0, visible = contentVisible, baseDelayMs = 60) {
                Column {
                    SectionLabel(AppStrings.current.about.appDescriptionTitle, horizontalPadding = 0.dp)
                    ScheduleCard { Text(text = AppStrings.current.about.appDescriptionText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp)) }
                }
            }

            StaggeredItem(index = 1, visible = contentVisible, baseDelayMs = 60) {
                Column {
                    SectionLabel(AppStrings.current.misc.licenseInfo, horizontalPadding = 0.dp)
                    ScheduleCard { Text(text = AppStrings.current.about.appLicenseText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp)) }
                }
            }

            StaggeredItem(index = 2, visible = contentVisible, baseDelayMs = 60) {
            Column {
                SectionLabel(AppStrings.current.about.authorsAndContributors, horizontalPadding = 0.dp)
                ScheduleCard {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Tobias Heneman") }
                            append(AppStrings.current.about.leadDeveloperRole)
                        },
                        modifier = Modifier
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Filip Cani Canibal") }
                            append(AppStrings.current.about.translatorRole)
                        },
                        modifier = Modifier
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Přemysl Křížan") }
                            append(AppStrings.current.about.bugReporterRole)
                        },
                        modifier = Modifier
                    )
                }
            }
                }
            }

            StaggeredItem(index = 3, visible = contentVisible, baseDelayMs = 60) {
                Column(modifier = Modifier.padding(top = Spacing.default)) {
                    ScheduleCard {
                        MenuRow(Icons.Filled.Security, AppStrings.current.otherScreen.privacyPolicy, onPrivacyClick)
                        RowDivider()
                        MenuRow(Icons.Filled.Description, AppStrings.current.otherScreen.termsOfUse, onTermsClick)
                    }
                }
            }

            StaggeredItem(index = 4, visible = contentVisible, baseDelayMs = 60) {
            Column {
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
    }
}
