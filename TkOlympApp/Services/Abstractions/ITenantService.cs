using TenantLocation = TkOlympApp.Services.TenantService.Location;
using TenantTrainer = TkOlympApp.Services.TenantService.TenantTrainer;

namespace TkOlympApp.Services.Abstractions;

public interface ITenantService
{
    Task<(List<TenantLocation> Locations, List<TenantTrainer> Trainers)> GetLocationsAndTrainersAsync(CancellationToken ct = default);
}
