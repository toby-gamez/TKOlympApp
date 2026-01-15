using Android.Content;
using AndroidX.Work;
using System;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp.Platforms.Android;

public class EventChangeCheckWorker : Worker
{
    public EventChangeCheckWorker(Context context, WorkerParameters workerParams) 
        : base(context, workerParams)
    {
    }

    public override Result DoWork()
    {
        try
        {
            System.Diagnostics.Debug.WriteLine("EventChangeCheckWorker: Starting background check");

            // Run async work synchronously in background thread
            Task.Run(async () =>
            {
                try
                {
                    // Check if user is authenticated
                    var hasToken = await AuthService.HasTokenAsync();
                    if (!hasToken)
                    {
                        System.Diagnostics.Debug.WriteLine("EventChangeCheckWorker: No auth token, skipping");
                        return;
                    }

                    // Load events for next 2 days
                    var start = DateTime.Now.Date;
                    var end = DateTime.Now.Date.AddDays(2).AddHours(23).AddMinutes(59);
                    var events = await EventService.GetMyEventInstancesForRangeAsync(start, end);

                    // Check for changes and send notifications
                    await EventNotificationService.CheckAndNotifyChangesAsync(events);

                    // Also reschedule upcoming notifications
                    await EventNotificationService.ScheduleNotificationsForEventsAsync(events);

                    System.Diagnostics.Debug.WriteLine($"EventChangeCheckWorker: Completed check for {events.Count} events");
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"EventChangeCheckWorker: Error in async work: {ex}");
                }
            }).Wait();

            return Result.InvokeSuccess();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"EventChangeCheckWorker: Worker failed: {ex}");
            return Result.InvokeFailure();
        }
    }
}
