using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TkOlympApp.Exceptions;
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
        try
        {
            var req = new GraphQlRequest { Query = query, Variables = variables };
            var json = JsonSerializer.Serialize(req, _options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _httpClient.PostAsync("", content, ct);

            if (!resp.IsSuccessStatusCode)
            {
                var body = await resp.Content.ReadAsStringAsync(ct);
                throw new ServiceException(
                        $"HTTP {(int)resp.StatusCode}",
                        isTransient: (int)resp.StatusCode >= 500,
                        httpStatusCode: (int)resp.StatusCode)
                    .WithContext("Body", body);
            }

            await using var stream = await resp.Content.ReadAsStreamAsync(ct);
            var data = await JsonSerializer.DeserializeAsync<GraphQlResponse<T>>(stream, _options, ct);

            if (data?.Errors != null && data.Errors.Count > 0)
            {
                var errors = data.Errors
                    .Select(e => e.Message)
                    .Where(m => !string.IsNullOrWhiteSpace(m))
                    .Cast<string>()
                    .ToList();
                var msg = errors.FirstOrDefault() ?? LocalizationService.Get("GraphQL_UnknownError") ?? "GraphQL error";
                throw new GraphQLException(msg, errors);
            }

            if (data == null) return default!;
            return data.Data;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (HttpRequestException ex)
        {
            throw new ServiceException("Network error", ex, isTransient: true);
        }
    }

    public async Task<(T Data, string Raw)> PostWithRawAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
    {
        try
        {
            var req = new GraphQlRequest { Query = query, Variables = variables };
            var json = JsonSerializer.Serialize(req, _options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _httpClient.PostAsync("", content, ct);
            var body = await resp.Content.ReadAsStringAsync(ct);
            if (!resp.IsSuccessStatusCode)
            {
                throw new ServiceException(
                        $"HTTP {(int)resp.StatusCode}",
                        isTransient: (int)resp.StatusCode >= 500,
                        httpStatusCode: (int)resp.StatusCode)
                    .WithContext("Body", body);
            }

            var data = JsonSerializer.Deserialize<GraphQlResponse<T>>(body, _options);
            if (data?.Errors != null && data.Errors.Count > 0)
            {
                var errors = data.Errors
                    .Select(e => e.Message)
                    .Where(m => !string.IsNullOrWhiteSpace(m))
                    .Cast<string>()
                    .ToList();
                var msg = errors.FirstOrDefault() ?? LocalizationService.Get("GraphQL_UnknownError") ?? "GraphQL error";
                throw new GraphQLException(msg, errors, rawResponse: body);
            }

            if (data == null) return (default!, body);
            return (data.Data, body);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (HttpRequestException ex)
        {
            throw new ServiceException("Network error", ex, isTransient: true);
        }
    }
}

