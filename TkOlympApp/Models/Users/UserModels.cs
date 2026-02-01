namespace TkOlympApp.Models.Users;

public sealed record CurrentUser(
    string UEmail,
    string? UJmeno,
    string ULogin,
    DateTime CreatedAt,
    long Id,
    DateTime? LastActiveAt,
    DateTime? LastLogin,
    long TenantId,
    string? UPrijmeni,
    DateTime UpdatedAt
);

public sealed record CoupleInfo(string ManName, string WomanName, string? Id);

public sealed record CohortInfo(string? Id, string? ColorRgb, string Name);
