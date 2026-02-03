using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.ViewModels;

public partial class PlainTextViewModel : ViewModelBase
{
    [ObservableProperty]
    private string _plainText = string.Empty;

    public override Task OnAppearingAsync()
    {
        if (string.IsNullOrEmpty(PlainText) && !string.IsNullOrEmpty(PlainTextService.LastText))
        {
            PlainText = PlainTextService.LastText ?? string.Empty;
            PlainTextService.LastText = null;
        }
        return base.OnAppearingAsync();
    }

    [RelayCommand]
    private async Task CopyAsync()
    {
        try
        {
            if (!string.IsNullOrEmpty(PlainText))
            {
                await Clipboard.SetTextAsync(PlainText);
                await Application.Current?.MainPage?.DisplayAlert(
                    LocalizationService.Get("PlainText_Copied_Title") ?? "Copied",
                    LocalizationService.Get("PlainText_Copied_Body") ?? "Text copied to clipboard",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
        }
        catch (Exception ex)
        {
            await Application.Current?.MainPage?.DisplayAlert(
                LocalizationService.Get("Error_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }
}
