using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class LoginViewModel : ViewModelBase
{
    private readonly IAuthService _authService;
    private readonly IUserNotifier _notifier;

    [ObservableProperty]
    private string _username = string.Empty;

    [ObservableProperty]
    private string _password = string.Empty;

    public LoginViewModel(IAuthService authService, IUserNotifier notifier)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    [RelayCommand]
    private async Task LoginAsync()
    {
        var username = Username?.Trim() ?? string.Empty;
        var password = Password ?? string.Empty;

        if (string.IsNullOrEmpty(username) || string.IsNullOrEmpty(password))
        {
            await _notifier.ShowAsync(
                LocalizationService.Get("Error_Title") ?? "Error",
                LocalizationService.Get("Login_Error_Missing") ?? "Zadejte přihlašovací údaje.",
                LocalizationService.Get("Button_OK") ?? "OK");
            return;
        }

        try
        {
            await _authService.LoginAsync(username, password);
            await Shell.Current.GoToAsync("//Přehled");
        }
        catch (Exception ex)
        {
            await _notifier.ShowAsync(
                LocalizationService.Get("Login_Failed_Title") ?? "Login failed",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }
}
