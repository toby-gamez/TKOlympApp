# TK Olymp aplikace

Krátký přehled projektu
- **TkOlympApp** je klientská .NET MAUI aplikace (mobilní) sloužící jako klient tkolymp.cz. Aplikace zobrazuje události, žebříčky, nástěnku a detailní stránky událostí.

Použité technologie
- Platforma: .NET MAUI (multi-target: Android, iOS, v budoucnu - Mac OS a Windows)
- Jazyk: C# + XAML
- Síť: GraphQL dotazy přes `HttpClient` (statické služby v `TkOlympApp/Services/`)
- Serializace: `System.Text.Json` s `JsonSerializerDefaults.Web`

Hlavní funkce
- Autentizace a ukládání JWT do `SecureStorage` (viz `TkOlympApp/Services/AuthService.cs`).
- Načítání a zobrazení údálostí pomocí GraphQL (viz `TkOlympApp/Services/EventService.cs`).
- Nástěnka a oznámení (viz `TkOlympApp/Services/NoticeboardService.cs`).
- Žebříčky (viz `TkOlympApp/Services/LeaderboardService.cs`).
- Uživatelský profil (viz `TkOlympApp/Services/UserService.cs`).

Struktura projektu (vybrané soubory)
- `TkOlympApp/MauiProgram.cs` – konfigurace aplikace a registrace fontů/ikon.
- `TkOlympApp/AppShell.xaml.cs` – routing a navigace (Shell + URI query parameters).
- `TkOlympApp/Pages/` – stránky aplikace (např. `MainPage`, `EventPage`, `LeaderboardPage`, `NoticeboardPage`, `LoginPage`).
- `TkOlympApp/Services/` – statické služby pro síťovou komunikaci a business logiku (`AuthService`, `EventService`, `NoticeboardService`, `LeaderboardService`, `UserService`).
- `TkOlympApp/Helpers/` – pomocné třídy (např. `HtmlHelpers`, `DateHelpers`).
- `TkOlympApp/Converters/` – XAML konvertory pro vazbu dat.

Důležité konvence
- Služby jsou implementovány jako statické helper třídy (není zde DI container). Pokud se přidá DI, dělej to konzistentně napříč projektem.
- GraphQL dotazy a odpovědní DTO často definovány jako interní privátní třídy/recordy uvnitř konkrétní service třídy.
- `JsonSerializerOptions(JsonSerializerDefaults.Web)` se používá pro konzistentní (de)serializaci.
- Uživatelské texty a chybová hlášení držíme v češtině.

Rychlý start (CLI)
1. Build (Debug):

```bash
dotnet build TkOlympApp.sln -c Debug
```

2. Publish Android (příklad):

```bash
dotnet publish TkOlympApp/TkOlympApp.csproj -f net10.0-android -c Release -o ./publish/android
```

Výsledné APK najdeš v `TkOlympApp/bin/<Config>/net10.0-android/<arch>/`.

Kde hledat další příklady
- Přehled služeb: `TkOlympApp/Services/*.cs`
- Stránky a navigace: `TkOlympApp/Pages/*` a `TkOlympApp/AppShell.xaml.cs`
- Pomocné utility: `TkOlympApp/Helpers/*`, `TkOlympApp/Converters/*`

Poznámka
- README je záměrně zaměřené na popis projektu a použitých funkcí. Kontakty, informace o AI nebo jiné externí reference nejsou v tomto dokumentu zahrnuty.
