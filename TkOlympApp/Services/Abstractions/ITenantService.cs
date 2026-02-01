using TenantLocation = TkOlympApp.Models.Tenants.Location;
using TenantTrainer = TkOlympApp.Models.Tenants.TenantTrainer;

namespace TkOlympApp.Services.Abstractions;

public interface ITenantService
{
    Task<(List<TenantLocation> Locations, List<TenantTrainer> Trainers)> GetLocationsAndTrainersAsync(CancellationToken ct = default);
}
