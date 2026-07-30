package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.ui.theme.AppTheme

@Composable
fun PrivacyConsentScreen(
    onReadPolicyClick: () -> Unit,
    onAccept: () -> Unit,
) {
    val strings = AppStrings.current.privacyConsent
    var accepted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = strings.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.body,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.readPolicyLinkLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClickLabel = strings.readPolicyLinkLabel) { onReadPolicyClick() }
                        .padding(8.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = strings.checkboxLabel) { accepted = !accepted }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = strings.checkboxLabel }
        ) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.checkboxLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAccept,
            enabled = accepted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(strings.continueButton, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Preview(name = "PrivacyConsentScreen — Light")
@Composable
private fun PrivacyConsentScreenPreviewLight() {
    AppTheme(darkTheme = false) {
        PrivacyConsentScreen(onReadPolicyClick = {}, onAccept = {})
    }
}

@Preview(name = "PrivacyConsentScreen — Dark")
@Composable
private fun PrivacyConsentScreenPreviewDark() {
    AppTheme(darkTheme = true) {
        PrivacyConsentScreen(onReadPolicyClick = {}, onAccept = {})
    }
}
