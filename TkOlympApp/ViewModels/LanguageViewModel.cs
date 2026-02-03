using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.ViewModels;

public partial class LanguageViewModel : ViewModelBase
{
    private readonly IServiceProvider _services;

    public ObservableCollection<LangItem> Languages { get; } = new();

    [ObservableProperty]
    private LangItem? _selectedLanguage;

    public LanguageViewModel(IServiceProvider services)
    {
        _services = services ?? throw new ArgumentNullException(nameof(services));
        LoadLanguages();
    }

    partial void OnSelectedLanguageChanged(LangItem? value)
    {
        if (value == null) return;
        _ = ApplyLanguageAsync(value);
    }

    private void LoadLanguages()
    {
        Languages.Clear();
        var stored = LocalizationService.GetStoredLanguage() ?? LocalizationService.DetermineDefaultLanguage();

        Languages.Add(new LangItem { Code = "cs", Name = LocalizationService.Get("Language_Czech") ?? "Čeština", Flag = "🇨🇿", IsCurrent = stored == "cs" });
        Languages.Add(new LangItem { Code = "en", Name = LocalizationService.Get("Language_English") ?? "English", Flag = "🇬🇧", IsCurrent = stored == "en" });
        Languages.Add(new LangItem { Code = "uk", Name = LocalizationService.Get("Language_Ukrainian") ?? "Українська", Flag = "🇺🇦", IsCurrent = stored == "uk" });
        Languages.Add(new LangItem { Code = "vi", Name = LocalizationService.Get("Language_Vietnamese") ?? "Tiếng Việt", Flag = "🇻🇳", IsCurrent = stored == "vi" });
        Languages.Add(new LangItem { Code = "no", Name = LocalizationService.Get("Language_Norwegian") ?? "Norsk", Flag = "🇳🇴", IsCurrent = stored == "no" });
        Languages.Add(new LangItem { Code = "sk", Name = LocalizationService.Get("Language_Slovak") ?? "Slovenčina", Flag = "🇸🇰", IsCurrent = stored == "sk" });
        Languages.Add(new LangItem { Code = "sl", Name = LocalizationService.Get("Language_Slovenian") ?? "Slovenščina", Flag = "🇸🇮", IsCurrent = stored == "sl" });
        Languages.Add(new LangItem { Code = "en-AU", Name = LocalizationService.Get("Language_en-AU") ?? "Brainrot", Flag = "🧠", IsCurrent = stored == "en-AU" });
    }

    private async Task ApplyLanguageAsync(LangItem item)
    {
        try
        {
            LocalizationService.ApplyLanguage(item.Code);
            await Application.Current?.MainPage?.DisplayAlert(
                LocalizationService.Get("Language_Title") ?? "Language",
                LocalizationService.Get("Language_Choose") ?? "Language changed",
                LocalizationService.Get("Button_OK") ?? "OK");

            foreach (var lang in Languages)
                lang.IsCurrent = lang.Code == item.Code;

            var win = Application.Current?.Windows?.FirstOrDefault();
            if (win != null)
                win.Page = _services.GetRequiredService<TkOlympApp.AppShell>();
            else
                try { await Shell.Current.GoToAsync(".."); } catch { }
        }
        catch
        {
            // ignore
        }
        finally
        {
            SelectedLanguage = null;
        }
    }

    public sealed partial class LangItem : ObservableObject
    {
        public string Code { get; init; } = string.Empty;
        public string Name { get; init; } = string.Empty;
        public string Flag { get; init; } = string.Empty;

        [ObservableProperty]
        private bool _isCurrent;
    }
}
