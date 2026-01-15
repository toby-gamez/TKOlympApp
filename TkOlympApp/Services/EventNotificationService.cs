using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Threading.Tasks;
using System.Text.Json;
using Microsoft.Maui.Storage;
using Plugin.LocalNotification;
using Plugin.LocalNotification.AndroidOption;

namespace TkOlympApp.Services;

public static class EventNotificationService
{
    private static readonly HashSet<int> _scheduledNotificationIds = new();
    private static DateTime _lastScheduledTime = DateTime.MinValue;
    private static bool _channelInitialized = false;

    private const string ChannelId = "tkolymp_events";
    private const string ChannelName = "Události a lekce";
    private const string ChannelDescription = "Upozornění na nadcházející události, lekce a tréninky";
    private const string EventsSnapshotKey = "events_snapshot_v1";

    private class EventSnapshot
    {
        public long Id { get; set; }
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
        public string? LocationText { get; set; }
        public string? EventName { get; set; }
        public bool IsCancelled { get; set; }
        public string? TrainersJson { get; set; }
    }

    /// <summary>
    /// Initialize notification channel (Android)
    /// </summary>
    private static void EnsureChannelInitialized()
    {
        if (_channelInitialized) return;
        
        try
        {
            // Plugin.LocalNotification handles channel creation automatically via AndroidOptions
            // We just need to use consistent ChannelId in notification requests
            _channelInitialized = true;
            Debug.WriteLine($"EventNotificationService: Using notification channel '{ChannelId}'");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Error in channel initialization: {ex}");
        }
    }

    /// <summary>
    /// Schedules notifications for upcoming events (1 hour and 5 minutes before)
    /// </summary>
    public static async Task ScheduleNotificationsForEventsAsync(List<EventService.EventInstance> events)
    {
        try
        {
            // Ensure notification channel exists (Android)
            EnsureChannelInitialized();

            // Clear old notifications if we're rescheduling (avoid duplicates)
            if ((DateTime.Now - _lastScheduledTime).TotalMinutes > 5)
            {
                await ClearAllScheduledNotificationsAsync();
            }

            _lastScheduledTime = DateTime.Now;
            int scheduledCount = 0;

            var now = DateTime.Now;
            var notificationId = 1000; // Start from 1000 to avoid conflicts

            foreach (var eventInstance in events)
            {
                if (eventInstance.Since == null || eventInstance.IsCancelled)
                    continue;

                var eventStart = eventInstance.Since.Value;
                if (eventStart <= now)
                    continue; // Skip past events

                var eventName = eventInstance.Event?.Name ?? LocalizationService.Get("Event");
                var locationText = eventInstance.Event?.LocationText;
                var eventType = eventInstance.Event?.Type;
                
                // Format event description
                var description = FormatEventDescription(eventStart, locationText);

                // Schedule 1 hour before
                var oneHourBefore = eventStart.AddHours(-1);
                if (oneHourBefore > now)
                {
                    var success = await ScheduleNotificationAsync(
                        notificationId++,
                        GetNotificationTitle(1, "hour"),
                        $"{eventName}\n{description}",
                        oneHourBefore,
                        eventInstance.Id
                    );
                    if (success) scheduledCount++;
                }

                // Schedule 5 minutes before
                var fiveMinBefore = eventStart.AddMinutes(-5);
                if (fiveMinBefore > now)
                {
                    var success = await ScheduleNotificationAsync(
                        notificationId++,
                        GetNotificationTitle(5, "minutes"),
                        $"{eventName}\n{description}",
                        fiveMinBefore,
                        eventInstance.Id
                    );
                    if (success) scheduledCount++;
                }
            }

            Debug.WriteLine($"EventNotificationService: Successfully scheduled {scheduledCount} notifications for {events.Count} events");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Error scheduling notifications: {ex}");
        }
    }

    private static string GetNotificationTitle(int time, string unit)
    {
        // Localized notification title
        if (unit == "hour")
        {
            return LocalizationService.Get("Notification_EventInHour") ?? "Za hodinu máte událost";
        }
        else
        {
            var template = LocalizationService.Get("Notification_EventInMinutes") ?? "Za {0} minut máte událost";
            return string.Format(template, time);
        }
    }

    private static string FormatEventDescription(DateTime eventStart, string? locationText)
    {
        var timeStr = eventStart.ToString("HH:mm");
        if (!string.IsNullOrWhiteSpace(locationText))
        {
            return $"{timeStr} • {locationText}";
        }
        return timeStr;
    }

    private static async Task<bool> ScheduleNotificationAsync(int notificationId, string title, string description, DateTime notifyTime, long eventId)
    {
        try
        {
            var notification = new NotificationRequest
            {
                NotificationId = notificationId,
                Title = title,
                Description = description,
                Schedule = new NotificationRequestSchedule
                {
                    NotifyTime = notifyTime,
                    // Android exact alarm for reliable delivery
                    NotifyRepeatInterval = null
                },
                ReturningData = eventId.ToString(), // Event ID for tap handling
                Android = new AndroidOptions
                {
                    ChannelId = ChannelId,
                    Priority = AndroidPriority.High,
                    AutoCancel = true,
                    VibrationPattern = new long[] { 0, 500, 200, 500 }, // Vibrate pattern
                    TimeoutAfter = TimeSpan.FromMinutes(15), // Auto-dismiss after 15 min
                    IconSmallName = new AndroidIcon("ic_notification") // Custom notification icon
                },
                CategoryType = NotificationCategoryType.Event
            };

            await LocalNotificationCenter.Current.Show(notification);
            _scheduledNotificationIds.Add(notificationId);

            var timeUntil = notifyTime - DateTime.Now;
            Debug.WriteLine($"EventNotificationService: ✓ Scheduled notification {notificationId} for {notifyTime:yyyy-MM-dd HH:mm} (in {timeUntil.TotalMinutes:F0} min)");
            return true;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: ✗ Failed to schedule notification {notificationId}: {ex.Message}");
            return false;
        }
    }

    private static Task ClearAllScheduledNotificationsAsync()
    {
        try
        {
            foreach (var id in _scheduledNotificationIds)
            {
                LocalNotificationCenter.Current.Cancel(id);
            }
            _scheduledNotificationIds.Clear();
            Debug.WriteLine("EventNotificationService: Cleared all scheduled notifications");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Error clearing notifications: {ex}");
        }
        return Task.CompletedTask;
    }

    /// <summary>
    /// Request notification permissions (call on app startup or first use)
    /// </summary>
    public static async Task<bool> RequestNotificationPermissionAsync()
    {
        try
        {
            // Ensure channel is created first
            EnsureChannelInitialized();
            
            var enabled = await LocalNotificationCenter.Current.AreNotificationsEnabled();
            if (!enabled)
            {
                var result = await LocalNotificationCenter.Current.RequestNotificationPermission();
                Debug.WriteLine($"EventNotificationService: Notification permission request result: {result}");
            }
            
            enabled = await LocalNotificationCenter.Current.AreNotificationsEnabled();
            
#if ANDROID
            // Check exact alarm permission (Android 12+)
            if (Android.OS.Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.S)
            {
                try
                {
                    var alarmManager = Android.App.AlarmManager.FromContext(Android.App.Application.Context);
                    if (alarmManager != null && !alarmManager.CanScheduleExactAlarms())
                    {
                        Debug.WriteLine("EventNotificationService: App cannot schedule exact alarms - user needs to grant permission in settings");
                        // Note: On Android 12+, user needs to manually enable exact alarms in Settings
                        // We can't request this permission directly from code
                    }
                    else
                    {
                        Debug.WriteLine("EventNotificationService: Exact alarm permission granted");
                    }
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"EventNotificationService: Error checking exact alarm permission: {ex.Message}");
                }
            }
#endif
            
            if (enabled)
            {
                Debug.WriteLine("EventNotificationService: ✓ Notifications enabled and ready");
            }
            else
            {
                Debug.WriteLine("EventNotificationService: ✗ Notifications not enabled");
            }
            
            return enabled;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Error requesting permission: {ex}");
            return false;
        }
    }

    /// <summary>
    /// Get diagnostics info about notification system state
    /// </summary>
    public static async Task<string> GetDiagnosticsAsync()
    {
        var sb = new System.Text.StringBuilder();
        sb.AppendLine("=== Event Notification Service Diagnostics ===");
        
        try
        {
            var enabled = await LocalNotificationCenter.Current.AreNotificationsEnabled();
            sb.AppendLine($"Notifications enabled: {enabled}");
            sb.AppendLine($"Channel initialized: {_channelInitialized}");
            sb.AppendLine($"Scheduled notification count: {_scheduledNotificationIds.Count}");
            sb.AppendLine($"Last scheduled time: {_lastScheduledTime:yyyy-MM-dd HH:mm:ss}");
            
            if (_scheduledNotificationIds.Count > 0)
            {
                sb.AppendLine($"Scheduled IDs: {string.Join(", ", _scheduledNotificationIds.OrderBy(x => x))}");
            }
            
#if ANDROID
            if (Android.OS.Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.S)
            {
                var alarmManager = Android.App.AlarmManager.FromContext(Android.App.Application.Context);
                if (alarmManager != null)
                {
                    var canScheduleExact = alarmManager.CanScheduleExactAlarms();
                    sb.AppendLine($"Can schedule exact alarms: {canScheduleExact}");
                }
            }
#endif
        }
        catch (Exception ex)
        {
            sb.AppendLine($"Error getting diagnostics: {ex.Message}");
        }
        
        return sb.ToString();
    }

    /// <summary>
    /// Check for changes in events and send immediate notifications for modifications and cancellations
    /// </summary>
    public static async Task CheckAndNotifyChangesAsync(List<EventService.EventInstance> currentEvents)
    {
        try
        {
            // Load previous snapshot
            var previousSnapshotJson = await SecureStorage.GetAsync(EventsSnapshotKey);
            if (string.IsNullOrEmpty(previousSnapshotJson))
            {
                // First run, save current state and exit
                await SaveEventsSnapshotAsync(currentEvents);
                return;
            }

            var previousSnapshots = JsonSerializer.Deserialize<List<EventSnapshot>>(previousSnapshotJson);
            if (previousSnapshots == null)
            {
                await SaveEventsSnapshotAsync(currentEvents);
                return;
            }

            var previousDict = previousSnapshots.ToDictionary(e => e.Id);
            int changesDetected = 0;

            foreach (var currentEvent in currentEvents)
            {
                if (currentEvent.Since == null) continue;

                // Check if event existed before
                if (!previousDict.TryGetValue(currentEvent.Id, out var previousEvent))
                    continue; // New event, not a change

                var eventName = currentEvent.Event?.Name ?? LocalizationService.Get("Event");
                
                // Check for cancellation
                if (currentEvent.IsCancelled && !previousEvent.IsCancelled)
                {
                    await SendImmediateNotificationAsync(
                        LocalizationService.Get("Notification_EventCancelled") ?? "Událost zrušena",
                        $"{eventName}\n{FormatEventDescription(currentEvent.Since.Value, currentEvent.Event?.LocationText)}",
                        currentEvent.Id
                    );
                    changesDetected++;
                    continue;
                }

                // Skip if already cancelled
                if (currentEvent.IsCancelled) continue;

                // Check for time change
                if (currentEvent.Since != previousEvent.Since || currentEvent.Until != previousEvent.Until)
                {
                    var oldTime = previousEvent.Since?.ToString("HH:mm") ?? "?";
                    var newTime = currentEvent.Since?.ToString("HH:mm") ?? "?";
                    await SendImmediateNotificationAsync(
                        LocalizationService.Get("Notification_EventTimeChanged") ?? "Změna času události",
                        $"{eventName}\n{oldTime} → {newTime}\n{currentEvent.Event?.LocationText ?? ""}",
                        currentEvent.Id
                    );
                    changesDetected++;
                    continue;
                }

                // Check for location change
                var currentLocation = currentEvent.Event?.LocationText ?? "";
                var previousLocation = previousEvent.LocationText ?? "";
                if (currentLocation != previousLocation)
                {
                    await SendImmediateNotificationAsync(
                        LocalizationService.Get("Notification_EventLocationChanged") ?? "Změna místa události",
                        $"{eventName}\n{currentLocation}\n{FormatEventDescription(currentEvent.Since.Value, null)}",
                        currentEvent.Id
                    );
                    changesDetected++;
                    continue;
                }

                // Check for trainer change
                var currentTrainers = JsonSerializer.Serialize(currentEvent.Event?.EventTrainersList?.Select(t => t.Name).OrderBy(n => n).ToList() ?? new List<string?>());
                if (currentTrainers != previousEvent.TrainersJson)
                {
                    await SendImmediateNotificationAsync(
                        LocalizationService.Get("Notification_EventDetailsChanged") ?? "Změna detailů události",
                        $"{eventName}\n{FormatEventDescription(currentEvent.Since.Value, currentEvent.Event?.LocationText)}",
                        currentEvent.Id
                    );
                    changesDetected++;
                }
            }

            if (changesDetected > 0)
            {
                Debug.WriteLine($"EventNotificationService: Detected and notified {changesDetected} event change(s)");
            }

            // Save current snapshot for next comparison
            await SaveEventsSnapshotAsync(currentEvents);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Error checking for changes: {ex}");
        }
    }

    private static async Task SaveEventsSnapshotAsync(List<EventService.EventInstance> events)
    {
        try
        {
            var snapshots = events.Where(e => e.Since != null).Select(e => new EventSnapshot
            {
                Id = e.Id,
                Since = e.Since,
                Until = e.Until,
                LocationText = e.Event?.LocationText,
                EventName = e.Event?.Name,
                IsCancelled = e.IsCancelled,
                TrainersJson = JsonSerializer.Serialize(e.Event?.EventTrainersList?.Select(t => t.Name).OrderBy(n => n).ToList() ?? new List<string?>())
            }).ToList();

            var json = JsonSerializer.Serialize(snapshots);
            await SecureStorage.SetAsync(EventsSnapshotKey, json);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Error saving snapshot: {ex}");
        }
    }

    private static async Task SendImmediateNotificationAsync(string title, string description, long eventId)
    {
        try
        {
            EnsureChannelInitialized();

            var notificationId = (int)(eventId % 10000) + 5000; // Use event ID to ensure uniqueness

            var notification = new NotificationRequest
            {
                NotificationId = notificationId,
                Title = title,
                Description = description,
                ReturningData = eventId.ToString(),
                Android = new AndroidOptions
                {
                    ChannelId = ChannelId,
                    Priority = AndroidPriority.Max, // Highest priority for immediate changes
                    AutoCancel = true,
                    VibrationPattern = new long[] { 0, 700, 300, 700 }, // Stronger vibration for changes
                    TimeoutAfter = TimeSpan.FromHours(2),
                    IconSmallName = new AndroidIcon("ic_notification")
                },
                CategoryType = NotificationCategoryType.Event
            };

            await LocalNotificationCenter.Current.Show(notification);
            Debug.WriteLine($"EventNotificationService: Sent immediate notification: {title}");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Failed to send immediate notification: {ex.Message}");
        }
    }

    /// <summary>
    /// Initialize background worker for periodic change detection (Android only)
    /// </summary>
    public static void InitializeBackgroundChangeDetection()
    {
#if ANDROID
        try
        {
            var constraints = new AndroidX.Work.Constraints.Builder()
                .SetRequiredNetworkType(AndroidX.Work.NetworkType.Connected)
                .SetRequiresBatteryNotLow(true)
                .Build();

            var workRequest = AndroidX.Work.PeriodicWorkRequest.Builder
                .From<Platforms.Android.EventChangeCheckWorker>(TimeSpan.FromHours(1))
                .SetConstraints(constraints)
                .SetBackoffCriteria(AndroidX.Work.BackoffPolicy.Exponential, TimeSpan.FromMinutes(15))
                .Build();

            AndroidX.Work.WorkManager
                .GetInstance(Android.App.Application.Context)
                .EnqueueUniquePeriodicWork(
                    "event_change_check",
                    AndroidX.Work.ExistingPeriodicWorkPolicy.Keep,
                    workRequest
                );

            Debug.WriteLine("EventNotificationService: Background change detection initialized (runs every 1 hour)");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"EventNotificationService: Failed to initialize background worker: {ex}");
        }
#endif
    }
}
