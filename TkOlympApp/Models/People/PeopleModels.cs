using System.Text.Json.Serialization;

namespace TkOlympApp.Models.People;

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
            if (DateTime.TryParse(BirthDate, out var dt)) return dt.ToString("dd.MM.yyyy");
            return BirthDate;
        }
    }

    public int? DaysUntilNextBirthday
    {
        get
        {
            if (string.IsNullOrWhiteSpace(BirthDate)) return null;
            if (!DateTime.TryParse(BirthDate, out var bd)) return null;
            var today = DateTime.Today;
            try
            {
                var next = new DateTime(today.Year, bd.Month, bd.Day);
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
}

public sealed record CohortMembership(
    [property: JsonPropertyName("cohort")] Cohort? Cohort
);

public sealed record Cohort(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("colorRgb")] string? ColorRgb,
    [property: JsonPropertyName("isVisible")] bool? IsVisible
);
