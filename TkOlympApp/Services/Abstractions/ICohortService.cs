using TkOlympApp.Models.Cohorts;

namespace TkOlympApp.Services.Abstractions;

public interface ICohortService
{
    Task<List<CohortGroup>> GetCohortGroupsAsync(CancellationToken ct = default);
}

