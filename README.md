# TK Olymp aplikace

## Krátký přehled projektu
- **TkOlympApp** je klientská .NET MAUI aplikace (mobilní) sloužící jako klient tkolymp.cz. Aplikace zobrazuje události, žebříčky, nástěnku a detailní stránky událostí.

## Použité technologie
- Platforma: .NET MAUI (multi-target: Android, iOS, v budoucnu - Mac OS a Windows)
- Jazyk: C# + XAML
- Síť: GraphQL dotazy přes `HttpClient` (instance-based služby přes DI v `TkOlympApp/Services/`)
- Serializace: `System.Text.Json` s `JsonSerializerDefaults.Web`

## Hlavní funkce
- Autentizace a ukládání JWT do `SecureStorage` (viz `TkOlympApp/Services/AuthService.cs`).
- Načítání a zobrazení údálostí pomocí GraphQL (viz `TkOlympApp/Services/EventService.cs`).
- Nástěnka a oznámení (viz `TkOlympApp/Services/NoticeboardService.cs`).
- Žebříčky (viz `TkOlympApp/Services/LeaderboardService.cs`).
- Uživatelský profil (viz `TkOlympApp/Services/UserService.cs`).

## Struktura projektu (vybrané soubory)
- `TkOlympApp/MauiProgram.cs` – konfigurace aplikace a registrace fontů/ikon.
- `TkOlympApp/AppShell.xaml.cs` – routing a navigace (Shell + URI query parameters).
- `TkOlympApp/Pages/` – stránky aplikace (např. `MainPage`, `EventPage`, `LeaderboardPage`, `NoticeboardPage`, `LoginPage`).
- `TkOlympApp/Services/` – instance-based služby pro síťovou komunikaci a business logiku (typicky `I*Service` + `*Implementation`, např. `AuthServiceImplementation`, `EventServiceImplementation`).
- `TkOlympApp/Helpers/` – pomocné třídy (např. `HtmlHelpers`, `DateHelpers`).
- `TkOlympApp/Converters/` – XAML konvertory pro vazbu dat.

## Důležité konvence
- Aplikace používá Dependency Injection (DI) přes `MauiProgram.CreateMauiApp()` (`builder.Services`). Nové služby přidávej jako instance-based (`I*Service` + `*Implementation`) a registruj do DI.
- GraphQL dotazy a odpovědní DTO často definovány jako interní privátní třídy/recordy uvnitř konkrétní service třídy.
- `JsonSerializerOptions(JsonSerializerDefaults.Web)` se používá pro konzistentní (de)serializaci.
- Uživatelské texty a chybová hlášení držíme v češtině.

## Dependency Injection (DI)

### Kde se DI konfiguruje

- Registrace probíhá v `TkOlympApp/MauiProgram.cs` pomocí `builder.Services`.
- HTTP komunikace používá `IHttpClientFactory` + typed clients (např. `IAuthService`, `IGraphQlClient`).
- Pages jsou registrované v DI (`AddTransient<SomePage>()`) – umožní to constructor injection.

### Jak přidat novou službu

1. Přidej rozhraní do `TkOlympApp/Services/Abstractions/` (např. `IFooService`).
2. Implementaci dej do `TkOlympApp/Services/` (např. `FooServiceImplementation`).
3. Zaregistruj do DI v `TkOlympApp/MauiProgram.cs`.

Příklad registrace:

```csharp
builder.Services.AddTransient<IFooService, FooServiceImplementation>();
```

### Jak udělat Page DI-friendly (constructor injection)

- Page zaregistruj do DI:

```csharp
builder.Services.AddTransient<FooPage>();
```

- V code-behind přidej konstruktor se závislostmi:

```csharp
public partial class FooPage : ContentPage
{
	private readonly IFooService _foo;

	public FooPage(IFooService foo)
	{
		InitializeComponent();
		_foo = foo;
	}
}
```

### Shell routing + DI

Shell navigace běží přes routy (např. `EventPage?id=123`). Pokud chceš routovanou page vytvářet přes DI (kvůli constructor injection), registruj ji do DI a použij route factory, který resolvuje page z containeru (v projektu je připravený helper `DiRouteFactory`).

Pozn.: Query parametry pro routy řeš standardně přes `[QueryProperty]` nebo zpracování `IQueryAttributable` v page.

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

## Kde hledat další příklady
- Přehled služeb: `TkOlympApp/Services/*.cs`
- Stránky a navigace: `TkOlympApp/Pages/*` a `TkOlympApp/AppShell.xaml.cs`
- Pomocné utility: `TkOlympApp/Helpers/*`, `TkOlympApp/Converters/*`

## Poznámka
- README je záměrně zaměřené na popis projektu a použitých funkcí. Kontakty, informace o AI nebo jiné externí reference nejsou v tomto dokumentu zahrnuty.


## License
This project is licensed under the MIT License.
© 2026 Tobias Heneman