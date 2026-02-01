using System.Text.Json.Serialization;

namespace TkOlympApp.Models.Cohorts;

public sealed record CohortGroup(
    [property: JsonPropertyName("cohortsList")] List<CohortItem>? CohortsList
);

public sealed record CohortItem(
    [property: JsonPropertyName("colorRgb")] string? ColorRgb,
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("description")] string? Description,
    [property: JsonPropertyName("location")] string? Location
);
