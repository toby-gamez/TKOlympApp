using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Storage;
using Plugin.LocalNotification;
using Plugin.LocalNotification.AndroidOption;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public sealed class NoticeboardNotificationService : INoticeboardNotificationService
{
    private readonly ISecureStorage _secureStorage;
    private readonly ILogger<NoticeboardNotificationService> _logger;

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    private const string ChannelId = "tkolymp_noticeboard";
    private const string ChannelName = "Nástěnka";
    private const string ChannelDescription = "Upozornění na nová oznámení na nástěnce";
    
    private const string LastCheckKey = "noticeboard_last_check";
    private const string LastAnnouncementsKey = "noticeboard_last_announcements";
    
    private bool _channelInitialized = false;

    public NoticeboardNotificationService(ISecureStorage secureStorage, ILogger<NoticeboardNotificationService> logger)
    {
        _secureStorage = secureStorage;
        _logger = logger;
    }

    /// <summary>
    /// Initialize notification channel (Android)
    /// </summary>
    private void EnsureChannelInitialized()
    {
        if (_channelInitialized) return;
        
        try
        {
            // Plugin.LocalNotification handles channel creation automatically via AndroidOptions
            // We just need to use consistent ChannelId in notification requests
            _channelInitialized = true;
            _logger.LogInformation("NoticeboardNotificationService: Using notification channel '{ChannelId}'", ChannelId);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error in channel initialization");
        }
    }

    /// <summary>
    /// Check for new or updated announcements and show notifications
    /// </summary>
    public async Task CheckAndNotifyChangesAsync(List<Announcement> currentAnnouncements, CancellationToken ct = default)
    {
        try
        {
            EnsureChannelInitialized();
            
            _logger.LogDebug("NoticeboardNotificationService: Starting check for new announcements");
            
            // Load last known state
            var lastCheckTimestamp = await LoadLastCheckTimestampAsync();
            var lastAnnouncements = await LoadLastAnnouncementsAsync();
            
            // Find new and updated announcements
            var newAnnouncements = new List<Announcement>();
            var updatedAnnouncements = new List<Announcement>();
            
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
                _logger.LogInformation("NoticeboardNotificationService: Found {Count} new announcement(s)", newAnnouncements.Count);
                await ShowNewAnnouncementsNotificationAsync(newAnnouncements);
            }
            
            // Show notifications for updated announcements
            if (updatedAnnouncements.Count > 0)
            {
                _logger.LogInformation("NoticeboardNotificationService: Found {Count} updated announcement(s)", updatedAnnouncements.Count);
                await ShowUpdatedAnnouncementsNotificationAsync(updatedAnnouncements);
            }
            
            // Save current state for next comparison
            await SaveLastCheckTimestampAsync(DateTime.Now);
            await SaveLastAnnouncementsAsync(currentAnnouncements);
            
            _logger.LogDebug("NoticeboardNotificationService: Check completed successfully");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error checking for new announcements");
        }
    }

    private async Task ShowNewAnnouncementsNotificationAsync(List<Announcement> announcements)
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
            _logger.LogInformation("NoticeboardNotificationService: Notification shown for {Count} new announcement(s)", count);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error showing notification");
        }
    }

    private async Task ShowUpdatedAnnouncementsNotificationAsync(List<Announcement> announcements)
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
            _logger.LogInformation("NoticeboardNotificationService: Notification shown for {Count} updated announcement(s)", count);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error showing notification");
        }
    }

    private static int GenerateNotificationId(string prefix)
    {
        // Generate a stable ID based on prefix and current date
        var hash = $"{prefix}_{DateTime.Now:yyyyMMdd}".GetHashCode();
        return Math.Abs(hash % 10000) + 5000; // Range 5000-15000
    }

    #region Storage helpers

    private async Task<DateTime> LoadLastCheckTimestampAsync()
    {
        try
        {
            var value = await _secureStorage.GetAsync(LastCheckKey);
            if (string.IsNullOrWhiteSpace(value))
                return DateTime.MinValue;
            
            if (DateTime.TryParse(value, out var timestamp))
                return timestamp;
            
            return DateTime.MinValue;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error loading last check timestamp");
            return DateTime.MinValue;
        }
    }

    private async Task SaveLastCheckTimestampAsync(DateTime timestamp)
    {
        try
        {
            await _secureStorage.SetAsync(LastCheckKey, timestamp.ToString("O"));
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error saving last check timestamp");
        }
    }

    private async Task<List<Announcement>> LoadLastAnnouncementsAsync()
    {
        try
        {
            var json = await _secureStorage.GetAsync(LastAnnouncementsKey);
            if (string.IsNullOrWhiteSpace(json))
                return new List<Announcement>();
            
            var announcements = JsonSerializer.Deserialize<List<Announcement>>(json, Options);
            return announcements ?? new List<Announcement>();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error loading last announcements");
            return new List<Announcement>();
        }
    }

    private async Task SaveLastAnnouncementsAsync(List<Announcement> announcements)
    {
        try
        {
            var json = JsonSerializer.Serialize(announcements, Options);
            await _secureStorage.SetAsync(LastAnnouncementsKey, json);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "NoticeboardNotificationService: Error saving last announcements");
        }
    }

    #endregion
}