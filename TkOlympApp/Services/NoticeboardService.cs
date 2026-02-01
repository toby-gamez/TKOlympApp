using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class NoticeboardService
{
    private static INoticeboardService? _instance;

    private static INoticeboardService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<INoticeboardService>();
            return _instance;
        }
    }

    internal static void SetInstance(INoticeboardService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<List<Announcement>> GetMyAnnouncementsAsync(bool? sticky = null, CancellationToken ct = default)
    {
        return await Instance.GetMyAnnouncementsAsync(sticky, ct);
    }

    

    private sealed class MyAnnouncementsData
    {
        [JsonPropertyName("myAnnouncements")] public AnnouncementsWrapper? MyAnnouncements { get; set; }
    }

    private sealed class AnnouncementsWrapper
    {
        [JsonPropertyName("nodes")] public List<Announcement>? Nodes { get; set; }
    }

    public sealed record Announcement(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("title")] string? Title,
        [property: JsonPropertyName("body")] string? Body,
        [property: JsonPropertyName("createdAt")] DateTime CreatedAt,
        [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
        [property: JsonPropertyName("isSticky")] bool IsSticky,
        [property: JsonPropertyName("isVisible")] bool IsVisible,
        [property: JsonPropertyName("author")] Author? Author
    );

    public static async Task<AnnouncementDetails?> GetAnnouncementAsync(long id, CancellationToken ct = default)
    {
        return await Instance.GetAnnouncementAsync(id, ct);
    }

    public static async Task<List<Announcement>> GetStickyAnnouncementsAsync(CancellationToken ct = default)
    {
        return await Instance.GetStickyAnnouncementsAsync(ct);
    }



    private sealed class AnnouncementData
    {
        [JsonPropertyName("announcement")] public AnnouncementDetails? Announcement { get; set; }
    }

    public sealed record AnnouncementDetails(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("title")] string? Title,
        [property: JsonPropertyName("body")] string? Body,
        [property: JsonPropertyName("createdAt")] DateTime CreatedAt,
        [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
        [property: JsonPropertyName("isVisible")] bool IsVisible,
        [property: JsonPropertyName("author")] Author? Author
    );

    public sealed record Author(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("uJmeno")] string? FirstName,
        [property: JsonPropertyName("uPrijmeni")] string? LastName
    );
}
