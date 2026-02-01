using TkOlympApp.Models.Couples;

namespace TkOlympApp.Services.Abstractions;

public interface ICoupleService
{
    Task<CoupleRecord?> GetCoupleAsync(string id, CancellationToken ct = default);
}

