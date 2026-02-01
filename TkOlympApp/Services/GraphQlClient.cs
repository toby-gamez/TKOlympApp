using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

/// <summary>
/// GraphQL client implementation with dependency injection support.
/// Instances are created via DI with HttpClient from IHttpClientFactory.
/// </summary>
public class GraphQlClientImplementation : IGraphQlClient
{
    private readonly HttpClient _httpClient;
    private readonly JsonSerializerOptions _options;

    public GraphQlClientImplementation(HttpClient httpClient)
    {
        _httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        _options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = true
        };
        _options.Converters.Add(new BigIntegerJsonConverter());
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

    public async Task<T> PostAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        var req = new GraphQlRequest { Query = query, Variables = variables };
        var json = JsonSerializer.Serialize(req, _options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await _httpClient.PostAsync("", content, ct);
        
        if (!resp.IsSuccessStatusCode)
        {
            // For error responses, read as string for better error message
            var body = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
        }

        // Use async stream deserialization to avoid blocking UI thread on large responses
        await using var stream = await resp.Content.ReadAsStreamAsync(ct);
        var data = await JsonSerializer.DeserializeAsync<GraphQlResponse<T>>(stream, _options, ct);
        
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        if (data == null) return default!;
        return data.Data;
    }

    public async Task<(T Data, string Raw)> PostWithRawAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        var req = new GraphQlRequest { Query = query, Variables = variables };
        var json = JsonSerializer.Serialize(req, _options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await _httpClient.PostAsync("", content, ct);
        var body = await resp.Content.ReadAsStringAsync(ct);
        if (!resp.IsSuccessStatusCode)
        {
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
        }

        var data = JsonSerializer.Deserialize<GraphQlResponse<T>>(body, _options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        if (data == null) return (default!, body);
        return (data.Data, body);
    }
}

/// <summary>
/// Static wrapper for GraphQlClient to maintain backward compatibility during DI migration.
/// TODO: Remove after all call sites are updated to use IGraphQlClient via DI.
/// </summary>
public static class GraphQlClient
{
    private static IGraphQlClient? _instance;
    
    /// <summary>
    /// Lazily resolves the IGraphQlClient instance from DI container.
    /// </summary>
    private static IGraphQlClient Instance
    {
        get
        {
            if (_instance == null)
            {
                var services = Application.Current?.Handler?.MauiContext?.Services;
                if (services == null)
                    throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
                
                _instance = services.GetRequiredService<IGraphQlClient>();
            }
            return _instance;
        }
    }
    
    internal static void SetInstance(IGraphQlClient instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    public static async Task<T> PostAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        return await Instance.PostAsync<T>(query, variables, ct);
    }

    public static async Task<(T Data, string Raw)> PostWithRawAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        return await Instance.PostWithRawAsync<T>(query, variables, ct);
    }
}

