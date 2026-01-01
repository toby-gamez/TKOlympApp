using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Generic;
using System.Linq;

namespace TkOlympApp.Services;

public static class PeopleService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<List<Person>> GetPeopleAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = "query MyQuery { people { nodes { id firstName lastName birthDate } } }"
        };

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<PeopleData>>(body, Options);
        return data?.Data?.People?.Nodes ?? new List<Person>();
    }

    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
    }

    private sealed class PeopleData
    {
        [JsonPropertyName("people")] public People? People { get; set; }
    }

    private sealed class People
    {
        [JsonPropertyName("nodes")] public List<Person>? Nodes { get; set; }
    }

    public sealed record Person(string? Id, string? FirstName, string? LastName, string? BirthDate)
    {
        public string FullName => string.Join(' ', new[] { FirstName, LastName }.Where(s => !string.IsNullOrWhiteSpace(s)));

        public string? DisplayBirthDate
        {
            get
            {
                if (string.IsNullOrWhiteSpace(BirthDate)) return null;
                if (System.DateTime.TryParse(BirthDate, out var dt))
                {
                    // Explicit Czech-style date format
                    return dt.ToString("dd.MM.yyyy");
                }
                return BirthDate;
            }
        }

        // Computed relative days until next birthday (null if unknown)
        public int? DaysUntilNextBirthday
        {
            get
            {
                if (string.IsNullOrWhiteSpace(BirthDate)) return null;
                if (!System.DateTime.TryParse(BirthDate, out var bd)) return null;
                var today = System.DateTime.Today;
                try
                {
                    var next = new System.DateTime(today.Year, bd.Month, bd.Day);
                    if (next < today) next = next.AddYears(1);
                    return (int)(next - today).TotalDays;
                }
                catch
                {
                    return null;
                }
            }
        }

        public bool IsBirthdayToday => DaysUntilNextBirthday.HasValue && DaysUntilNextBirthday.Value == 0;
    };
}
