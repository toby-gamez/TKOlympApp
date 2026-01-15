using Microsoft.Maui.Controls;
using System.Diagnostics;
using System;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class MainPage : ContentPage
{
    public MainPage()
    {
        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            try { Debug.WriteLine($"MainPage: InitializeComponent failed: {ex}"); } catch { }
            Content = new Microsoft.Maui.Controls.StackLayout
            {
                Children =
                {
                    new Microsoft.Maui.Controls.Label { Text = LocalizationService.Get("Error_Loading_Prefix") + ex.Message }
                }
            };
            return;
        }
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        try { Debug.WriteLine("MainPage: OnAppearing"); } catch { }

        // Initialization for notifications (moved from old calendar logic)
        try
        {
            Dispatcher.Dispatch(async () =>
            {
                try
                {
                    // Schedule notifications for upcoming events (1 hour and 5 minutes before)
                    // Load events 2 days ahead for notifications
                    var notifStart = DateTime.Now.Date;
                    var notifEnd = DateTime.Now.Date.AddDays(2).AddHours(23).AddMinutes(59);
                    var notifEvents = await EventService.GetMyEventInstancesForRangeAsync(notifStart, notifEnd);

                    // Check for changes and cancellations first
                    await EventNotificationService.CheckAndNotifyChangesAsync(notifEvents);

                    // Then schedule upcoming notifications
                    await EventNotificationService.ScheduleNotificationsForEventsAsync(notifEvents);
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"MainPage: Failed to schedule notifications: {ex}");
                }
            });
        }
        catch
        {
            // fallback if dispatch isn't available
        }
    }
}
