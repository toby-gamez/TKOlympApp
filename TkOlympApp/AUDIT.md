Zde stručný audit logiky projektu (bez UI), s konkrétními problémy a doporučeními.

Architektura služeb

Služby jsou statické helpery (singletony): AuthService (TkOlympApp/Services/AuthService.cs), EventService (TkOlympApp/Services/EventService.cs), UserService (TkOlympApp/Services/UserService.cs). To je jednoduché, ale ztěžuje testování a změnu na DI později.



Používáte `JsonSerializerOptions(JsonSerializerDefaults.Web)` konzistentně — dobré. Nicméně DTO jsou často privátně vloženy do service tříd (např. v `EventService`), což ztěžuje unit testy a opětovné použití. Doporučit přesun kritické logiky/DTO do testovatelného core projektu. (`EventService.cs`, `README.md`)
Async patterns a chybějící await

Spousta UI handlerů používá `async void` (běžné pro události) — ujistit se, že všechny možné výjimky jsou ošetřené (mnoho try/catch existuje, ale někde může být unhandled). Některé stránky používají `_appeared/_loadRequested` vzor správně. `EventPage.xaml.cs`, `CouplePage.xaml.cs`
Testovatelnost a separace logiky

Testy jsou prozatím placeholdery; doporučit extrahovat čistou logiku (DateHelpers, HTML parsing, shody registrací) do `TkOlympApp.Core` nebo podobného projektu pro unit testy. `README.md`
Logging / telemetrie

Používáte `Debug.WriteLine` místy; zvážit centralizované logování (`ILogger`) a chytrou telemetrii/chyby pro produkční nasazení. `MauiProgram` má debug logging v DEBUG only. `MauiProgram.cs`

2. (Částečně vyřešeno) Přestat spoléhat na porovnávání jmen tam, kde je dostupné `person.id`. Viz změna v `Pages/EditRegistrationsPage.xaml.cs`.
3. Extrahovat business logiku/DTO do testovatelného projektu a napsat unit testy. `README.md`