using System.Text.Json.Serialization;

namespace TkOlympApp.Models.People;

public sealed record PersonDetail(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("firstName")] string? FirstName,
    [property: JsonPropertyName("lastName")] string? LastName,
    [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
    [property: JsonPropertyName("suffixTitle")] string? SuffixTitle,
    [property: JsonPropertyName("bio")] string? Bio,
    [property: JsonPropertyName("birthDate")] string? BirthDate,
    [property: JsonPropertyName("email")] string? Email,
    [property: JsonPropertyName("phone")] string? Phone,
    [property: JsonPropertyName("gender")] string? Gender,
    [property: JsonPropertyName("isTrainer")] bool? IsTrainer,
    [property: JsonPropertyName("wdsfId")] string? WdsfId,
    [property: JsonPropertyName("cstsId")] string? CstsId,
    [property: JsonPropertyName("nationalIdNumber")] string? NationalIdNumber,
    [property: JsonPropertyName("nationality")] string? Nationality,
    [property: JsonPropertyName("address")] PersonAddress? Address,
    [property: JsonPropertyName("activeCouplesList")] List<PersonActiveCouple>? ActiveCouplesList,
    [property: JsonPropertyName("cohortMembershipsList")] List<PersonCohortMembership>? CohortMembershipsList
);

public sealed record PersonAddress(
    [property: JsonPropertyName("city")] string? City,
    [property: JsonPropertyName("conscriptionNumber")] string? ConscriptionNumber,
    [property: JsonPropertyName("district")] string? District,
    [property: JsonPropertyName("orientationNumber")] string? OrientationNumber,
    [property: JsonPropertyName("postalCode")] string? PostalCode,
    [property: JsonPropertyName("region")] string? Region,
    [property: JsonPropertyName("street")] string? Street
);

public sealed record PersonActiveCouple(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("man")] PersonActiveMember? Man,
    [property: JsonPropertyName("woman")] PersonActiveMember? Woman
);

public sealed record PersonActiveMember(
    [property: JsonPropertyName("firstName")] string? FirstName,
    [property: JsonPropertyName("lastName")] string? LastName
);

public sealed record PersonCohortMembership(
    [property: JsonPropertyName("cohort")] PersonCohort? Cohort
);

public sealed record PersonCohort(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("colorRgb")] string? ColorRgb,
    [property: JsonPropertyName("ordering")] int? Ordering,
    [property: JsonPropertyName("isVisible")] bool? IsVisible
);

public sealed record PersonUpdateRequest(
    string? Bio,
    DateTime? BirthDate,
    bool BirthDateSet,
    string? CstsId,
    string? Email,
    string? FirstName,
    string? LastName,
    string? NationalIdNumber,
    string? Nationality,
    string? Phone,
    string? WdsfId,
    string? PrefixTitle,
    string? SuffixTitle,
    string? Gender,
    PersonAddress? Address
);
