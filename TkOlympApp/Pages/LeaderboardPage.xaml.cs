using System.Collections.ObjectModel;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class LeaderboardPage : ContentPage
{
    private readonly ObservableCollection<LeaderboardRow> _rows = new();

    public LeaderboardPage()
    {
        try
        {
            InitializeComponent();
            ScoreboardCollection.ItemsSource = _rows;
        }
        catch (Exception ex)
        {
            Content = new Label
            {
                Text = LocalizationService.Get("Error_Loading_Prefix") + ex.Message,
                Padding = 20
            };
        }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        try
        {
            await LoadAsync();
        }
        catch (Exception ex)
        {
            await DisplayAlert(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
    }

    private async Task LoadAsync()
    {
        try
        {
            LoadingIndicator.IsVisible = true;
            LoadingIndicator.IsRunning = true;
            ScoreboardCollection.IsVisible = false;

            var list = await LeaderboardService.GetScoreboardsAsync();

            // Get current user for highlighting
            string? currentFullName = null;
            try
            {
                var current = await UserService.GetCurrentUserAsync();
                if (current != null)
                {
                    var parts = new[] { current.UJmeno ?? string.Empty, current.UPrijmeni ?? string.Empty };
                    currentFullName = string.Join(' ', parts.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
                }
            }
            catch
            {
                // Highlighting disabled if user fetch fails
            }

            var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;

            // Process data on background thread
            var processedData = await Task.Run(() =>
                list.Select(item => new
                {
                    PersonDisplay = item.PersonDisplay ?? string.Empty,
                    RankingDisplay = string.IsNullOrEmpty(item.RankingDisplay) ? string.Empty : item.RankingDisplay + ".",
                    TotalScoreDisplay = string.IsNullOrEmpty(item.TotalScoreDisplay) ? string.Empty : item.TotalScoreDisplay + " b",
                    IsCurrentUser = !string.IsNullOrEmpty(currentFullName) && 
                                    string.Equals(item.PersonDisplay?.Trim(), currentFullName, StringComparison.OrdinalIgnoreCase),
                    Ranking = item.Ranking.HasValue ? (int?)item.Ranking.Value : null
                }).ToList()
            );

            // Update UI on main thread
            _rows.Clear();
            foreach (var data in processedData)
            {
                _rows.Add(CreateLeaderboardRow(data.PersonDisplay, data.RankingDisplay, data.TotalScoreDisplay, 
                                                data.IsCurrentUser, data.Ranking, theme));
            }

            ScoreboardCollection.IsVisible = true;
        }
        catch (Exception ex)
        {
            await DisplayAlert(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            LoadingIndicator.IsRunning = false;
            LoadingIndicator.IsVisible = false;
            if (LeaderboardRefresh != null)
                LeaderboardRefresh.IsRefreshing = false;
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

    private async void OnLeaderboardRefresh(object? sender, EventArgs e)
    {
        await LoadAsync();
    }

    private sealed class LeaderboardRow
    {
        public string PersonDisplay { get; init; } = string.Empty;
        public string RankingDisplay { get; init; } = string.Empty;
        public string TotalScoreDisplay { get; init; } = string.Empty;
        public bool IsCurrentUser { get; init; }
        public Color BackgroundColor { get; set; } = Colors.Transparent;
        public Color TextColor { get; set; } = Colors.Black;
    }
}
