using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class TenantService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<(List<Location> Locations, List<TenantTrainer> Trainers)> GetLocationsAndTrainersAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = "query MyQuery { tenantLocationsList { name } tenantTrainersList { person { firstName lastName } } }"
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
        var data = JsonSerializer.Deserialize<GraphQlResponse<TenantData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? "Neznámá chyba GraphQL.";
            throw new InvalidOperationException(msg);
        }

        var locations = data?.Data?.TenantLocationsList ?? new List<Location>();
        var trainers = data?.Data?.TenantTrainersList ?? new List<TenantTrainer>();
        return (locations, trainers);
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

    private sealed class TenantData
    {
        [JsonPropertyName("tenantLocationsList")] public List<Location>? TenantLocationsList { get; set; }
        [JsonPropertyName("tenantTrainersList")] public List<TenantTrainer>? TenantTrainersList { get; set; }
    }

    public sealed record Location(
        [property: JsonPropertyName("name")] string? Name
    );

    public sealed record TenantTrainer(
        [property: JsonPropertyName("person")] Person? Person
    );

    public sealed record Person(
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName
    );
}
