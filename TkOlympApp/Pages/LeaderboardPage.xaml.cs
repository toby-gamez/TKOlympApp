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
            // If initialization fails, show a simple fallback UI instead of crashing the app
            Content = new Label
            {
                Text = "Chyba při inicializaci stránky: " + ex.Message,
                Padding = 20
            };
            return;
        }
    }

    protected override async void OnAppearing()
    {
        try
        {
            base.OnAppearing();
            await LoadAsync();
        }
        catch (Exception ex)
        {
            try
            {
                await DisplayAlertAsync("Chyba", ex.Message, "OK");
            }
            catch
            {
                // ignore secondary failures when displaying alert
            }
        }
    }

    private async Task LoadAsync()
    {
        try
        {
            LoadingIndicator.IsVisible = true;
            LoadingIndicator.IsRunning = true;

            _rows.Clear();
            var list = await LeaderboardService.GetScoreboardsAsync();
            // totals were removed from the UI; nothing to set here

            // Try to get current user to allow highlighting their row
            string? currentFullName = null;
            try
            {
                var current = await UserService.GetCurrentUserAsync();
                if (current != null)
                {
                    // Coalesce potentially-null name parts to avoid nullability warnings
                    var parts = new[] { current.UJmeno ?? string.Empty, current.UPrijmeni ?? string.Empty };
                    currentFullName = string.Join(' ', parts.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
                }
            }
            catch
            {
                // ignore failures fetching current user; highlighting will be disabled
            }

            foreach (var item in list)
            {
                // Determine theme to pick readable colors
                var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
                Color ThemeColor(string lightHex, string darkHex) => (theme == AppTheme.Dark) ? Color.FromArgb(darkHex) : Color.FromArgb(lightHex);

                var rankSuffix = string.IsNullOrEmpty(item.RankingDisplay) ? string.Empty : item.RankingDisplay + ".";
                var isCurrent = !string.IsNullOrEmpty(currentFullName) &&
                                string.Equals(item.PersonDisplay?.Trim(), currentFullName, StringComparison.OrdinalIgnoreCase);

                var row = new LeaderboardRow
                {
                    PersonDisplay = item.PersonDisplay ?? string.Empty,
                    RankingDisplay = rankSuffix,
                    TotalScoreDisplay = string.IsNullOrEmpty(item.TotalScoreDisplay) ? string.Empty : item.TotalScoreDisplay + " b",
                    IsCurrentUser = isCurrent,
                    BackgroundColor = Colors.Transparent,
                    TextColor = (theme == AppTheme.Dark) ? Color.FromArgb("#E0E0E0") : Color.FromArgb("#222222")
                };

                if (item.Ranking.HasValue)
                {
                    try
                    {
                        var r = (int)item.Ranking.Value;
                        if (r == 1)
                        {
                            row.BackgroundColor = ThemeColor("#FFF9E6", "#3B2A00");
                            row.TextColor = ThemeColor("#5A3D00", "#FFD700");
                        }
                        else if (r == 2)
                        {
                            row.BackgroundColor = ThemeColor("#F2F5F7", "#2B2F31");
                            row.TextColor = ThemeColor("#37474F", "#E0E0E0");
                        }
                        else if (r == 3)
                        {
                            row.BackgroundColor = ThemeColor("#FAF0EC", "#3A2618");
                            row.TextColor = ThemeColor("#5D4037", "#F0C49A");
                        }
                    }
                    catch
                    {
                        // ignore conversion issues
                    }
                }

                _rows.Add(row);
            }
        }
            catch (Exception ex)
            {
                await DisplayAlertAsync("Chyba", ex.Message, "OK");
            }
        finally
        {
            try
            {
                LoadingIndicator.IsRunning = false;
                LoadingIndicator.IsVisible = false;
                try
                {
                    if (LeaderboardRefresh != null)
                        LeaderboardRefresh.IsRefreshing = false;
                }
                catch
                {
                    // ignore
                }
            }
            catch
            {
                // ignore failures updating UI elements
            }
        }
    }

    private async void OnLeaderboardRefresh(object? sender, EventArgs e)
    {
        await LoadAsync();
        try
        {
            if (LeaderboardRefresh != null)
                LeaderboardRefresh.IsRefreshing = false;
        }
        catch
        {
            // ignore
        }
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
