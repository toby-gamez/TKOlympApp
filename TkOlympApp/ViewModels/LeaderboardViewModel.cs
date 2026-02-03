using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class LeaderboardViewModel : ViewModelBase
{
    private readonly ILeaderboardService _leaderboardService;
    private readonly IUserService _userService;

    public ObservableCollection<LeaderboardRow> Rows { get; } = new();

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _isLoading;

    [ObservableProperty]
    private bool _isListVisible;

    public LeaderboardViewModel(ILeaderboardService leaderboardService, IUserService userService)
    {
        _leaderboardService = leaderboardService ?? throw new ArgumentNullException(nameof(leaderboardService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadAsync();
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            IsLoading = true;
            IsListVisible = false;
            IsRefreshing = true;

            var list = await _leaderboardService.GetScoreboardsAsync();

            string? currentFullName = null;
            try
            {
                var current = await _userService.GetCurrentUserAsync();
                if (current != null)
                {
                    var parts = new[] { current.UJmeno ?? string.Empty, current.UPrijmeni ?? string.Empty };
                    currentFullName = string.Join(' ', parts.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
                }
            }
            catch
            {
                // ignore, no highlight
            }

            var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;

            var processedData = await Task.Run(() =>
                list.Select(item => new
                {
                    PersonDisplay = item.PersonDisplay ?? string.Empty,
                    RankingDisplay = string.IsNullOrEmpty(item.RankingDisplay) ? string.Empty : item.RankingDisplay + ".",
                    TotalScoreDisplay = string.IsNullOrEmpty(item.TotalScoreDisplay) ? string.Empty : item.TotalScoreDisplay + " b",
                    IsCurrentUser = !string.IsNullOrEmpty(currentFullName) &&
                                    string.Equals(item.PersonDisplay?.Trim(), currentFullName, StringComparison.OrdinalIgnoreCase),
                    Ranking = item.Ranking.HasValue ? (int?)item.Ranking.Value : null
                }).ToList());

            Rows.Clear();
            foreach (var data in processedData)
            {
                Rows.Add(CreateLeaderboardRow(data.PersonDisplay, data.RankingDisplay, data.TotalScoreDisplay,
                    data.IsCurrentUser, data.Ranking, theme));
            }

            IsListVisible = true;
        }
        finally
        {
            IsRefreshing = false;
            IsLoading = false;
        }
    }

    private static LeaderboardRow CreateLeaderboardRow(string personDisplay, string rankingDisplay,
        string totalScoreDisplay, bool isCurrentUser, int? ranking, AppTheme theme)
    {
        var isDark = theme == AppTheme.Dark;
        var row = new LeaderboardRow
        {
            PersonDisplay = personDisplay,
            RankingDisplay = rankingDisplay,
            TotalScoreDisplay = totalScoreDisplay,
            IsCurrentUser = isCurrentUser,
            BackgroundColor = Colors.Transparent,
            TextColor = isDark ? Color.FromArgb("#E0E0E0") : Color.FromArgb("#222222")
        };

        if (ranking.HasValue)
        {
            switch (ranking.Value)
            {
                case 1:
                    row.BackgroundColor = isDark ? Color.FromArgb("#3B2A00") : Color.FromArgb("#FFF9E6");
                    row.TextColor = isDark ? Color.FromArgb("#FFD700") : Color.FromArgb("#5A3D00");
                    break;
                case 2:
                    row.BackgroundColor = isDark ? Color.FromArgb("#2B2F31") : Color.FromArgb("#F2F5F7");
                    row.TextColor = isDark ? Color.FromArgb("#E0E0E0") : Color.FromArgb("#37474F");
                    break;
                case 3:
                    row.BackgroundColor = isDark ? Color.FromArgb("#3A2618") : Color.FromArgb("#FAF0EC");
                    row.TextColor = isDark ? Color.FromArgb("#F0C49A") : Color.FromArgb("#5D4037");
                    break;
            }
        }

        return row;
    }

}
