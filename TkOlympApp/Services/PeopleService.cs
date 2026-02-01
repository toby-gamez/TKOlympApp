using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Generic;
using System.Linq;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class PeopleService
{
    private static IPeopleService? _instance;

    private static IPeopleService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<IPeopleService>();
            return _instance;
        }
    }

    internal static void SetInstance(IPeopleService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<List<Person>> GetPeopleAsync(CancellationToken ct = default)
    {
        return await Instance.GetPeopleAsync(ct);
    }

    

    private sealed class PeopleData
    {
        [JsonPropertyName("people")] public People? People { get; set; }
    }

    private sealed class People
    {
        [JsonPropertyName("nodes")] public List<Person>? Nodes { get; set; }
    }

    public sealed record Person(
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("birthDate")] string? BirthDate,
        [property: JsonPropertyName("cohortMembershipsList")] List<CohortMembership>? CohortMembershipsList
    )
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

    public sealed record CohortMembership(
        [property: JsonPropertyName("cohort")] Cohort? Cohort
    );

    public sealed record Cohort(
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("colorRgb")] string? ColorRgb,
        [property: JsonPropertyName("isVisible")] bool? IsVisible
    );
}
