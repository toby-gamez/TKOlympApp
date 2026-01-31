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
            var query = "query MyQuery { tenantLocationsList { name } tenantTrainersList { person { id firstName lastName prefixTitle suffixTitle } guestPrice45Min { amount currency } guestPayout45Min { amount currency } isVisible } }";

            var data = await GraphQlClient.PostAsync<TenantData>(query, null, ct);

            var locations = data?.TenantLocationsList ?? new List<Location>();
            var trainers = (data?.TenantTrainersList ?? new List<TenantTrainer>())
                .Where(t => t.IsVisible == true)
                .ToList();
            return (locations, trainers);
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
        [property: JsonPropertyName("person")] Person? Person,
        [property: JsonPropertyName("guestPrice45Min")] Price? GuestPrice45Min,
        [property: JsonPropertyName("guestPayout45Min")] Price? GuestPayout45Min,
        [property: JsonPropertyName("isVisible")] bool? IsVisible
    );

    public sealed record Person(
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
        [property: JsonPropertyName("suffixTitle")] string? SuffixTitle
    );

    public sealed record Price(
        [property: JsonPropertyName("amount")] decimal? Amount,
        [property: JsonPropertyName("currency")] string? Currency
    );
}
