using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class GraphQlClient
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };
    static GraphQlClient()
    {
        // Register shared converters used by services (e.g., BigInteger handling)
        Options.Converters.Add(new BigIntegerJsonConverter());
    }

    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T Data { get; set; } = default!;
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    public static async Task<T> PostAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        var req = new GraphQlRequest { Query = query, Variables = variables };
        var json = JsonSerializer.Serialize(req, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        
        if (!resp.IsSuccessStatusCode)
        {
            // For error responses, read as string for better error message
            var body = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
        }

        // Use async stream deserialization to avoid blocking UI thread on large responses
        await using var stream = await resp.Content.ReadAsStreamAsync(ct);
        var data = await JsonSerializer.DeserializeAsync<GraphQlResponse<T>>(stream, Options, ct);
        
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        if (data == null) return default!;
        return data.Data;
    }

    // Returns parsed data together with raw response body for debugging purposes
    // Note: This method still reads full body for debugging - use PostAsync for better performance
    public static async Task<(T Data, string Raw)> PostWithRawAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        var req = new GraphQlRequest { Query = query, Variables = variables };
        var json = JsonSerializer.Serialize(req, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        var body = await resp.Content.ReadAsStringAsync(ct);
        if (!resp.IsSuccessStatusCode)
        {
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
        }

        var data = JsonSerializer.Deserialize<GraphQlResponse<T>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        if (data == null) return (default!, body);
        return (data.Data, body);
    }
}
