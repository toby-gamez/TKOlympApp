# TkOlympApp.Tests

Test project pro TkOlympApp.

## Poznámky k testování MAUI aplikací

Testování .NET MAUI aplikací je náročnější než běžných .NET projektů:

1. **Target Framework Incompatibility**: MAUI projekty používají `net10.0-android` / `net10.0-ios`, což není kompatibilní s běžným `net10.0` test projektem.

2. **Možná řešení**:
   - **Shared Library Pattern**: Extrahovat business logiku (Helpers, Services bez MAUI závislostí) do samostatné knihovny s `net10.0` target frameworkem
   - **MAUI Testing Frameworks**: Použít `xunit.runner.devices` nebo `Appium` pro UI a integrační testy
   - **Manual Testing**: Pro MAUI-specifické komponenty (Converters, XAML UI) provádět ruční testování

## Současný stav

Tento projekt je nastaven jako infrastruktura pro budoucí testy. Pro okamžité použití:

1. Extrahujte non-MAUI logiku do `TkOlympApp.Core` projektu
2. Reference `TkOlympApp.Core` v test projektu
3. Pište unit testy pro čistou business logiku

## Spuštění testů

```bash
dotnet test
```

## Roadmap

- [ ] Extrahovat `Helpers` (bez MAUI závislostí) do Core library
- [ ] Napsat unit testy pro `DateHelpers`, `PhoneHelpers`, atd.
- [ ] Nastavit xunit.runner.devices pro UI testy
- [ ] Přidat integrační testy pro Services
