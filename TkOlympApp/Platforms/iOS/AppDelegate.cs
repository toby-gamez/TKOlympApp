using Foundation;
using UIKit;
using System;
using System.Threading.Tasks;
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
        catch { }
        return base.FinishedLaunching(app, options);
    }

    public override void PerformFetch(UIApplication application, Action<UIBackgroundFetchResult> completionHandler)
    {
        // Run background fetch asynchronously and call completionHandler when done
        Task.Run(async () =>
        {
            try
            {
                // Ensure auth initialized (loads JWT into HttpClient if present)
                await AuthService.InitializeAsync();

                // Fetch events for the next 2 days (same range used elsewhere)
                var start = DateTime.Now;
                var end = DateTime.Now.AddDays(2);
                var events = await EventService.GetMyEventInstancesForRangeAsync(start, end);

                // Let the notification service check for changes and send local notifications if needed
                await EventNotificationService.CheckAndNotifyChangesAsync(events);

                completionHandler(UIBackgroundFetchResult.NewData);
            }
            catch
            {
                completionHandler(UIBackgroundFetchResult.Failed);
            }
        });
    }
}