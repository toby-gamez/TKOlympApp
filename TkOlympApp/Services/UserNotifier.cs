using System;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services
{
    public sealed class UserNotifier : IUserNotifier
    {
        public async Task ShowAsync(string title, string message, string cancel = "OK")
        {
            try
            {
                if (Application.Current?.MainPage == null)
                {
                    return;
                }

                await MainThread.InvokeOnMainThreadAsync(async () =>
                {
                    await Application.Current.MainPage.DisplayAlert(title ?? string.Empty, message ?? string.Empty, cancel);
                });
            }
            catch (Exception ex)
            {
                try { LoggerService.SafeLogError<UserNotifier>(ex, "UserNotifier failed to show alert"); }
                catch (Exception logEx)
                {
                    System.Diagnostics.Debug.WriteLine($"UserNotifier logging failed: {logEx}");
                }
            }
        }
    }
}
