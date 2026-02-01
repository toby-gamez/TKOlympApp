using System.Text.Json.Serialization;
using TkOlympApp.Services.Abstractions;
using System.Linq;
using TenantLocation = TkOlympApp.Services.TenantService.Location;
using TenantTrainer = TkOlympApp.Services.TenantService.TenantTrainer;

namespace TkOlympApp.Services;

public sealed class TenantServiceImplementation : ITenantService
{
    private readonly IGraphQlClient _graphQlClient;

    public TenantServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
    }

    public async Task<(List<TenantLocation> Locations, List<TenantTrainer> Trainers)> GetLocationsAndTrainersAsync(CancellationToken ct = default)
    {
        var query =
            "query MyQuery { tenantLocationsList { name } tenantTrainersList { person { id firstName lastName prefixTitle suffixTitle } guestPrice45Min { amount currency } guestPayout45Min { amount currency } isVisible } }";

        var data = await _graphQlClient.PostAsync<TenantData>(query, null, ct);

        var locations = data?.TenantLocationsList ?? new List<TenantLocation>();
        var trainers = (data?.TenantTrainersList ?? new List<TenantTrainer>())
            .Where(t => t.IsVisible == true)
            .ToList();
        return (locations, trainers);
    }

    private sealed class TenantData
    {
        [JsonPropertyName("tenantLocationsList")] public List<TenantLocation>? TenantLocationsList { get; set; }
        [JsonPropertyName("tenantTrainersList")] public List<TenantTrainer>? TenantTrainersList { get; set; }
    }
}
