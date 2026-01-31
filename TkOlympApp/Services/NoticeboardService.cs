using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;

namespace TkOlympApp.Services;

public static class NoticeboardService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<List<Announcement>> GetMyAnnouncementsAsync(bool? sticky = null, CancellationToken ct = default)
    {
        var query = "query MyQuery($sticky: Boolean) { myAnnouncements(sticky: $sticky) { nodes { body createdAt id isSticky isVisible title author { id uJmeno uPrijmeni } updatedAt } } }";

        Dictionary<string, object>? variables = null;
        if (sticky.HasValue)
        {
            variables = new Dictionary<string, object> { { "sticky", sticky.Value } };
        }

        var data = await GraphQlClient.PostAsync<MyAnnouncementsData>(query, variables, ct);
        return data?.MyAnnouncements?.Nodes ?? new List<Announcement>();
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
        var query = "query MyQuery($id: BigInt!) { announcement(id: $id) { id title body createdAt updatedAt isVisible author { uJmeno uPrijmeni } } }";
        var variables = new Dictionary<string, object> { { "id", id } };

        var data = await GraphQlClient.PostAsync<AnnouncementData>(query, variables, ct);
        return data?.Announcement;
    }

    public static async Task<List<Announcement>> GetStickyAnnouncementsAsync(CancellationToken ct = default)
    {
        return await GetMyAnnouncementsAsync(sticky: true, ct);
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
