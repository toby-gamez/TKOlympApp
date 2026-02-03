using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class LanguageViewModel : ViewModelBase
{
    private readonly IServiceProvider _services;
    private readonly IUserNotifier _notifier;

    public ObservableCollection<LanguageItem> Languages { get; } = new();

    [ObservableProperty]
    private LanguageItem? _selectedLanguage;

    public LanguageViewModel(IServiceProvider services, IUserNotifier notifier)
    {
        _services = services ?? throw new ArgumentNullException(nameof(services));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
        LoadLanguages();
    }

    partial void OnSelectedLanguageChanged(LanguageItem? value)
    {
        if (value == null) return;
        _ = ApplyLanguageAsync(value);
    }

    private void LoadLanguages()
    {
        Languages.Clear();
        var stored = LocalizationService.GetStoredLanguage() ?? LocalizationService.DetermineDefaultLanguage();

        Languages.Add(new LanguageItem { Code = "cs", Name = LocalizationService.Get("Language_Czech") ?? "Čeština", Flag = "🇨🇿", IsCurrent = stored == "cs" });
        Languages.Add(new LanguageItem { Code = "en", Name = LocalizationService.Get("Language_English") ?? "English", Flag = "🇬🇧", IsCurrent = stored == "en" });
        Languages.Add(new LanguageItem { Code = "uk", Name = LocalizationService.Get("Language_Ukrainian") ?? "Українська", Flag = "🇺🇦", IsCurrent = stored == "uk" });
        Languages.Add(new LanguageItem { Code = "vi", Name = LocalizationService.Get("Language_Vietnamese") ?? "Tiếng Việt", Flag = "🇻🇳", IsCurrent = stored == "vi" });
        Languages.Add(new LanguageItem { Code = "no", Name = LocalizationService.Get("Language_Norwegian") ?? "Norsk", Flag = "🇳🇴", IsCurrent = stored == "no" });
        Languages.Add(new LanguageItem { Code = "sk", Name = LocalizationService.Get("Language_Slovak") ?? "Slovenčina", Flag = "🇸🇰", IsCurrent = stored == "sk" });
        Languages.Add(new LanguageItem { Code = "sl", Name = LocalizationService.Get("Language_Slovenian") ?? "Slovenščina", Flag = "🇸🇮", IsCurrent = stored == "sl" });
        Languages.Add(new LanguageItem { Code = "en-AU", Name = LocalizationService.Get("Language_en-AU") ?? "Brainrot", Flag = "🧠", IsCurrent = stored == "en-AU" });
    }

    private async Task ApplyLanguageAsync(LanguageItem item)
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
                try { await Shell.Current.GoToAsync(".."); }
                catch (Exception ex)
                {
                    LoggerService.SafeLogWarning<LanguageViewModel>("Failed to navigate back after language change: {0}", new object[] { ex.Message });
                }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<LanguageViewModel>("Failed to apply language: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<LanguageViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
        finally
        {
            SelectedLanguage = null;
        }
    }

}
