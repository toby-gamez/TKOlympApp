package com.tkolymp.tkolympapp.Screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val pkgManager = ctx.packageManager
    val pkgName = ctx.packageName
    var versionName: String? = null
    var versionCode: Long? = null
    try {
        val pi = pkgManager.getPackageInfo(pkgName, 0)
        versionName = pi.versionName
        versionCode = PackageInfoCompat.getLongVersionCode(pi)
    } catch (_: Throwable) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("O aplikaci") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Zpět")
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
            Text("Verze ${versionName ?: "?"} (Build ${versionCode ?: "?"})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Popis aplikace", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = "Tato aplikace zobrazuje rozvrhy, aktuality a informace o trenérech a prostorách. Je napojena na vzdálené API a využívá lokální notifikace pro upozornění na nové aktuality a události.", modifier = Modifier.padding(top = 6.dp))
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                Text("Licenční informace", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = "Tento projekt může obsahovat třetí strany knihoven; ověřte licence v závislostech.", modifier = Modifier.padding(top = 6.dp))
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Autoři a přispěvatelé", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Tobias Heneman") }
                            append(" - hlavní vývojář")
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Filip Cani Canibal") }
                            append(" - překladatel, podporovatel a pomocník")
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text(
                text = "Prohlášení: 'Brainrot' jazyk je zábavnou obměnou původní aplikace, obohacenou o trochu 'brainrot' humoru. Je to všechno pro zábavu a nemá být brán vážně.",
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
