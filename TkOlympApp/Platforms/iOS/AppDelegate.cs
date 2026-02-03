using Foundation;
using UIKit;
using System;
using System.Threading.Tasks;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.Services;


namespace TkOlympApp;

[Register("AppDelegate")]
public class AppDelegate : MauiUIApplicationDelegate
{
    protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();

    public override bool FinishedLaunching(UIApplication app, NSDictionary options)
    {
        try
        {
            // Request background fetch approximately every 30 minutes (system may adjust)
            UIApplication.SharedApplication.SetMinimumBackgroundFetchInterval(1800); // seconds
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<AppDelegate>("Failed to set background fetch interval: {0}", new object[] { ex.Message });
        }
        return base.FinishedLaunching(app, options);
    }

    public override void PerformFetch(UIApplication application, Action<UIBackgroundFetchResult> completionHandler)
    {
        // Run background fetch asynchronously and call completionHandler when done
        Task.Run(async () =>
        {
            try
            {
                var services = Services;
                var authService = services.GetRequiredService<IAuthService>();
                var eventService = services.GetRequiredService<IEventService>();
                var eventNotificationService = services.GetRequiredService<IEventNotificationService>();

                // Ensure auth initialized (loads JWT into HttpClient if present)
                await authService.InitializeAsync();

                var hasToken = await authService.HasTokenAsync();
                if (!hasToken)
                {
                    completionHandler(UIBackgroundFetchResult.NoData);
                    return;
                }

                // Fetch events for the next 2 days (same range used elsewhere)
                var start = DateTime.Now;
                var end = DateTime.Now.AddDays(2);
                var events = await eventService.GetMyEventInstancesForRangeAsync(start, end);

                // Let the notification service check for changes and send local notifications if needed
                await eventNotificationService.CheckAndNotifyChangesAsync(events);

                completionHandler(UIBackgroundFetchResult.NewData);
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<AppDelegate>("Background fetch failed: {0}", new object[] { ex.Message });
                completionHandler(UIBackgroundFetchResult.Failed);
            }
        });
    }
}