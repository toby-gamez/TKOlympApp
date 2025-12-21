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

    private sealed class BigIntegerJsonConverter : JsonConverter<BigInteger>
    {
        public override BigInteger Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        {
            // Support both JSON string and number tokens
            string s;
            if (reader.TokenType == JsonTokenType.String)
            {
                s = reader.GetString() ?? string.Empty;
            }
            else if (reader.TokenType == JsonTokenType.Number)
            {
                s = Encoding.UTF8.GetString(reader.ValueSpan);
            }
            else
            {
                throw new JsonException($"Unexpected token parsing BigInteger: {reader.TokenType}");
            }

            if (BigInteger.TryParse(s, out var value))
                return value;

            throw new JsonException($"Unable to parse BigInteger from '{s}'");
        }

        public override void Write(Utf8JsonWriter writer, BigInteger value, JsonSerializerOptions options)
        {
            // Write as string to avoid issues with extremely large numbers
            writer.WriteStringValue(value.ToString());
        }
    }

    public static async Task<List<ScoreboardDto>> GetScoreboardsAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = "query MyQuery { scoreboardsList { person { firstName lastName scoreboardsList { totalScore personId } } ranking eventTotalScore groupTotalScore lessonTotalScore totalScore } }"
        };

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var errorBody = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
        }

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<ScoreboardsData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? "Neznámá chyba GraphQL.";
            throw new InvalidOperationException(msg);
        }

        return data?.Data?.ScoreboardsList ?? new List<ScoreboardDto>();
    }

    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class ScoreboardsData
    {
        [JsonPropertyName("scoreboardsList")] public List<ScoreboardDto>? ScoreboardsList { get; set; }
    }

    public sealed record ScoreboardDto(
        [property: JsonPropertyName("person")] PersonDto? Person,
        // BigInt fields mapped to BigInteger for accurate numeric handling
        [property: JsonPropertyName("ranking")] BigInteger? Ranking,
        [property: JsonPropertyName("eventTotalScore")] BigInteger? EventTotalScore,
        [property: JsonPropertyName("groupTotalScore")] BigInteger? GroupTotalScore,
        [property: JsonPropertyName("lessonTotalScore")] BigInteger? LessonTotalScore,
        [property: JsonPropertyName("totalScore")] BigInteger? TotalScore
    )
    {
        public string RankingDisplay => Ranking?.ToString() ?? string.Empty;
        public string PersonDisplay => Person == null ? string.Empty : $"{Person.FirstName} {Person.LastName}".Trim();
        public string TotalScoreDisplay => TotalScore?.ToString() ?? string.Empty;
    }

    public sealed record PersonDto(
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("scoreboardsList")] List<PersonScoreboardDto>? ScoreboardsList
    );

    public sealed record PersonScoreboardDto(
        [property: JsonPropertyName("totalScore")] BigInteger? TotalScore,
        [property: JsonPropertyName("personId")] BigInteger? PersonId
    );
}
