using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class CoupleService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<CoupleRecord?> GetCoupleAsync(string id, CancellationToken ct = default)
    {
        // The GraphQL schema on the server uses BigInt for ids; avoid declaring a variable of type ID
        // and instead embed the id directly in the query (same pattern used elsewhere in the app).
        var safeId = id?.Replace("\\", "\\\\").Replace("\"", "\\\"") ?? string.Empty;
        var query = "query MyQuery { couple(id: \"" + safeId + "\") { createdAt id man { id firstName lastName phone } woman { id firstName lastName phone } } }";

        var gqlReq = new { query };
        var json = JsonSerializer.Serialize(gqlReq, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var errorBody = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
        }

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<CoupleData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        return data?.Data?.Couple;
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

    private sealed class CoupleData
    {
        [JsonPropertyName("couple")] public CoupleRecord? Couple { get; set; }
    }

    public sealed class CoupleRecord
    {
        [JsonPropertyName("createdAt")] public string? CreatedAt { get; set; }
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("man")] public Person? Man { get; set; }
        [JsonPropertyName("woman")] public Person? Woman { get; set; }
    }

    public sealed class Person
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
        [JsonPropertyName("phone")] public string? Phone { get; set; }
    }
}
