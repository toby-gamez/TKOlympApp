using System;
using System.Globalization;
using System.Resources;
using Microsoft.Maui.Storage;
using TkOlympApp.Helpers;

namespace TkOlympApp.Services;

public static class LocalizationService
{
    static readonly ResourceManager RM = new ResourceManager("TkOlympApp.Resources.Strings", typeof(LocalizationService).Assembly);

    public static readonly string[] Supported = new[] { "cs", "en", "vi", "uk", "no", "sk", "sl", "brainrot" };

    public static string? GetStoredLanguage() => Preferences.Get(AppConstants.AppLanguageKey, (string?)null);

    public static string DetermineDefaultLanguage()
    {
        var sys = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName;
        if (Array.IndexOf(Supported, sys) >= 0) return sys;
        return "en";
    }

        public static void ApplyLanguage(string lang)
        {
            if (string.IsNullOrEmpty(lang)) return;
            var culture = lang switch
            {
                "cs" => new CultureInfo("cs"),
                "en" => new CultureInfo("en"),
                "vi" => new CultureInfo("vi"),
                "uk" => new CultureInfo("uk"),
                "no" => new CultureInfo("no"),
                "sk" => new CultureInfo("sk"),
                "sl" => new CultureInfo("sl"),
                "en-AU" => new CultureInfo("en-AU"),
                _ => new CultureInfo("en")
            };

            CultureInfo.DefaultThreadCurrentCulture = culture;
            CultureInfo.DefaultThreadCurrentUICulture = culture;
            Preferences.Set(AppConstants.AppLanguageKey, lang);
        }

    public static string Get(string key)
    {
        try
        {
            var s = RM.GetString(key, CultureInfo.CurrentUICulture);
            return string.IsNullOrEmpty(s) ? key : s;
        }
        catch
        {
            return key;
        }
    }
}
