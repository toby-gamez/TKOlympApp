using System.Text.Json.Serialization;
using TkOlympApp.Services.Abstractions;
using static TkOlympApp.Services.CohortService;

namespace TkOlympApp.Services;

public sealed class CohortServiceImplementation : ICohortService
{
    private readonly IGraphQlClient _graphQlClient;

    public CohortServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
    }

    public async Task<List<CohortGroup>> GetCohortGroupsAsync(CancellationToken ct = default)
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

        var data = await _graphQlClient.PostAsync<CohortGroupsData>(query, null, ct);

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
}
