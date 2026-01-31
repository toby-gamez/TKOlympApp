using System.Text;
using System.Numerics;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class LeaderboardService
{
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
        var (list, _) = await GetScoreboardsWithRawAsync(cohortId, since, until, ct);
        return list;
    }

    /// <summary>
    /// Returns parsed scoreboard entries along with the raw JSON response body.
    /// Useful for debugging or showing the raw response in the UI.
    /// </summary>
    public static async Task<(List<ScoreboardDto> Scoreboards, string RawBody)> GetScoreboardsWithRawAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default)
    {
        var query = @"query Scoreboard($cohortId: BigInt, $since: Date, $until: Date) {
    getCurrentTenant {
        id
        cohortsList(condition: { isVisible: true }, orderBy: [NAME_ASC]) {
            id
            name
        }
    }
    scoreboardEntriesList(cohortId: $cohortId, since: $since, until: $until) {
        totalScore
        lessonTotalScore
        groupTotalScore
        eventTotalScore
        manualTotalScore
        ranking
        personId
        cohortId
        cohort {
            id
            name
        }
        person {
            firstName
            lastName
            id
        }
    }
}";

        // Variables: cohortId if provided, default since = yesterday, until = today (date-only yyyy-MM-dd)
        var variables = new Dictionary<string, object>();
        if (cohortId.HasValue)
            variables["cohortId"] = cohortId.Value;

        var sinceDate = since ?? new DateTime(2025, 9, 1, 0, 0, 0, DateTimeKind.Utc);
        var untilDate = until ?? DateTime.UtcNow.AddDays(-1);
        variables["since"] = sinceDate.ToString("yyyy-MM-dd");
        variables["until"] = untilDate.ToString("yyyy-MM-dd");
        query = query; // keep local variable
        var (data, raw) = await GraphQlClient.PostWithRawAsync<ScoreboardsData>(query, variables, ct);
        return (data?.ScoreboardEntriesList ?? new List<ScoreboardDto>(), raw);
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
