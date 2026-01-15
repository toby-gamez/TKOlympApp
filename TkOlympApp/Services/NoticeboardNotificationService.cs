using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Maui.Storage;
using Plugin.LocalNotification;
using Plugin.LocalNotification.AndroidOption;

namespace TkOlympApp.Services;

public static class NoticeboardNotificationService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    private const string ChannelId = "tkolymp_noticeboard";
    private const string ChannelName = "Nástěnka";
    private const string ChannelDescription = "Upozornění na nová oznámení na nástěnce";
    
    private const string LastCheckKey = "noticeboard_last_check";
    private const string LastAnnouncementsKey = "noticeboard_last_announcements";
    
    private static bool _channelInitialized = false;

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
            Debug.WriteLine($"NoticeboardNotificationService: Using notification channel '{ChannelId}'");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error in channel initialization: {ex}");
        }
    }

    /// <summary>
    /// Check for new or updated announcements and show notifications
    /// </summary>
    public static async Task CheckAndNotifyChangesAsync(List<NoticeboardService.Announcement> currentAnnouncements, CancellationToken ct = default)
    {
        try
        {
            EnsureChannelInitialized();
            
            Debug.WriteLine("NoticeboardNotificationService: Starting check for new announcements");
            
            // Load last known state
            var lastCheckTimestamp = await LoadLastCheckTimestampAsync();
            var lastAnnouncements = await LoadLastAnnouncementsAsync();
            
            // Find new and updated announcements
            var newAnnouncements = new List<NoticeboardService.Announcement>();
            var updatedAnnouncements = new List<NoticeboardService.Announcement>();
            
            foreach (var announcement in currentAnnouncements)
            {
                var previousVersion = lastAnnouncements.FirstOrDefault(a => a.Id == announcement.Id);
                
                if (previousVersion == null)
                {
                    // This is a new announcement
                    if (lastCheckTimestamp != DateTime.MinValue && announcement.CreatedAt > lastCheckTimestamp)
                    {
                        newAnnouncements.Add(announcement);
                    }
                }
                else
                {
                    // Check if it was updated (comparing body content)
                    if (announcement.Body != previousVersion.Body || announcement.Title != previousVersion.Title)
                    {
                        updatedAnnouncements.Add(announcement);
                    }
                }
            }
            
            // Show notifications for new announcements
            if (newAnnouncements.Count > 0)
            {
                Debug.WriteLine($"NoticeboardNotificationService: Found {newAnnouncements.Count} new announcement(s)");
                await ShowNewAnnouncementsNotificationAsync(newAnnouncements);
            }
            
            // Show notifications for updated announcements
            if (updatedAnnouncements.Count > 0)
            {
                Debug.WriteLine($"NoticeboardNotificationService: Found {updatedAnnouncements.Count} updated announcement(s)");
                await ShowUpdatedAnnouncementsNotificationAsync(updatedAnnouncements);
            }
            
            // Save current state for next comparison
            await SaveLastCheckTimestampAsync(DateTime.Now);
            await SaveLastAnnouncementsAsync(currentAnnouncements);
            
            Debug.WriteLine("NoticeboardNotificationService: Check completed successfully");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error checking for new announcements: {ex}");
        }
    }

    private static async Task ShowNewAnnouncementsNotificationAsync(List<NoticeboardService.Announcement> announcements)
    {
        try
        {
            var count = announcements.Count;
            var title = count == 1 
                ? LocalizationService.Get("Noticeboard_NewAnnouncement") ?? "Nové oznámení"
                : string.Format(LocalizationService.Get("Noticeboard_NewAnnouncements") ?? "Nová oznámení ({0})", count);
            
            var firstAnnouncement = announcements.First();
            var message = count == 1
                ? firstAnnouncement.Title ?? "Bylo přidáno nové oznámení"
                : string.Join(", ", announcements.Select(a => a.Title).Take(3)) + (count > 3 ? "..." : "");

            var request = new NotificationRequest
            {
                NotificationId = GenerateNotificationId("new"),
                Title = title,
                Description = message,
                Schedule = new NotificationRequestSchedule
                {
                    NotifyTime = DateTime.Now.AddSeconds(2)
                },
                Android = new AndroidOptions
                {
                    ChannelId = ChannelId,
                    Priority = AndroidPriority.High,
                    AutoCancel = true
                }
            };

            await LocalNotificationCenter.Current.Show(request);
            Debug.WriteLine($"NoticeboardNotificationService: Notification shown for {count} new announcement(s)");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error showing notification: {ex}");
        }
    }

    private static async Task ShowUpdatedAnnouncementsNotificationAsync(List<NoticeboardService.Announcement> announcements)
    {
        try
        {
            var count = announcements.Count;
            var title = count == 1 
                ? LocalizationService.Get("Noticeboard_UpdatedAnnouncement") ?? "Aktualizované oznámení"
                : string.Format(LocalizationService.Get("Noticeboard_UpdatedAnnouncements") ?? "Aktualizovaná oznámení ({0})", count);
            
            var firstAnnouncement = announcements.First();
            var message = count == 1
                ? firstAnnouncement.Title ?? "Oznámení bylo aktualizováno"
                : string.Join(", ", announcements.Select(a => a.Title).Take(3)) + (count > 3 ? "..." : "");

            var request = new NotificationRequest
            {
                NotificationId = GenerateNotificationId("updated"),
                Title = title,
                Description = message,
                Schedule = new NotificationRequestSchedule
                {
                    NotifyTime = DateTime.Now.AddSeconds(2)
                },
                Android = new AndroidOptions
                {
                    ChannelId = ChannelId,
                    Priority = AndroidPriority.Default,
                    AutoCancel = true
                }
            };

            await LocalNotificationCenter.Current.Show(request);
            Debug.WriteLine($"NoticeboardNotificationService: Notification shown for {count} updated announcement(s)");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error showing notification: {ex}");
        }
    }

    private static int GenerateNotificationId(string prefix)
    {
        // Generate a stable ID based on prefix and current date
        var hash = $"{prefix}_{DateTime.Now:yyyyMMdd}".GetHashCode();
        return Math.Abs(hash % 10000) + 5000; // Range 5000-15000
    }

    #region Storage helpers

    private static async Task<DateTime> LoadLastCheckTimestampAsync()
    {
        try
        {
            var value = await SecureStorage.GetAsync(LastCheckKey);
            if (string.IsNullOrWhiteSpace(value))
                return DateTime.MinValue;
            
            if (DateTime.TryParse(value, out var timestamp))
                return timestamp;
            
            return DateTime.MinValue;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error loading last check timestamp: {ex}");
            return DateTime.MinValue;
        }
    }

    private static async Task SaveLastCheckTimestampAsync(DateTime timestamp)
    {
        try
        {
            await SecureStorage.SetAsync(LastCheckKey, timestamp.ToString("O"));
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error saving last check timestamp: {ex}");
        }
    }

    private static async Task<List<NoticeboardService.Announcement>> LoadLastAnnouncementsAsync()
    {
        try
        {
            var json = await SecureStorage.GetAsync(LastAnnouncementsKey);
            if (string.IsNullOrWhiteSpace(json))
                return new List<NoticeboardService.Announcement>();
            
            var announcements = JsonSerializer.Deserialize<List<NoticeboardService.Announcement>>(json, Options);
            return announcements ?? new List<NoticeboardService.Announcement>();
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error loading last announcements: {ex}");
            return new List<NoticeboardService.Announcement>();
        }
    }

    private static async Task SaveLastAnnouncementsAsync(List<NoticeboardService.Announcement> announcements)
    {
        try
        {
            var json = JsonSerializer.Serialize(announcements, Options);
            await SecureStorage.SetAsync(LastAnnouncementsKey, json);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"NoticeboardNotificationService: Error saving last announcements: {ex}");
        }
    }

    #endregion
}