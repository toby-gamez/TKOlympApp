using System.Text.Json.Serialization;

namespace TkOlympApp.Models.Tenants;

public sealed record Location(
    [property: JsonPropertyName("name")] string? Name
);

public sealed record TenantTrainer(
    [property: JsonPropertyName("person")] Person? Person,
    [property: JsonPropertyName("guestPrice45Min")] Price? GuestPrice45Min,
    [property: JsonPropertyName("guestPayout45Min")] Price? GuestPayout45Min,
    [property: JsonPropertyName("isVisible")] bool? IsVisible
);

public sealed record Person(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("firstName")] string? FirstName,
    [property: JsonPropertyName("lastName")] string? LastName,
    [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
    [property: JsonPropertyName("suffixTitle")] string? SuffixTitle
);

public sealed record Price(
    [property: JsonPropertyName("amount")] decimal? Amount,
    [property: JsonPropertyName("currency")] string? Currency
);
