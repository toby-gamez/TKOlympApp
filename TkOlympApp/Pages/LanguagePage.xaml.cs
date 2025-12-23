using System.Collections.Generic;
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
            new LangItem { Code = "cs", Name = LocalizationService.Get("Language_Czech"), Flag = "🇨🇿", IsCurrent = stored == "cs" },
            new LangItem { Code = "en", Name = LocalizationService.Get("Language_English"), Flag = "🇬🇧", IsCurrent = stored == "en" },
            new LangItem { Code = "uk", Name = LocalizationService.Get("Language_Ukrainian"), Flag = "🇺🇦", IsCurrent = stored == "uk" },
            new LangItem { Code = "vi", Name = LocalizationService.Get("Language_Vietnamese"), Flag = "🇻🇳", IsCurrent = stored == "vi" }
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
            await DisplayAlert(LocalizationService.Get("Language_Title") ?? "Language", LocalizationService.Get("Language_Choose") ?? "Language changed", LocalizationService.Get("Button_OK") ?? "OK");

            // Recreate Shell/MainPage so XAML markup extensions re-evaluate with new culture
            try
            {
                Application.Current.MainPage = new AppShell();
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
