using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class CohortService
{
    private static ICohortService? _instance;

    private static ICohortService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<ICohortService>();
            return _instance;
        }
    }

    internal static void SetInstance(ICohortService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<List<CohortGroup>> GetCohortGroupsAsync(CancellationToken ct = default)
    {
        return await Instance.GetCohortGroupsAsync(ct);
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
