# Security Audit — TKOlympApp

**Datum:** 12. března 2026  
**Auditor:** Senior Developer / GitHub Copilot  
**Scope:** Kotlin Multiplatform projekt — `composeApp`, `shared`, `iosApp`  
**API endpoint:** `https://api.rozpisovnik.cz/graphql`

---

## Shrnutí

Aplikace slouží českému tanečnímu klubu TK Olymp jako mobilní klient pro správu členů, přihlášky na akce a oznámení. Zpracovává **vysoce citlivá osobní data** (rodná čísla, adresy, telefonní čísla, datum narození, e-maily). Byl nalezen jeden kritický vektor útoku a několik vážných slabých míst v zabezpečení ukládání dat.

| Závažnost | Počet nálezů | Stav |
|-----------|--------------|------|
| 🔴 Kritická | 2 | vyřešeno |
| 🟠 Vysoká | 4 | vyřešeno |
| 🟡 Střední | 5 | 1 vyřešeno, 4 čeká |
| 🟢 Nízká | 3 | čeká |

---

## 🔴 Kritické nálezy

---

### K-1 — JWT token uložen v plaintextovém SharedPreferences

**Soubor:** [shared/src/androidMain/kotlin/com/tkolymp/shared/storage/TokenStorageAndroid.kt](shared/src/androidMain/kotlin/com/tkolymp/shared/storage/TokenStorageAndroid.kt)  
**OWASP:** A02 – Cryptographic Failures

**Kód:**
```kotlin
private val prefs = context.getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

actual suspend fun saveToken(token: String) {
    prefs.edit().putString("jwt", token).apply()     // ← plaintext na disku
}
```

**Problém:**  
JWT je uložen jako čistý text do XML souboru `/data/data/com.tkolymp.tkolympapp/shared_prefs/tkolymp_prefs.xml`. Jakýkoliv útočník s:
- rootnutým zařízením
- ADB přístupem (viz K-2)
- přístupem k záloze (viz K-2)

může token jednoduše přečíst a provést plný **session hijacking** — s platným tokenem se může přihlásit jako uživatel bez hesla.

**Řešení — použít `EncryptedSharedPreferences`:**
```kotlin
// shared/build.gradle.kts — přidat závislost:
// implementation("androidx.security:security-crypto:1.1.0-alpha06")

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class TokenStorage actual constructor(platformContext: Any) {
    private val context = platformContext as Context

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tkolymp_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual suspend fun saveToken(token: String) {
        prefs.edit().putString("jwt", token).apply()
    }
    // ...
}
```

---

### K-2 — `android:allowBackup="true"` umožňuje exfiltraci tokenu přes ADB

**Soubor:** [composeApp/src/androidMain/AndroidManifest.xml](composeApp/src/androidMain/AndroidManifest.xml)  
**OWASP:** A05 – Security Misconfiguration

**Kód:**
```xml
<application
    android:allowBackup="true"   ← !!!
    ...
```

**Problém:**  
S povolenou ADB zálohou (`adb backup`) může kdokoliv s fyzickým přístupem k telefonu (s povoleným USB debugováním) extrahovat celý adresář SharedPreferences aplikace **bez rootu**. Výsledkem je:
- JWT token (viz K-1)
- `current_user_json` s emailem, přihlašovacím jménem
- `person_details_json` s rodným číslem, adresou, telefonem (viz V-1)

**Řešení:**
```xml
<application
    android:allowBackup="false"
    ...
```

Nebo pokud je záloha žádoucí (obnovení nastavení), vyloučit citlivé soubory:
```xml
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"
    ...
```

```xml
<!-- res/xml/backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="tkolymp_prefs.xml"/>
    <exclude domain="sharedpref" path="user_prefs.xml"/>
</full-backup-content>
```

---

## 🟠 Vysoké nálezy

---

### V-1 — Osobní identifikační číslo, adresa, datum narození — v plaintextu v SharedPreferences

**Soubor:** [shared/src/androidMain/kotlin/com/tkolymp/shared/storage/UserStorage.android.kt](shared/src/androidMain/kotlin/com/tkolymp/shared/storage/UserStorage.android.kt)  
**OWASP:** A02 – Cryptographic Failures

**Kód:**
```kotlin
actual suspend fun savePersonDetailsJson(json: String) {
    prefs.edit().putString("person_details_json", json).apply()  // plaintext
}
```

**Data uložená v `person_details_json`** (z GraphQL dotazu v [UserService.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/user/UserService.kt)):
```graphql
person(id: $id) {
  bio birthDate lastName firstName email phone
  nationalIdNumber          # ← RODNÉ ČÍSLO
  address {
    city conscriptionNumber district orientationNumber postalCode region street
  }
  ...
}
```

Rodné číslo je v ČR identifikátor ekvivalentní SSN — jeho únik je přestupek dle GDPR s potenciálem pokut. Tato data jsou dostupná stejnými útočnými vektory jako JWT (K-1, K-2).

**Řešení:**  
Stejně jako u K-1 — přejít na `EncryptedSharedPreferences` pro soubor `user_prefs`. Zvážit, zda je vůbec nutné ukládat `nationalIdNumber` lokálně (načítat jen on-demand ze serveru a neukládat).

---

### V-2 — Žádné certificate pinning pro API komunikující s přihlašovacími údaji

**Soubor:** [shared/src/androidMain/kotlin/com/tkolymp/shared/PlatformNetwork.kt](shared/src/androidMain/kotlin/com/tkolymp/shared/PlatformNetwork.kt)  
**OWASP:** A02 – Cryptographic Failures

**Kód:**
```kotlin
val client = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(15, TimeUnit.SECONDS)
            // ← žádné certificate pinning
        }
    }
}
```

**Problém:**  
Prostředí bez certificate pinningu jsou zranitelná vůči MitM útokům přes:
- Kompromitovanou CA (vydání falešného certifikátu pro `api.rozpisovnik.cz`)
- Uživatelem nainstalovaný certifikát (obvyklé při pentestu nebo v firmě s MDM)
- Vývojářský proxy na testovacím zařízení

Přes toto spojení prochází přihlašovací jméno a heslo (v mutation těle).

**Řešení:**
```kotlin
// shared — přidat do PlatformNetwork.kt
engine {
    config {
        certificatePinner(
            CertificatePinner.Builder()
                // Zjistit aktuální pin: openssl s_client -connect api.rozpisovnik.cz:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                .add("api.rozpisovnik.cz", "sha256/AAAA...=")  // primární pin
                .add("api.rozpisovnik.cz", "sha256/BBBB...=")  // záložní pin
                .build()
        )
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(15, TimeUnit.SECONDS)
        writeTimeout(15, TimeUnit.SECONDS)
    }
}
```

> **Poznámka:** Vždy přidávejte záložní pin pro případ rotace certifikátu.

---

### V-3 — Debug `println` logy v produkčním kódu

**Soubory:**
- [shared/src/commonMain/kotlin/com/tkolymp/shared/network/GraphQlClientImpl.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/network/GraphQlClientImpl.kt)
- [shared/src/commonMain/kotlin/com/tkolymp/shared/auth/AuthService.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/auth/AuthService.kt)

**OWASP:** A09 – Security Logging and Monitoring Failures

**Kód:**
```kotlin
// GraphQlClientImpl.kt
println("GraphQlClientImpl.post: posting to $endpoint, tokenPresent=${token != null}")

// AuthService.kt
val errors = resp.jsonObject["errors"]?.toString() ?: resp.toString()
try { println("Login failed: $errors") } catch (_: Throwable) {}

try { println("refreshJwt failed: ${ex.message}") } catch (_: Throwable) {}
```

**Problém:**  
- Logcat je přístupný všem aplikacím s oprávněním `READ_LOGS` (standardně dostupné přes ADB nebo root).
- `errors` obsahuje surovou odpověď serveru — pokud server v chybové zprávě echuje vstup (např. `"Invalid login 'jméno@email.cz'"`), e-mail se objeví v logu.
- ProGuard `println` nestripuje automaticky.

**Řešení:**  
Odebrat všechny `println` z produkčního kódu. Pokud je logování potřeba pro vývoj, použít Timber nebo podmíněný logger:

```kotlin
// shared/commonMain — přidat simple logger
object Logger {
    var isDebugEnabled = false  // nastavit v BuildConfig / initNetworking
    fun d(tag: String, msg: String) {
        if (isDebugEnabled) println("[$tag] $msg")
    }
}
```

---

### V-4 — string interpolace v GraphQL dotazech (neescapovaná ID)

**Soubor:** [shared/src/commonMain/kotlin/com/tkolymp/shared/people/PeopleService.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/people/PeopleService.kt)  
**OWASP:** A03 – Injection

**Kód:**
```kotlin
// ŽÁDNÉ escaping nebo validace:
val query = """
    query MyQuery {
      person(id: "$personId") {
        lastName firstName
      }
    }
""".trimIndent()

val query = """
    query MyQuery {
      couple(id: "$coupleId") {
```

**Problém:**  
`personId` a `coupleId` jsou vkládány přímo do query stringu. V současné době pocházejí ze serverové odpovědi (typicky číselné ID), ale pokud by se někdy dostalo malformované nebo malicious ID do kódu, mohlo by rozbít nebo pozměnit GraphQL strukturu.  
Navíc `AuthService.login` používá ruční escaping jen pro `\` a `"` — vynechává `$`, `\n`, `\r`, unicode escape sekvence.

**Řešení — vždy používat GraphQL proměnné:**
```kotlin
// Správný způsob (konzistentní s jinými dotazy v codebase):
val query = """
    query GetPerson(${'$'}id: ID!) {
      person(id: ${'$'}id) {
        lastName firstName
      }
    }
""".trimIndent()

val variables = buildJsonObject { put("id", JsonPrimitive(personId)) }
client.post(query, variables)
```

---

## 🟡 Střední nálezy

---

### S-1 — JWT bez proaktivní validace expirace

**Soubor:** [shared/src/commonMain/kotlin/com/tkolymp/shared/auth/AuthService.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/auth/AuthService.kt)

```kotlin
override suspend fun hasToken(): Boolean = currentToken != null
override fun getToken(): String? = currentToken
```

Token je považován za platný, dokud není `null`. Neexistuje kontrola claimu `exp`. **Praktický dopad:** 
- Expirovaný token je odesílán na server, dokud server nevrátí chybu.
- `refreshJwt()` není nikdy volán proaktivně — jen externě na vyžádání.
- Neexistuje auto-retry middleware po 401 odpovědi.

**Řešení:**
```kotlin
// Přidat do AuthService:
private fun isTokenExpired(token: String): Boolean {
    return try {
        val parts = token.split(".")
        if (parts.size != 3) return true
        val payload = java.util.Base64.getUrlDecoder()
            .decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='))
        val json = Json.parseToJsonElement(String(payload)).jsonObject
        val exp = json["exp"]?.jsonPrimitive?.long ?: return true
        exp < (System.currentTimeMillis() / 1000)
    } catch (_: Exception) { true }
}

override suspend fun hasToken(): Boolean {
    val t = currentToken ?: return false
    if (isTokenExpired(t)) {
        return refreshJwt()  // pokus o refresh
    }
    return true
}
```

---

### S-2 — Hardcoded API URL a tenant ID ve zdrojovém kódu

**Soubory:**  
- [composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/App.kt](composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/App.kt) — `"https://api.rozpisovnik.cz/graphql"`  
- [shared/src/commonMain/kotlin/com/tkolymp/shared/network/GraphQlClientImpl.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/network/GraphQlClientImpl.kt) — `header("x-tenant-id", "1")`

**Problém:**  
Konfigurace prostředí (staging/prod) vyžaduje zmenu zdrojového kódu a nový build. Hardcoded `x-tenant-id` neumožňuje jednoduchý multi-tenant provoz nebo testování.

**Řešení:**
```kotlin
// composeApp/build.gradle.kts — přidat buildConfigField
buildTypes {
    debug {
        buildConfigField("String", "API_BASE_URL", "\"https://staging.rozpisovnik.cz/graphql\"")
        buildConfigField("String", "TENANT_ID", "\"1\"")
    }
    release {
        buildConfigField("String", "API_BASE_URL", "\"https://api.rozpisovnik.cz/graphql\"")
        buildConfigField("String", "TENANT_ID", "\"1\"")
    }
}
// Použití: initNetworking(ctx, BuildConfig.API_BASE_URL)
```

---

### S-3 — Nepoužitá závislost `biometric:1.4.0-alpha02`

**Soubor:** [composeApp/build.gradle.kts](composeApp/build.gradle.kts)

```kotlin
implementation("androidx.biometric:biometric:1.4.0-alpha02")
```

Závislost je přítomna, ale žádný kód ji nepoužívá. Komentář v `shared/build.gradle.kts` dokonce říká _"Removed biometric support."_ Alpha verze může obsahovat nezralé bezpečnostní implementace a zvětšuje útočnou plochu bez přidané hodnoty.

**Řešení:** Odstranit řádek. Pokud bude biometrika v budoucnu implementována, použít stabilní verzi a správně navázat na `EncryptedSharedPreferences` pro odemknutí klíče.

---

### S-4 — ~~Chybějící `actual` implementace pro iOS secure storage~~ ✅ vyřešeno

**Adresář:** `shared/src/iosMain/`

Implementovány `actual` třídy používající **iOS Keychain** (`Security.framework`) pro všechna tři úložiště:

- [shared/src/iosMain/kotlin/com/tkolymp/shared/storage/TokenStorage.ios.kt](shared/src/iosMain/kotlin/com/tkolymp/shared/storage/TokenStorage.ios.kt) — JWT token přes `kSecClassGenericPassword`
- [shared/src/iosMain/kotlin/com/tkolymp/shared/storage/UserStorage.ios.kt](shared/src/iosMain/kotlin/com/tkolymp/shared/storage/UserStorage.ios.kt) — `person_id`, `couple_ids`, `current_user_json`, `person_details_json` (rodné číslo, adresa)
- [shared/src/iosMain/kotlin/com/tkolymp/shared/notification/NotificationStorage.ios.kt](shared/src/iosMain/kotlin/com/tkolymp/shared/notification/NotificationStorage.ios.kt) — nastavení a naplánované notifikace

Všechny položky jsou uloženy jako `kSecClassGenericPassword` s `kSecAttrService = "com.tkolymp.tkolympapp"`. Data jsou šifrována iOS Keychainem (AES-256) a **nejsou** synchronizována s iCloud ani extrahovatelná přes iTunes zálohu (na rozdíl od NSUserDefaults).

---

### S-5 — Potenciální race condition při dvojitém loginu

**Soubor:** [shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/LoginViewModel.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/LoginViewModel.kt)

```kotlin
suspend fun login(): Boolean {
    if (_state.value.isLoading) return false   // ← non-atomic check
    ...
```

Kontrola `isLoading` není atomická — dva souběžné coroutine může přečíst stejný stav před první aktualizací. Výsledkem by mohly být dva paralelní login requesty s heslem.

**Řešení:** Použít `Mutex` nebo `AtomicBoolean`:
```kotlin
private val loginMutex = Mutex()

suspend fun login(): Boolean = loginMutex.withLock {
    // žádná potřeba `isLoading` guard zde, mutex zajišťuje jednoznačnost
    ...
}
```

---

## 🟢 Nízká závažnost

---

### N-1 — Zastaralé závislosti

**Soubor:** [gradle/libs.versions.toml](gradle/libs.versions.toml)

| Závislost | Aktuální | Doporučená |
|-----------|----------|------------|
| `ktor` | `2.3.4` | `3.1.x` (Ktor 2.x přechází do LTS módu) |
| `kotlinx-serialization` | `1.6.0` | `1.7.x` |
| `accompanist-swiperefresh` | `0.30.1` | Deprecated — nahradit nativním Compose `PullRefresh` |
| `androidx.compose:compose-bom` | `2024.10.00` | Aktualizovat na nejnovější stabilní |

**Poznámka k `agp = "9.1.0"`:** Tato verze Android Gradle Pluginu neexistuje jako stabilní release (aktuálně 8.x). Ověřte, zda se nejedná o překlep, jinak může dojít k resolution problémům při buildu.

---

### N-2 — ProGuard chybí pravidla pro OkHttp

**Soubor:** [composeApp/proguard-rules.pro](composeApp/proguard-rules.pro)

OkHttp3 poměrně agresivně spoléhá na reflexi a bez explicitních pravidel může být problematicky zaóbfuskován:

```proguard
# Přidat:
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
```

---

### N-3 — `WAKE_LOCK` oprávnění implicitně vyžadováno AlarmManagerem

Aplikace používá `AlarmManager` pro notifikace, ale v `AndroidManifest.xml` není deklarováno `RECEIVE_BOOT_COMPLETED` oprávnění. Pokud jsou alarmy nastaveny na `RTC_WAKEUP`, zařízení je buzeno — bez explicitního oprávnění může dojít k nedefinovanému chování na některých zařízeních.

Pokud je záměrem notifikovat i po restartu zařízení, přidat:
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

## Prioritizovaný plán nápravy

| Priorita | Akce | Komplexnost |
|----------|------|-------------|
| **1. Týden** | K-1: Přejít na `EncryptedSharedPreferences` pro token i user data | Střední |
| **1. Týden** | K-2: Nastavit `android:allowBackup="false"` nebo exclusion rules | Nízká |
| **2. Týden** | V-3: Odstranit všechny `println` logy | Nízká |
| **2. Týden** | V-4: Nahradit string interpolaci GraphQL proměnnými | Střední |
| **3. Týden** | V-2: Implementovat certificate pinning | Střední |
| **3. Týden** | S-3: Odebrat nepoužitou biometric závislost | Trivial |
| **4. Týden** | S-1: Přidat JWT expiry check + auto-refresh | Střední |
| **4. Týden** | S-2: Přesunout config do BuildConfig | Nízká |
| **Průběžně** | N-1: Aktualizovat závislosti | Nízká |
| **Průběžně** | N-2: Doplnit ProGuard pravidla | Trivial |
| **Před iOS** | S-4: Implementovat iOS Keychain storage | ✅ Hotovo |

---

## Závěr

Nejzávažnějším problémem je kombinace **K-1 + K-2 + V-1**: JWT token a GDPR-citlivá osobní data (rodné číslo, adresa) jsou uložena v plaintextu a jsou extrahovatelná bez rootu jediným příkazem `adb backup`. Toto je nutné řešit jako první, protože porušení je přímé a triviálně zneužitelné.

Zbývající nálezy jsou standardní pro KMP aplikaci ve vývoji — jsou řešitelné a neukazují na systémové architektonické problémy.

> Tento audit pokrývá statickou analýzu zdrojového kódu. Doporučuje se doplnit o dynamické testování (DAST) se zachytáváním provozu (Burp Suite / mitmproxy) a review serverové části API.
