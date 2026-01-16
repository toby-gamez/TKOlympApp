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
        var query = new GraphQlRequest
        {
            Query = "query MyQuery($sticky: Boolean) { myAnnouncements(sticky: $sticky) { nodes { body createdAt id isSticky isVisible title author { id uJmeno uPrijmeni } updatedAt } } }"
        };

        if (sticky.HasValue)
        {
            query.Variables = new Dictionary<string, object> { { "sticky", sticky.Value } };
        }

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var errorBody = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
        }

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<MyAnnouncementsData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        return data?.Data?.MyAnnouncements?.Nodes ?? new List<Announcement>();
    }

    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
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
        var query = new GraphQlRequest
        {
            Query = "query MyQuery($id: BigInt!) { announcement(id: $id) { id title body createdAt updatedAt isVisible author { uJmeno uPrijmeni } } }",
            Variables = new Dictionary<string, object> { { "id", id } }
        };

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var errorBody = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
        }

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<AnnouncementData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        return data?.Data?.Announcement;
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
