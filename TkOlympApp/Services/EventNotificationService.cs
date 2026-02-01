using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Storage;
using Plugin.LocalNotification;
using Plugin.LocalNotification.AndroidOption;
using TkOlympApp.Services;

namespace TkOlympApp.Services;

public static class EventNotificationService
{
    private static readonly HashSet<int> _scheduledNotificationIds = new();
    private static DateTime _lastScheduledTime = DateTime.MinValue;
    private static bool _channelInitialized = false;
    private static ILogger? _logger;

    private const string ChannelId = "tkolymp_events";
    private const string ChannelName = "Události a lekce";
    private const string ChannelDescription = "Upozornění na nadcházející události, lekce a tréninky";
    private const string EventsSnapshotKey = "events_snapshot_v1";

    /// <summary>
    /// Initialize the service with a logger instance
    /// </summary>
    public static void Initialize(ILogger<App>? logger = null)
    {
        if (logger != null)
        {
            _logger = logger;
        }
    }

    private class EventSnapshot
    {
        public long Id { get; set; }
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
        public string? LocationText { get; set; }
        public string? EventName { get; set; }
        public bool IsCancelled { get; set; }
        public string? TrainersJson { get; set; }
        public string? RegistrationsJson { get; set; }
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
            _logger?.LogInformation("Using notification channel '{ChannelId}'", ChannelId);
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error in channel initialization");
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

            // Load user-defined notification rules
            var rules = NotificationSettingsService.GetAll();

            foreach (var eventInstance in events)
            {
                if (eventInstance.Since == null || eventInstance.IsCancelled)
                    continue;

                var eventStart = eventInstance.Since.Value;
                if (eventStart <= now)
                    continue; // Skip past events

                var eventName = GetEventDisplayName(eventInstance);
                var locationText = eventInstance.Event?.LocationText;
                var eventType = eventInstance.Event?.Type;
                // Collect trainer IDs as strings (person id from API)
                string[]? trainerIds = null;
                try
                {
                    var ids = eventInstance.Event?.EventTrainersList?.Select(t => t?.Id).Where(x => !string.IsNullOrWhiteSpace(x)).ToArray();
                    if (ids != null && ids.Length > 0) trainerIds = ids;
                }
                catch { trainerIds = null; }
                
                // Format event description
                var description = FormatEventDescription(eventStart, locationText);

                // If user has defined rules, schedule according to them. Otherwise fallback to 1h and 5min.
                if (rules != null && rules.Count > 0)
                {
                    foreach (var rule in rules)
                    {
                        try
                        {
                            if (!rule.Enabled) continue;

                            // match event type
                            if (rule.EventTypes != null && rule.EventTypes.Count > 0)
                            {
                                if (string.IsNullOrEmpty(eventType)) continue;
                                if (!rule.EventTypes.Exists(t => string.Equals(t, eventType, StringComparison.OrdinalIgnoreCase))) continue;
                            }

                            // match trainers
                            if (rule.TrainerIds != null && rule.TrainerIds.Count > 0)
                            {
                                if (trainerIds == null || trainerIds.Length == 0) continue;
                                var matched = trainerIds.Any(id => id != null && rule.TrainerIds.Contains(id));
                                if (!matched) continue;
                            }

                            var notifyTime = eventStart - rule.TimeBefore;
                            if (notifyTime <= now) continue;

                            var title = rule.TimeBefore.TotalMinutes >= 60 && (rule.TimeBefore.TotalMinutes % 60) == 0
                                ? GetNotificationTitle((int)rule.TimeBefore.TotalHours, "hour")
                                : GetNotificationTitle((int)Math.Max(1, rule.TimeBefore.TotalMinutes), "minutes");

                            var success = await ScheduleNotificationAsync(notificationId++, title, $"{eventName}\n{description}", notifyTime, eventInstance.Id);
                            if (success) scheduledCount++;
                        }
                        catch { }
                    }
                }
                else
                {
                    // Fallback: schedule 1 hour and 5 minutes before
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
            }

            _logger?.LogInformation("Successfully scheduled {ScheduledCount} notifications for {EventCount} events", scheduledCount, events.Count);
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error scheduling notifications");
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

    private static string GetEventDisplayName(EventService.EventInstance eventInstance)
    {
        var evt = eventInstance.Event;
        var defaultName = LocalizationService.Get("Event") ?? "Událost";

        if (evt == null)
            return defaultName;

            if (!string.IsNullOrWhiteSpace(evt.Type) && string.Equals(evt.Type, "lesson", StringComparison.OrdinalIgnoreCase))
            {
                var trainerName = EventService.GetTrainerDisplayName(evt.EventTrainersList?.FirstOrDefault());
                if (!string.IsNullOrWhiteSpace(trainerName))
                    return trainerName;
            }

        return evt.Name ?? defaultName;
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
            _logger?.LogInformation("✓ Scheduled notification {NotificationId} for {NotifyTime} (in {Minutes:F0} min)", notificationId, notifyTime.ToString("yyyy-MM-dd HH:mm"), timeUntil.TotalMinutes);
            return true;
        }
        catch (Exception ex)
        {
            _logger?.LogWarning(ex, "✗ Failed to schedule notification {NotificationId}", notificationId);
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
            _logger?.LogInformation("Cleared all scheduled notifications");
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error clearing notifications");
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
                _logger?.LogInformation("Notification permission request result: {Result}", result);
            }
            
            enabled = await LocalNotificationCenter.Current.AreNotificationsEnabled();
            
#if ANDROID
    #pragma warning disable CA1416
                // Check exact alarm permission (Android 12+)
                if (Android.OS.Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.S)
                {
                    try
                    {
                        var alarmManager = Android.App.AlarmManager.FromContext(Android.App.Application.Context);
                        if (alarmManager != null && !alarmManager.CanScheduleExactAlarms())
                        {
                            _logger?.LogWarning("App cannot schedule exact alarms - user needs to grant permission in settings");
                            // Note: On Android 12+, user needs to manually enable exact alarms in Settings
                            // We can't request this permission directly from code
                        }
                        else
                        {
                            _logger?.LogInformation("Exact alarm permission granted");
                        }
                    }
                    catch (Exception ex)
                    {
                        _logger?.LogWarning(ex, "Error checking exact alarm permission");
                    }
                }
    #pragma warning restore CA1416
#endif
            
            if (enabled)
            {
                _logger?.LogInformation("✓ Notifications enabled and ready");
            }
            else
            {
                _logger?.LogWarning("✗ Notifications not enabled");
            }
            
            return enabled;
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error requesting permission");
            return false;
        }
    }

    /// <summary>
    /// Notify user about a registration or unregistration for a specific event.
    /// Uses localized titles when available and falls back to simple Czech messages.
    /// </summary>
    public static async Task NotifyRegistrationAsync(long eventId, bool registered)
    {
        try
        {
            // Fetch event details to build a meaningful message
            var ev = await EventService.GetEventAsync(eventId);
            var eventName = ev?.Name ?? LocalizationService.Get("Event") ?? "Událost";
            var since = ev?.Since;

            var title = registered
                ? LocalizationService.Get("Notification_Registration_Confirmed") ?? "Registrace potvrzena"
                : LocalizationService.Get("Notification_Registration_Cancelled") ?? "Registrace zrušena";

            var description = since != null
                ? $"{eventName}\n{FormatEventDescription(since.Value, ev?.LocationText)}"
                : eventName;

            await SendImmediateNotificationAsync(title, description, eventId);
        }
        catch (Exception ex)
        {
            _logger?.LogWarning(ex, "Failed to notify registration change");
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
                    #pragma warning disable CA1416
                    var canScheduleExact = alarmManager.CanScheduleExactAlarms();
                    sb.AppendLine($"Can schedule exact alarms: {canScheduleExact}");
                    #pragma warning restore CA1416
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

                var eventName = GetEventDisplayName(currentEvent);
                
                // Prepare trainer ids for rule matching
                string[]? trainerIdsForMatch = null;
                try
                {
                    var ids = currentEvent.Event?.EventTrainersList?.Select(t => t?.Id).Where(x => !string.IsNullOrWhiteSpace(x)).ToArray();
                    if (ids != null && ids.Length > 0) trainerIdsForMatch = ids;
                }
                catch { trainerIdsForMatch = null; }

                // Check for cancellation
                if (currentEvent.IsCancelled && !previousEvent.IsCancelled)
                {
                    if (!NotificationSettingsService.ShouldNotify(currentEvent.Event?.Type, trainerIdsForMatch)) continue;
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
                    if (!NotificationSettingsService.ShouldNotify(currentEvent.Event?.Type, trainerIdsForMatch)) continue;
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
                    if (!NotificationSettingsService.ShouldNotify(currentEvent.Event?.Type, trainerIdsForMatch)) continue;
                    await SendImmediateNotificationAsync(
                        LocalizationService.Get("Notification_EventLocationChanged") ?? "Změna místa události",
                        $"{eventName}\n{currentLocation}\n{FormatEventDescription(currentEvent.Since.Value, null)}",
                        currentEvent.Id
                    );
                    changesDetected++;
                    continue;
                }

                // Check for trainer change
                var currentTrainers = JsonSerializer.Serialize(currentEvent.Event?.EventTrainersList?.Select(t => EventService.GetTrainerDisplayName(t)).OrderBy(n => n).ToList() ?? new List<string>());
                if (currentTrainers != previousEvent.TrainersJson)
                {
                    if (!NotificationSettingsService.ShouldNotify(currentEvent.Event?.Type, trainerIdsForMatch)) continue;
                    await SendImmediateNotificationAsync(
                        LocalizationService.Get("Notification_EventDetailsChanged") ?? "Změna detailů události",
                        $"{eventName}\n{FormatEventDescription(currentEvent.Since.Value, currentEvent.Event?.LocationText)}",
                        currentEvent.Id
                    );
                    changesDetected++;
                }

                // Check for registration changes (add/remove)
                try
                {
                    var currentRegs = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                    if (currentEvent.Event?.EventRegistrationsList != null)
                    {
                        foreach (var rn in currentEvent.Event.EventRegistrationsList)
                        {
                            if (rn == null) continue;
                            // Use display names for snapshot/compare since instance-level query does not include IDs
                            if (rn.Person != null && !string.IsNullOrWhiteSpace(rn.Person.Name)) currentRegs.Add("p:" + rn.Person.Name.Trim());
                            else if (rn.Couple != null)
                            {
                                var man = rn.Couple.Man?.LastName?.Trim() ?? string.Empty;
                                var woman = rn.Couple.Woman?.LastName?.Trim() ?? string.Empty;
                                var key = (man + " " + woman).Trim();
                                if (!string.IsNullOrWhiteSpace(key)) currentRegs.Add("c:" + key);
                            }
                        }
                    }

                    var previousRegs = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                    if (!string.IsNullOrWhiteSpace(previousEvent.RegistrationsJson))
                    {
                        try
                        {
                            var arr = JsonSerializer.Deserialize<List<string>>(previousEvent.RegistrationsJson!);
                            if (arr != null) foreach (var a in arr) previousRegs.Add(a);
                        }
                        catch { }
                    }

                    var added = currentRegs.Except(previousRegs).ToList();
                    var removed = previousRegs.Except(currentRegs).ToList();

                    if (added.Count > 0 || removed.Count > 0)
                    {
                        // Check if current user is affected
                        try
                        {
                            await UserService.InitializeAsync();
                            var myPersonId = UserService.CurrentPersonId;
                            var myCoupleIds = new List<string>();
                            try
                            {
                                var couples = await UserService.GetActiveCouplesFromUsersAsync();
                                if (couples != null) myCoupleIds = couples.Where(c => !string.IsNullOrWhiteSpace(c.Id)).Select(c => c.Id!).ToList();
                            }
                            catch { }

                            var affected = false;
                            if (!string.IsNullOrWhiteSpace(myPersonId) && (added.Any(a => string.Equals(a, "p:" + myPersonId, StringComparison.OrdinalIgnoreCase)) || removed.Any(a => string.Equals(a, "p:" + myPersonId, StringComparison.OrdinalIgnoreCase)))) affected = true;
                            if (!affected && myCoupleIds.Count > 0)
                            {
                                foreach (var cid in myCoupleIds)
                                {
                                    if (added.Any(a => string.Equals(a, "c:" + cid, StringComparison.OrdinalIgnoreCase)) || removed.Any(a => string.Equals(a, "c:" + cid, StringComparison.OrdinalIgnoreCase)))
                                    {
                                        affected = true; break;
                                    }
                                }
                            }

                            if (affected)
                            {
                                // Compose message: if added contains my id => registered, if removed contains my id => unregistered
                                var registeredNow = !string.IsNullOrWhiteSpace(myPersonId) && added.Any(a => string.Equals(a, "p:" + myPersonId, StringComparison.OrdinalIgnoreCase));
                                if (!registeredNow && myCoupleIds.Count > 0) registeredNow = myCoupleIds.Any(cid => added.Any(a => string.Equals(a, "c:" + cid, StringComparison.OrdinalIgnoreCase)));

                                var title = registeredNow
                                    ? LocalizationService.Get("Notification_Registration_YouWereRegistered") ?? "Byl(a) jste přihlášen(a)"
                                    : LocalizationService.Get("Notification_Registration_YouWereUnregistered") ?? "Byla zrušena vaše registrace";

                                var description = $"{eventName}\n{FormatEventDescription(currentEvent.Since.Value, currentEvent.Event?.LocationText)}";
                                await SendImmediateNotificationAsync(title, description, currentEvent.Id);
                                changesDetected++;
                            }
                        }
                        catch { }
                    }
                }
                catch { }
            }

            if (changesDetected > 0)
            {
                _logger?.LogInformation("Detected and notified {ChangesDetected} event change(s)", changesDetected);
            }

            // Save current snapshot for next comparison
            await SaveEventsSnapshotAsync(currentEvents);
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error checking for changes");
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
                TrainersJson = JsonSerializer.Serialize(e.Event?.EventTrainersList?.Select(t => EventService.GetTrainerDisplayName(t)).OrderBy(n => n).ToList() ?? new List<string>()),
                RegistrationsJson = JsonSerializer.Serialize(
                    (e.Event?.EventRegistrationsList?.Select(rn =>
                        {
                            if (rn == null) return null;
                            if (rn.Person != null && !string.IsNullOrWhiteSpace(rn.Person.Name)) return "p:" + rn.Person.Name.Trim();
                            if (rn.Couple != null)
                            {
                                var man = rn.Couple.Man?.LastName?.Trim() ?? string.Empty;
                                var woman = rn.Couple.Woman?.LastName?.Trim() ?? string.Empty;
                                var key = (man + " " + woman).Trim();
                                if (!string.IsNullOrWhiteSpace(key)) return "c:" + key;
                            }
                            return null;
                        }).Where(s => s != null).Cast<string>().ToList()) ?? new List<string>()
                )
            }).ToList();

            var json = JsonSerializer.Serialize(snapshots);
            await SecureStorage.SetAsync(EventsSnapshotKey, json);
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error saving snapshot");
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
            _logger?.LogInformation("Sent immediate notification: {Title}", title);
        }
        catch (Exception ex)
        {
            _logger?.LogWarning(ex, "Failed to send immediate notification");
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
                .SetRequiredNetworkType(AndroidX.Work.NetworkType.Connected!)
                .SetRequiresBatteryNotLow(true)
                .Build();

            var workRequest = AndroidX.Work.PeriodicWorkRequest.Builder
                .From<Platforms.Android.EventChangeCheckWorker>(TimeSpan.FromMinutes(30))
                .SetConstraints(constraints)
                .SetBackoffCriteria(AndroidX.Work.BackoffPolicy.Exponential!, TimeSpan.FromMinutes(15))
                .Build();

            AndroidX.Work.WorkManager
                .GetInstance(Android.App.Application.Context)
                .EnqueueUniquePeriodicWork(
                    "event_change_check",
                    AndroidX.Work.ExistingPeriodicWorkPolicy.Keep!,
                    workRequest
                );

            _logger?.LogInformation("Background change detection initialized (runs every 1 hour)");
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Failed to initialize background worker");
        }
#endif
    }
}
