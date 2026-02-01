using System.Numerics;
using TkOlympApp.Models.Leaderboards;

namespace TkOlympApp.Services.Abstractions;

public interface ILeaderboardService
{
    Task<List<ScoreboardDto>> GetScoreboardsAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default);
    Task<(List<ScoreboardDto> Scoreboards, string RawBody)> GetScoreboardsWithRawAsync(BigInteger? cohortId = null, DateTime? since = null, DateTime? until = null, CancellationToken ct = default);
}

