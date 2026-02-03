using TkOlympApp.Models.Users;

namespace TkOlympApp.Services.Abstractions;

public interface IUserService
{
    string? CurrentPersonId { get; }
    string? CurrentCohortId { get; }

    void SetCurrentPersonId(string? personId);
    Task SetCurrentPersonIdAsync(string? personId, CancellationToken ct = default);

    void SetCurrentCohortId(string? cohortId);
    Task SetCurrentCohortIdAsync(string? cohortId, CancellationToken ct = default);

    Task InitializeAsync(CancellationToken ct = default);

    Task<CurrentUser?> GetCurrentUserAsync(CancellationToken ct = default);
    Task<List<CoupleInfo>> GetActiveCouplesFromUsersAsync(CancellationToken ct = default);
    Task<List<CohortInfo>> GetCohortsFromUsersAsync(CancellationToken ct = default);

    Task<string?> GetCurrentUserProxyIdAsync(CancellationToken ct = default);
}

