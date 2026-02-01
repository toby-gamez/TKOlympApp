using System.Numerics;
using System.Text.Json.Serialization;

namespace TkOlympApp.Models.Leaderboards;

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
