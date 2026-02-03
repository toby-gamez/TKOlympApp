using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class OtherViewModel : ViewModelBase
{
    private readonly IAuthService _authService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    public OtherViewModel(IAuthService authService, INavigationService navigationService, IUserNotifier notifier)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    [RelayCommand]
    private async Task LogoutAsync()
    {
        try
        {
            await _authService.LogoutAsync();
            await _navigationService.NavigateToAsync($"//{nameof(Pages.LoginPage)}");
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<OtherViewModel>("Logout failed: {0}", new object[] { ex.Message });
            try
            {
                await _navigationService.NavigateToAsync(nameof(Pages.LoginPage));
            }
            catch (Exception navEx)
            {
                LoggerService.SafeLogWarning<OtherViewModel>("Fallback navigation failed: {0}", new object[] { navEx.Message });
            }

            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<OtherViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    [RelayCommand]
    private Task AboutMeAsync() => NavigateAsync(nameof(Pages.AboutMePage));

    [RelayCommand]
    private Task TrainersAsync() => NavigateAsync(nameof(Pages.TrainersAndLocationsPage));

    [RelayCommand]
    private Task PeopleAsync() => NavigateAsync(nameof(Pages.PeoplePage));

    [RelayCommand]
    private Task CohortGroupsAsync() => NavigateAsync(nameof(Pages.CohortGroupsPage));

    [RelayCommand]
    private Task LeaderboardAsync() => NavigateAsync(nameof(Pages.LeaderboardPage));

    [RelayCommand]
    private Task LanguagesAsync() => NavigateAsync(nameof(Pages.LanguagePage));

    [RelayCommand]
    private Task AboutAppAsync() => NavigateAsync(nameof(Pages.AboutAppPage));

    [RelayCommand]
    private Task PrivacyPolicyAsync() => NavigateAsync(nameof(Pages.PrivacyPolicyPage));

    [RelayCommand]
    private Task NotificationSettingsAsync() => NavigateAsync(nameof(Pages.EventNotificationSettingsPage));

    [RelayCommand]
    private Task EventsAsync() => NavigateAsync(nameof(Pages.EventsPage));

    [RelayCommand]
    private void SendTestNotification()
    {
        try
        {
            NotificationManagerService.EnsureInitialized();
            var mgr = NotificationManagerService.Instance;
            mgr?.SendNotification("Nová aktualita", "Test aktuality");
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<OtherViewModel>("Failed to send test notification: {0}", new object[] { ex.Message });
        }
    }

    private async Task NavigateAsync(string route)
    {
        try
        {
            await _navigationService.NavigateToAsync(route);
        }
        catch (Exception ex)
        {
            await _notifier.ShowAsync(
                LocalizationService.Get("Error_Navigation_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }
}
