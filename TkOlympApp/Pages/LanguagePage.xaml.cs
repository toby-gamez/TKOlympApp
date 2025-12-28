using System.Collections.Generic;
using System.Linq;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class LanguagePage : ContentPage
{
    class LangItem
    {
        public string Code { get; init; } = "";
        public string Name { get; init; } = "";
        public string Flag { get; init; } = "";
        public bool IsCurrent { get; set; }
    }

    public LanguagePage()
    {
        InitializeComponent();

        var stored = LocalizationService.GetStoredLanguage() ?? LocalizationService.DetermineDefaultLanguage();

        var items = new List<LangItem>
        {
            new LangItem { Code = "cs", Name = LocalizationService.Get("Language_Czech") ?? "Čeština", Flag = "🇨🇿", IsCurrent = stored == "cs" },
            new LangItem { Code = "en", Name = LocalizationService.Get("Language_English") ?? "English", Flag = "🇬🇧", IsCurrent = stored == "en" },
            new LangItem { Code = "uk", Name = LocalizationService.Get("Language_Ukrainian") ?? "Українська", Flag = "🇺🇦", IsCurrent = stored == "uk" },
            new LangItem { Code = "vi", Name = LocalizationService.Get("Language_Vietnamese") ?? "Tiếng Việt", Flag = "🇻🇳", IsCurrent = stored == "vi" },
            new LangItem { Code = "no", Name = LocalizationService.Get("Language_Norwegian") ?? "Norsk", Flag = "🇳🇴", IsCurrent = stored == "no" },
            new LangItem { Code = "sk", Name = LocalizationService.Get("Language_Slovak") ?? "Slovenčina", Flag = "🇸🇰", IsCurrent = stored == "sk" },
            new LangItem { Code = "sl", Name = LocalizationService.Get("Language_Slovenian") ?? "Slovenščina", Flag = "🇸🇮", IsCurrent = stored == "sl" },
            new LangItem { Code = "en-AU", Name = LocalizationService.Get("Language_en-AU") ?? "Brainrot", Flag = "🧠", IsCurrent = stored == "en-AU" }
        };

        LangList.ItemsSource = items;
    }

    private async void OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (e.CurrentSelection == null || e.CurrentSelection.Count == 0) return;
        if (e.CurrentSelection[0] is not LangItem it) return;

            try
            {
                LocalizationService.ApplyLanguage(it.Code);
                await DisplayAlertAsync(LocalizationService.Get("Language_Title") ?? "Language", LocalizationService.Get("Language_Choose") ?? "Language changed", LocalizationService.Get("Button_OK") ?? "OK");

                // Recreate Shell/MainPage so XAML markup extensions re-evaluate with new culture
                try
                {
                    var win = Application.Current?.Windows?.FirstOrDefault();
                    if (win != null)
                        win.Page = new AppShell();
                    else
                        try { await Shell.Current.GoToAsync(".."); } catch { /* ignore */ }
                }
                catch
                {
                    // fallback: try navigate back
                    try { await Shell.Current.GoToAsync(".."); } catch { /* ignore */ }
                }
            }
        catch
        {
            // ignore
        }
    }
}
