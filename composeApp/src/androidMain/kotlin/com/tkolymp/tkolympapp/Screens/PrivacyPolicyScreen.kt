package com.tkolymp.tkolympapp

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit = {}) {
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zásady ochrany osobních údajů") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zpět")
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
        ) {
            Text("Datum účinnosti: 24. prosince 2025", style = MaterialTheme.typography.bodySmall)
            Text("\nStručné shrnutí", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Aplikace TkOlymp je klientem služby poskytované na https://api.rozpisovnik.cz. Shromažďujeme pouze nezbytná data pro provoz aplikace (autentizace, zobrazení událostí, oznámení a profilové informace).", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\n1) Jaká data shromažďujeme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("• Autentizační token (JWT): ukládán lokálně ve zabezpečeném úložišti zařízení pro autorizaci volání API (viz AuthService).", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text("\n• Data z API: události, instance, oznámení, informace o uživatelích/skupinách a žebříčky — načítají se dynamicky z GraphQL API podle vašeho přihlášení.", style = MaterialTheme.typography.bodyMedium)
            Text("\n• Volitelná data: pokud v aplikaci vyplníte profilové údaje nebo upravíte nastavení, budou použita ke zlepšení uživatelské zkušenosti.", style = MaterialTheme.typography.bodyMedium)

            Text("\n2) Kde a jak jsou data uložena", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("• Síťové požadavky směřují na https://api.rozpisovnik.cz/graphql. Aplikace nevyvytváří vlastní centrální úložiště dat mimo poskytovatele API.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text("\n• JWT je uložen v SecureStorage (platformní zabezpečené úložiště).", style = MaterialTheme.typography.bodyMedium)

            Text("\n3) Účel zpracování", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Data používáme pro: autentizaci, autorizaci, zobrazování vašich událostí, odesílání a přijímání notifikací, správu žebříčků a dalších funkcí, které očekáváte od klienta služby.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\n4) Sdílení a zpracování třetími stranami", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("• Data nejsou prodávána ani sdílena s třetími stranami pro marketing bez vašeho souhlasu. Sdílíme pouze s poskytovatelem API a s nezbytnými systémovými službami (např. mapy), pokud je použijete.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text("\n• Notifikace jsou zprostředkovány platformními službami (Android/iOS) přes NotificationManagerService nebo nativní rozhraní.", style = MaterialTheme.typography.bodyMedium)

            Text("\n5) Oprávnění a lokální přístup", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Aplikace vyžaduje jen nezbytná oprávnění (např. pro zobrazování map nebo příjem notifikací). Nepřistupujeme k vašim kontaktům, fotogaleriím ani dalším citlivým zdrojům bez výslovného povolení.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\n6) Jak dlouho data uchováváme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("JWT je uchováván po dobu platnosti tokenu nebo dokud se odhlásíte/nesmažete účet. Data na straně API se řídí politikou poskytovatele API; aplikace neprovádí dlouhodobé lokální ukládání citlivých dat bez souhlasu.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\n7) Vaše práva", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Máte právo požadovat přístup ke svým údajům, opravu nebo smazání. Pro tyto požadavky kontaktujte správce služby nebo použijte kontaktní kanály poskytovatele API.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\n8) Změny zásad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Zásady mohou být aktualizovány. O významných změnách budeme informovat v aplikaci nebo přes notifikaci. Doporučujeme občas zkontrolovat datum účinnosti.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\nKontakt", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Máte-li dotaz k ochraně osobních údajů nebo žádost o výmaz, kontaktujte správce služby: tkolymp.cz/kontakt", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            Text("\nTechnické detaily: implementace autentizace a volání GraphQL najdete v `Services/AuthService.cs` a `Services/EventServices.cs`. Notifikace spravuje `NotificationManagerService`.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
