using System.Text;
using System.Numerics;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class LeaderboardService
{
    private static ILeaderboardService? _instance;

    private static ILeaderboardService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<ILeaderboardService>();
            return _instance;
        }
    }

    internal static void SetInstance(ILeaderboardService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    // Register BigInteger converter so BigInt values from GraphQL (which may come as numbers or strings)
    static LeaderboardService()
    {
        Options.Converters.Add(new BigIntegerJsonConverter());
    }

    

    public static async Task<List<ScoreboardDto>> GetScoreboardsAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default)
    {
        return await Instance.GetScoreboardsAsync(cohortId, since, until, ct);
    }

    /// <summary>
    /// Returns parsed scoreboard entries along with the raw JSON response body.
    /// Useful for debugging or showing the raw response in the UI.
    /// </summary>
    public static async Task<(List<ScoreboardDto> Scoreboards, string RawBody)> GetScoreboardsWithRawAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default)
    {
        return await Instance.GetScoreboardsWithRawAsync(cohortId, since, until, ct);
    }

    

    private sealed class ScoreboardsData
    {
        [JsonPropertyName("scoreboardEntriesList")] public List<ScoreboardDto>? ScoreboardEntriesList { get; set; }
        [JsonPropertyName("getCurrentTenant")] public TenantDto? GetCurrentTenant { get; set; }
    }

    public sealed record ScoreboardDto(
        [property: JsonPropertyName("person")] PersonDto? Person,
        [property: JsonPropertyName("ranking")] BigInteger? Ranking,
        [property: JsonPropertyName("personId")] BigInteger? PersonId,
        [property: JsonPropertyName("cohortId")] BigInteger? CohortId,
        [property: JsonPropertyName("cohort")] CohortDto? Cohort,
        [property: JsonPropertyName("totalScore")] BigInteger? TotalScore,
        [property: JsonPropertyName("lessonTotalScore")] BigInteger? LessonTotalScore,
        [property: JsonPropertyName("groupTotalScore")] BigInteger? GroupTotalScore,
        [property: JsonPropertyName("eventTotalScore")] BigInteger? EventTotalScore,
        [property: JsonPropertyName("manualTotalScore")] BigInteger? ManualTotalScore
    )
    {
        public string RankingDisplay => Ranking?.ToString() ?? string.Empty;
        public string PersonDisplay => Person == null ? string.Empty : $"{Person.FirstName} {Person.LastName}".Trim();
        public string PersonIdDisplay => PersonId?.ToString() ?? Person?.Id?.ToString() ?? string.Empty;
        public string CohortIdDisplay => CohortId?.ToString() ?? string.Empty;
        public string CohortName => Cohort?.Name ?? string.Empty;
        public string TotalScoreDisplay => TotalScore?.ToString() ?? string.Empty;
    }

    public sealed record PersonDto(
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("id")] BigInteger? Id
    );

    public sealed record PersonScoreboardDto(
        [property: JsonPropertyName("totalScore")] BigInteger? TotalScore,
        [property: JsonPropertyName("personId")] BigInteger? PersonId
    );

    public sealed record TenantDto(
        [property: JsonPropertyName("id")] BigInteger? Id,
        [property: JsonPropertyName("cohortsList")] List<CohortDto>? CohortsList
    );

    public sealed record CohortDto(
        [property: JsonPropertyName("id")] BigInteger? Id,
        [property: JsonPropertyName("name")] string? Name
    );
}
