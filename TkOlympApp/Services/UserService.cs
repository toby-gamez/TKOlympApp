using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class UserService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

        public static async Task<CurrentUser?> GetCurrentUserAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = "query MyQuery($versionId: String!) { getCurrentUser(versionId: $versionId) { uEmail uJmeno uLogin uCreatedAt createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }",
            Variables = new Dictionary<string, object> { { "versionId", "" } }
        };

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<CurrentUserData>>(body, Options);
        return data?.Data?.GetCurrentUser;
    }

    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
    }

    private sealed class CurrentUserData
    {
        [JsonPropertyName("getCurrentUser")] public CurrentUser? GetCurrentUser { get; set; }
    }

    public sealed record CurrentUser(
        string UEmail,
        string? UJmeno,
        string ULogin,
        DateTime UCreatedAt,
        DateTime? CreatedAt,
        long Id,
        DateTime? LastActiveAt,
        DateTime? LastLogin,
        long TenantId,
        string? UPrijmeni,
        DateTime UpdatedAt
    );
    // couples-related helpers removed
}
