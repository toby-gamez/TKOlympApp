using System.Threading.Tasks;

namespace TkOlympApp.Services.Abstractions
{
    public interface IRuntimeState
    {
        string? CurrentPersonId { get; }
        string? CurrentCohortId { get; }
        Task SetCurrentPersonIdAsync(string? personId);
        Task SetCurrentCohortIdAsync(string? cohortId);
        Task InitializeAsync();
    }
}
