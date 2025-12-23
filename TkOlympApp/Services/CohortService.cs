using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class CohortService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<List<CohortGroup>> GetCohortGroupsAsync(CancellationToken ct = default)
    {
                var query = new GraphQlRequest
                {
                        Query = @"query Query {
    getCurrentTenant {
        id
        cohortsList(condition: { isVisible: true }, orderBy: [NAME_ASC]) {
            colorRgb
            name
            description
            location
        }
    }
}" 
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
        var data = JsonSerializer.Deserialize<GraphQlResponse<CohortGroupsData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        // API now returns cohortsList under getCurrentTenant; map it into the existing CohortGroup shape
        var cohorts = data?.Data?.GetCurrentTenant?.CohortsList ?? new List<CohortItem>();
        return new List<CohortGroup> { new CohortGroup(cohorts) };
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

    private sealed class CohortGroupsData
    {
        [JsonPropertyName("getCurrentTenant")] public GetCurrentTenantWrapper? GetCurrentTenant { get; set; }
    }

    private sealed class GetCurrentTenantWrapper
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("cohortsList")] public List<CohortItem>? CohortsList { get; set; }
    }

    public sealed record CohortGroup(
        [property: JsonPropertyName("cohortsList")] List<CohortItem>? CohortsList
    );

    public sealed record CohortItem(
        [property: JsonPropertyName("colorRgb")] string? ColorRgb,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("description")] string? Description,
        [property: JsonPropertyName("location")] string? Location
    );
}
