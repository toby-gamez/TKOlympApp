# TkOlympApp.Tests

Test project pro TkOlympApp.

## Poznámky k testování MAUI aplikací

Testování .NET MAUI aplikací je náročnější než běžných .NET projektů:

1. **Target Framework Incompatibility**: MAUI projekty používají `net10.0-android` / `net10.0-ios`, což není kompatibilní s běžným `net10.0` test projektem.

2. **Možná řešení**:
   - **Shared Library Pattern**: Extrahovat business logiku (Helpers, Services bez MAUI závislostí) do samostatné knihovny s `net10.0` target frameworkem
   - **Source Linking**: Linkovat zdrojové soubory bez MAUI závislostí pomocí `<Compile Include="..." Link="..." />`
   - **MAUI Testing Frameworks**: Použít `xunit.runner.devices` nebo `Appium` pro UI a integrační testy
   - **Manual Testing**: Pro MAUI-specifické komponenty (Converters, XAML UI) provádět ruční testování

## Současný stav

✅ **Implementované testy (132 testů celkem):**
- `PhoneHelpersTests` (17 testů) - formátování telefonních čísel
- `PostalCodeHelpersTests` (10 testů) - formátování PSČ
- `DateHelpersTests` (11 testů) - formátování dat a času
- `NationalityHelperTests` (36 testů) - mapování kódů zemí, lokalizované přídavné jméno
- `CohortColorHelperTests` (58 testů) - parsování barev z JSON, RGB/hex formáty

📦 **Testovací infrastruktura:**
- Source linking pro pure helper metody (PhoneHelpers, PostalCodeHelpers, DateHelpers, NationalityHelper, CohortColorHelper)
- Mock LocalizationService pro DateHelpers testy
- Microsoft.Maui.Controls pro testování MAUI-dependentních metod (CohortColorHelper)
- FluentAssertions pro expresivní asserty

## Pokrytí

| Soubor | Testů | Status | Poznámka |
|--------|-------|--------|----------|
| PhoneHelpers | 17 | ✅ | Kompletní pokrytí |
| PostalCodeHelpers | 10 | ✅ | Kompletní pokrytí |
| DateHelpers | 11 | ✅ | Bez testů pro "dnes"/"zítra" (vyžadují time provider) |
| NationalityHelper | 36 | ✅ | Pokrývá všechny metody a lokalizace |
| CohortColorHelper | 58 | ✅ | Kompletní pokrytí včetně JSON parsing |
| HtmlHelpers | 0 | ⏳ | Vyžaduje rozsáhlejší MAUI mock |
| FirstRunHelper | 0 | ⏳ | Závisí na SecureStorage |

## Spuštění testů

```bash
dotnet test
```

Nebo s detailnějším výstupem:

```bash
dotnet test --verbosity normal
```

## Roadmap

- [x] Napsat unit testy pro `PhoneHelpers` a `PostalCodeHelpers`
- [x] Napsat základní testy pro `DateHelpers`
- [x] Napsat komplexní testy pro `NationalityHelper` (všechny metody + lokalizace)
- [x] Napsat komplexní testy pro `CohortColorHelper` (JSON parsing, barevné formáty)
- [ ] Přidat testy pro HtmlHelpers (vyžaduje rozsáhlejší MAUI mockování)
- [ ] Extrahovat `Helpers` (bez MAUI závislostí) do Core library pro lepší testovatelnost
- [ ] Nastavit xunit.runner.devices pro UI testy
- [ ] Přidat integrační testy pro Services (vyžaduje mockování HttpClient)
- [ ] Zvážit CI/CD pipeline s automatickým spouštěním testů
- [ ] Přidat code coverage reporting

## Struktura
          # Globální using direktivy (Xunit, FluentAssertions)
├── Helpers/                           # Testy pro helper třídy (132 testů)
│   ├── DateHelpersTests.cs           # 11 testů
│   ├── PhoneHelpersTests.cs          # 17 testů
│   ├── PostalCodeHelpersTests.cs     # 10 testů
│   ├── NationalityHelperTests.cs     # 36 testů
│   └── CohortColorHelperTests.cs     # 58 testů
└── Mocks/          teHelpersTests.cs
│   ├── PhoneHelpersTests.cs
│   └── PostalCodeHelpersTests.cs
└── Mocks/                   # Mock objekty pro testování
    └── MockLocalizationService.cs
```

