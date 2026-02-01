using System.Numerics;
using System.Text.Json.Serialization;
using TkOlympApp.Services.Abstractions;
using static TkOlympApp.Services.LeaderboardService;

namespace TkOlympApp.Services;

public sealed class LeaderboardServiceImplementation : ILeaderboardService
{
    private readonly IGraphQlClient _graphQlClient;

    public LeaderboardServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
    }

    public async Task<List<ScoreboardDto>> GetScoreboardsAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default)
    {
        var (list, _) = await GetScoreboardsWithRawAsync(cohortId, since, until, ct);
        return list;
    }

    public async Task<(List<ScoreboardDto> Scoreboards, string RawBody)> GetScoreboardsWithRawAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default)
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

        var variables = new Dictionary<string, object>();
        if (cohortId.HasValue)
            variables["cohortId"] = cohortId.Value;

        var sinceDate = since ?? new DateTime(2025, 9, 1, 0, 0, 0, DateTimeKind.Utc);
        var untilDate = until ?? DateTime.UtcNow.AddDays(-1);
        variables["since"] = sinceDate.ToString("yyyy-MM-dd");
        variables["until"] = untilDate.ToString("yyyy-MM-dd");

        var (data, raw) = await _graphQlClient.PostWithRawAsync<ScoreboardsData>(query, variables, ct);
        return (data?.ScoreboardEntriesList ?? new List<ScoreboardDto>(), raw);
    }

    private sealed class ScoreboardsData
    {
        [JsonPropertyName("scoreboardEntriesList")] public List<ScoreboardDto>? ScoreboardEntriesList { get; set; }
        [JsonPropertyName("getCurrentTenant")] public TenantDto? GetCurrentTenant { get; set; }
    }
}
