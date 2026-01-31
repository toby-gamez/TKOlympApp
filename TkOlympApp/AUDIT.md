Zde stručný audit logiky projektu (bez UI), s konkrétními problémy a doporučeními.

Architektura služeb

Služby jsou statické helpery (singletony): AuthService (TkOlympApp/Services/AuthService.cs), EventService (TkOlympApp/Services/EventService.cs), UserService (TkOlympApp/Services/UserService.cs). To je jednoduché, ale ztěžuje testování a změnu na DI později.
Autentizace / tokeny

JWT ukládáno v SecureStorage a nastavováno do hlavičky HttpClientu (AuthService.InitializeAsync, AuthService.LoginAsync). Chybí refresh token / kontrola expirace a centralizované zpracování 401 (možné zůstat přihlášen s expirovaným tokenem). AuthService.cs
HttpClient a hlavičky

Statický HttpClient s BaseAddress a pevnou x-tenant-id: 1 — dobré pro reuse, ale Authorization header může přetrvávat mezi uživateli; ujistit se, že `AuthService.LogoutAsync` maže header a SecureStorage. AuthService.cs
Chybové hlášky a UX

Mnoho míst zobrazí `ex.Message` uživateli (např. `RegistrationPage.LoadAsync`), což může odkrýt interní zprávy a není lokalizované. Doporučit mapovat chyby na lokalizované texty. `RegistrationPage.xaml.cs`
Serializace / DTO

Používáte `JsonSerializerOptions(JsonSerializerDefaults.Web)` konzistentně — dobré. Nicméně DTO jsou často privátně vloženy do service tříd (např. v `EventService`), což ztěžuje unit testy a opětovné použití. Doporučit přesun kritické logiky/DTO do testovatelného core projektu. (`EventService.cs`, `README.md`)
Matchování uživatelů podle jmen

Některé algoritmy pro zjištění, že je uživatel přihlášen v registraci, porovnávají jména/texty (např. `EventPage.LoadAsync`, `EditRegistrationsPage`). To je křehké (diakritika, různé formáty, duplicitní jména). Kde to jde, používat ID (`UserService.CurrentPersonId` / párové id) místo string match. `EventPage.xaml.cs`, `UserService.cs`
Async patterns a chybějící await

Spousta UI handlerů používá `async void` (běžné pro události) — ujistit se, že všechny možné výjimky jsou ošetřené (mnoho try/catch existuje, ale někde může být unhandled). Některé stránky používají `_appeared/_loadRequested` vzor správně. `EventPage.xaml.cs`, `CouplePage.xaml.cs`
Testovatelnost a separace logiky

Testy jsou prozatím placeholdery; doporučit extrahovat čistou logiku (DateHelpers, HTML parsing, shody registrací) do `TkOlympApp.Core` nebo podobného projektu pro unit testy. `README.md`
Logging / telemetrie

Používáte `Debug.WriteLine` místy; zvážit centralizované logování (`ILogger`) a chytrou telemetrii/chyby pro produkční nasazení. `MauiProgram` má debug logging v DEBUG only. `MauiProgram.cs`
Lokalizace

`LocalizationService.ApplyLanguage` podporuje více jazyků — ujistit se, že všechny chybové texty a nové zprávy používají `LocalizationService.Get(...)` (některé jsou hardcoded). `LocalizationService.cs`
Prioritní doporučené kroky (rychlý plán)

1. Přidat token-expiry handling + 401 interceptor / central logout. `AuthService.cs`
2. Přestat spoléhat na porovnávání jmen při identifikaci uživatele; používat person/couple id. `UserService.cs`, `EventPage.xaml.cs`
3. Extrahovat business logiku/DTO do testovatelného projektu a napsat unit testy. `README.md`

Pokud chcete, připravím konkrétní PR/patch pro některý z těchto bodů (např. token refresh + 401 handling v `AuthService`, nebo náhrada name-based matching v `EventPage.LoadAsync`).