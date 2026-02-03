using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class ChangePasswordViewModel : ViewModelBase
{
    private readonly IAuthService _authService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    [ObservableProperty]
    private string _newPassword = string.Empty;

    [ObservableProperty]
    private string _confirmPassword = string.Empty;

    [ObservableProperty]
    private string _lengthText = string.Empty;

    [ObservableProperty]
    private string _matchText = string.Empty;

    [ObservableProperty]
    private Brush _newPassStroke = GetBrush("Gray300Brush", Brush.Default);

    [ObservableProperty]
    private double _newPassStrokeThickness = 1;

    [ObservableProperty]
    private Brush _confirmPassStroke = GetBrush("Gray300Brush", Brush.Default);

    [ObservableProperty]
    private double _confirmPassStrokeThickness = 1;

    [ObservableProperty]
    private string _errorText = string.Empty;

    [ObservableProperty]
    private bool _isErrorVisible;

    public ChangePasswordViewModel(IAuthService authService, INavigationService navigationService, IUserNotifier notifier)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));

        UpdateFeedback();
    }

    partial void OnNewPasswordChanged(string value)
    {
        UpdateFeedback();
    }

    partial void OnConfirmPasswordChanged(string value)
    {
        UpdateFeedback();
    }

    [RelayCommand]
    private async Task CancelAsync()
    {
        try { await Shell.Current.Navigation.PopModalAsync(); }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<ChangePasswordViewModel>("Failed to close change password modal: {0}", new object[] { ex.Message });
        }
    }

    [RelayCommand]
    private async Task ChangeAsync()
    {
        try
        {
            IsErrorVisible = false;
            ErrorText = string.Empty;

            if (string.IsNullOrWhiteSpace(NewPassword))
            {
                ErrorText = LocalizationService.Get("ChangePassword_EnterNew") ?? "Zadejte nové heslo.";
                IsErrorVisible = true;
                return;
            }

            if (NewPassword != ConfirmPassword)
            {
                ErrorText = LocalizationService.Get("ChangePassword_Error_Mismatch") ?? "Hesla se neshodují.";
                IsErrorVisible = true;
                return;
            }

            await _authService.ChangePasswordAsync(NewPassword);

            try { await Shell.Current.Navigation.PopModalAsync(); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<ChangePasswordViewModel>("Failed to close change password modal after success: {0}", new object[] { ex.Message });
            }

            try
            {
                await _authService.LogoutAsync();
                try { await _navigationService.NavigateToAsync(nameof(Pages.LoginPage)); }
                catch (Exception navEx)
                {
                    LoggerService.SafeLogWarning<ChangePasswordViewModel>("Login navigation failed: {0}", new object[] { navEx.Message });
                    try { await Shell.Current.GoToAsync($"//{nameof(Pages.LoginPage)}"); }
                    catch (Exception shellEx)
                    {
                        LoggerService.SafeLogWarning<ChangePasswordViewModel>("Shell login navigation failed: {0}", new object[] { shellEx.Message });
                    }
                }
            }
            catch
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("ChangePassword_Success_Title") ?? "Hotovo",
                    LocalizationService.Get("ChangePassword_Success_LoggedOut_Message") ?? "Heslo bylo úspěšně změněno. Proběhlo automatické odhlášení.",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
        }
        catch (Exception ex)
        {
            ErrorText = ex.Message;
            IsErrorVisible = true;
        }
    }

    private void UpdateFeedback()
    {
        var newText = NewPassword ?? string.Empty;
        var confirm = ConfirmPassword ?? string.Empty;

        if (newText.Length >= 8)
        {
            LengthText = string.Format(LocalizationService.Get("ChangePassword_Length_OK_Format") ?? "Délka: {0}/8 (OK)", newText.Length);
            NewPassStroke = GetBrush("SuccessBrush", Brush.Default);
            NewPassStrokeThickness = 2;
        }
        else
        {
            LengthText = string.Format(LocalizationService.Get("ChangePassword_Length_Format") ?? "Délka: {0}/8", newText.Length);
            NewPassStroke = GetBrush("Gray300Brush", Brush.Default);
            NewPassStrokeThickness = 1;
        }

        if (string.IsNullOrEmpty(confirm))
        {
            MatchText = LocalizationService.Get("ChangePassword_Match_None") ?? "Hesla: -";
            ConfirmPassStroke = GetBrush("Gray300Brush", Brush.Default);
            ConfirmPassStrokeThickness = 1;
        }
        else if (confirm == newText)
        {
            MatchText = LocalizationService.Get("ChangePassword_Match_OK") ?? "Hesla se shodují";
            ConfirmPassStroke = GetBrush("SuccessBrush", Brush.Default);
            ConfirmPassStrokeThickness = 2;
            if (newText.Length >= 8)
            {
                NewPassStroke = GetBrush("SuccessBrush", Brush.Default);
                NewPassStrokeThickness = 2;
            }
        }
        else
        {
            MatchText = LocalizationService.Get("ChangePassword_Match_MISMATCH") ?? "Hesla se neshodují";
            ConfirmPassStroke = GetBrush("DangerBrush", Brush.Default);
            ConfirmPassStrokeThickness = 2;
        }
    }

    private static Brush GetBrush(string resourceKey, Brush fallback)
    {
        try
        {
            if (Application.Current?.Resources.TryGetValue(resourceKey, out var value) == true && value is Brush brush)
            {
                return brush;
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<ChangePasswordViewModel>("Failed to resolve brush: {0}", new object[] { ex.Message });
        }

        return fallback;
    }

}
