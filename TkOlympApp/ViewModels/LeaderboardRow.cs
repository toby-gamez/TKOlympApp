using Microsoft.Maui.Graphics;

namespace TkOlympApp.ViewModels;

public sealed class LeaderboardRow
{
    public string PersonDisplay { get; init; } = string.Empty;
    public string RankingDisplay { get; init; } = string.Empty;
    public string TotalScoreDisplay { get; init; } = string.Empty;
    public bool IsCurrentUser { get; init; }
    public Color BackgroundColor { get; set; } = Colors.Transparent;
    public Color TextColor { get; set; } = Colors.Black;
}
