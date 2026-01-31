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
        var query = @"query Query {
    getCurrentTenant {
        id
        cohortsList(condition: { isVisible: true }, orderBy: [NAME_ASC]) {
            colorRgb
            name
            description
            location
        }
    }
}";

        var data = await GraphQlClient.PostAsync<CohortGroupsData>(query, null, ct);

        // API now returns cohortsList under getCurrentTenant; map it into the existing CohortGroup shape
        var cohorts = data?.GetCurrentTenant?.CohortsList ?? new List<CohortItem>();
        return new List<CohortGroup> { new CohortGroup(cohorts) };
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
