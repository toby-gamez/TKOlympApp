using static TkOlympApp.Services.CoupleService;

namespace TkOlympApp.Services.Abstractions;

public interface ICoupleService
{
    Task<CoupleRecord?> GetCoupleAsync(string id, CancellationToken ct = default);
}
