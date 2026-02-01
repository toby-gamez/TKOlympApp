using static TkOlympApp.Services.CohortService;

namespace TkOlympApp.Services.Abstractions;

public interface ICohortService
{
    Task<List<CohortGroup>> GetCohortGroupsAsync(CancellationToken ct = default);
}
