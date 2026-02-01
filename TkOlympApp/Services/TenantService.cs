using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class TenantService
{
    private static ITenantService? _instance;

    private static ITenantService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<ITenantService>();
            return _instance;
        }
    }

    internal static void SetInstance(ITenantService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

        public static async Task<(List<Location> Locations, List<TenantTrainer> Trainers)> GetLocationsAndTrainersAsync(CancellationToken ct = default)
        {
            return await Instance.GetLocationsAndTrainersAsync(ct);
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
