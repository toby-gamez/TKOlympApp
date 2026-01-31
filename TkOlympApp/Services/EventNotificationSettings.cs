using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services
{
    public record EventNotificationSetting
    {
        public Guid Id { get; init; } = Guid.NewGuid();
        public string Name { get; init; } = "Default";
        public bool Enabled { get; init; } = true;
        // Time before the event when the notification should fire
        public TimeSpan TimeBefore { get; init; } = TimeSpan.FromMinutes(15);
        // If empty or null -> applies to all event types
        public List<string>? EventTypes { get; init; }
        // If empty or null -> applies to all trainers (store trainer person id strings)
        public List<string>? TrainerIds { get; init; }
    }

    public static class NotificationSettingsService
    {
        const string PrefKey = "event_notification_settings_v1";
        static readonly System.Text.Json.JsonSerializerOptions Options = new(System.Text.Json.JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = true
        };

        static List<EventNotificationSetting> LoadAll()
        {
            try
            {
                var json = Microsoft.Maui.Storage.Preferences.Get(PrefKey, (string?)null);
                if (string.IsNullOrWhiteSpace(json)) return new List<EventNotificationSetting>();
                var items = System.Text.Json.JsonSerializer.Deserialize<List<EventNotificationSetting>>(json, Options);
                return items ?? new List<EventNotificationSetting>();
            }
            catch
            {
                return new List<EventNotificationSetting>();
            }
        }

        static void SaveAll(List<EventNotificationSetting> items)
        {
            try
            {
                var json = System.Text.Json.JsonSerializer.Serialize(items, Options);
                Microsoft.Maui.Storage.Preferences.Set(PrefKey, json);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"NotificationSettingsService.SaveAll failed: {ex}");
            }
        }

        public static IReadOnlyList<EventNotificationSetting> GetAll()
        {
            var list = LoadAll();
            if (list.Count == 0)
            {
                // Create default rules (1 hour before, 5 minutes before) enabled by default
                try
                {
                    var hourName = LocalizationService.Get("Notification_DefaultRuleName_Hour") ?? "1 hour before";
                    var fiveName = LocalizationService.Get("Notification_DefaultRuleName_5min") ?? "5 minutes before";

                    var hourRule = new EventNotificationSetting
                    {
                        Id = Guid.NewGuid(),
                        Name = hourName,
                        Enabled = true,
                        TimeBefore = TimeSpan.FromHours(1),
                        EventTypes = null,
                        TrainerIds = null
                    };

                    var fiveRule = new EventNotificationSetting
                    {
                        Id = Guid.NewGuid(),
                        Name = fiveName,
                        Enabled = true,
                        TimeBefore = TimeSpan.FromMinutes(5),
                        EventTypes = null,
                        TrainerIds = null
                    };

                    list.Add(hourRule);
                    list.Add(fiveRule);
                    SaveAll(list);
                }
                catch
                {
                    // ignore localization or save errors, return empty list fallback
                }
            }

            return list.AsReadOnly();
        }

        public static void AddOrUpdate(EventNotificationSetting setting)
        {
            var list = LoadAll();
            var idx = list.FindIndex(x => x.Id == setting.Id);
            if (idx >= 0) list[idx] = setting;
            else list.Add(setting);
            SaveAll(list);
        }

        public static void Remove(Guid id)
        {
            var list = LoadAll();
            list.RemoveAll(x => x.Id == id);
            SaveAll(list);
        }

        public static bool ShouldNotify(string? eventType, IEnumerable<string>? trainerIds)
        {
            // Use GetAll() so default rules (1h and 5min) are created when none exist
            var list = new List<EventNotificationSetting>(GetAll());
            foreach (var s in list)
            {
                if (!s.Enabled) continue;

                var typeMatch = true;
                if (s.EventTypes != null && s.EventTypes.Count > 0)
                {
                    if (string.IsNullOrEmpty(eventType)) typeMatch = false;
                    else typeMatch = s.EventTypes.Exists(t => string.Equals(t, eventType, StringComparison.OrdinalIgnoreCase));
                }

                if (!typeMatch) continue;

                var trainerMatch = true;
                if (s.TrainerIds != null && s.TrainerIds.Count > 0)
                {
                    if (trainerIds == null) trainerMatch = false;
                    else
                    {
                        trainerMatch = false;
                        foreach (var id in trainerIds)
                        {
                            if (id != null && s.TrainerIds.Contains(id))
                            {
                                trainerMatch = true;
                                break;
                            }
                        }
                    }
                }

                if (!trainerMatch) continue;

                return true;
            }

            return false;
        }
    }
}
