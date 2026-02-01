namespace TkOlympApp.Services;

/// <summary>
/// Mock LocalizationService pro unit testy.
/// Reálný LocalizationService závisí na Microsoft.Maui.Storage.
/// </summary>
public static class LocalizationService
{
    private static readonly Dictionary<string, string> MockStrings = new()
    {
        { "Date_Today_Prefix", "dnes " },
        { "Date_Tomorrow_Prefix", "zítra " }
    };

    public static string? Get(string key)
    {
        return MockStrings.TryGetValue(key, out var value) ? value : key;
    }
}
