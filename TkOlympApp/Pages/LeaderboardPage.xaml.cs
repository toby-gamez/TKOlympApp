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
                ScoreboardCollection!.ItemsSource = _rows;
        }
        catch (Exception ex)
        {
            // If initialization fails, show a simple fallback UI instead of crashing the app
            Content = new Label
            {
                Text = LocalizationService.Get("Error_Loading_Prefix") + ex.Message,
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
                await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
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
                LoadingIndicator!.IsVisible = true;
                LoadingIndicator.IsRunning = true;

            // Hide the list while loading so the previous content isn't visible behind the loader
            try { if (ScoreboardCollection != null) ScoreboardCollection.IsVisible = false; } catch { }

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

            // Avoid touching UI types on a background thread: compute only lightweight strings there
            var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
            var processedData = await Task.Run(() =>
            {
                var tmp = new List<(string PersonDisplay, string RankingDisplay, string TotalScoreDisplay, bool IsCurrentUser, int? Ranking)>();

                foreach (var item in list)
                {
                    var rankSuffix = string.IsNullOrEmpty(item.RankingDisplay) ? string.Empty : item.RankingDisplay + ".";
                    var isCurrent = !string.IsNullOrEmpty(currentFullName) &&
                                    string.Equals(item.PersonDisplay?.Trim(), currentFullName, StringComparison.OrdinalIgnoreCase);

                    int? ranking = item.Ranking.HasValue ? (int?)item.Ranking.Value : null;

                    tmp.Add((item.PersonDisplay ?? string.Empty,
                             rankSuffix,
                             string.IsNullOrEmpty(item.TotalScoreDisplay) ? string.Empty : item.TotalScoreDisplay + " b",
                             isCurrent,
                             ranking));
                }

                return tmp;
            });

            // Map lightweight data to UI types on the UI thread
            Dispatcher.Dispatch(() =>
            {
                try
                {
                    var rows = new List<LeaderboardRow>();
                    Color ThemeColor(string lightHex, string darkHex) => (theme == AppTheme.Dark) ? Color.FromArgb(darkHex) : Color.FromArgb(lightHex);

                    foreach (var d in processedData)
                    {
                        var row = new LeaderboardRow
                        {
                            PersonDisplay = d.PersonDisplay,
                            RankingDisplay = d.RankingDisplay,
                            TotalScoreDisplay = d.TotalScoreDisplay,
                            IsCurrentUser = d.IsCurrentUser,
                            BackgroundColor = Colors.Transparent,
                            TextColor = (theme == AppTheme.Dark) ? Color.FromArgb("#E0E0E0") : Color.FromArgb("#222222")
                        };

                        if (d.Ranking.HasValue)
                        {
                            try
                            {
                                var r = d.Ranking.Value;
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

                        rows.Add(row);
                    }

                    ScoreboardCollection!.ItemsSource = rows;
                    // Show the list now that new items are assigned
                    try { if (ScoreboardCollection != null) ScoreboardCollection.IsVisible = true; } catch { }
                }
                catch
                {
                    _rows.Clear();
                    foreach (var d in processedData)
                    {
                        var row = new LeaderboardRow
                        {
                            PersonDisplay = d.PersonDisplay,
                            RankingDisplay = d.RankingDisplay,
                            TotalScoreDisplay = d.TotalScoreDisplay,
                            IsCurrentUser = d.IsCurrentUser,
                            BackgroundColor = Colors.Transparent,
                            TextColor = (theme == AppTheme.Dark) ? Color.FromArgb("#E0E0E0") : Color.FromArgb("#222222")
                        };

                        if (d.Ranking.HasValue)
                        {
                            try
                            {
                                var r = d.Ranking.Value;
                                if (r == 1)
                                {
                                    row.BackgroundColor = (theme == AppTheme.Dark) ? Color.FromArgb("#3B2A00") : Color.FromArgb("#FFF9E6");
                                    row.TextColor = (theme == AppTheme.Dark) ? Color.FromArgb("#FFD700") : Color.FromArgb("#5A3D00");
                                }
                                else if (r == 2)
                                {
                                    row.BackgroundColor = (theme == AppTheme.Dark) ? Color.FromArgb("#2B2F31") : Color.FromArgb("#F2F5F7");
                                    row.TextColor = (theme == AppTheme.Dark) ? Color.FromArgb("#E0E0E0") : Color.FromArgb("#37474F");
                                }
                                else if (r == 3)
                                {
                                    row.BackgroundColor = (theme == AppTheme.Dark) ? Color.FromArgb("#3A2618") : Color.FromArgb("#FAF0EC");
                                    row.TextColor = (theme == AppTheme.Dark) ? Color.FromArgb("#F0C49A") : Color.FromArgb("#5D4037");
                                }
                            }
                            catch { }
                        }

                        _rows.Add(row);
                    }
                    try { if (ScoreboardCollection != null) ScoreboardCollection.IsVisible = true; } catch { }
                }
            });
        }
            catch (Exception ex)
            {
                await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
            }
        finally
        {
                try
                {
                    LoadingIndicator!.IsRunning = false;
                    LoadingIndicator.IsVisible = false;
                try
                {
                    if (LeaderboardRefresh != null)
                        LeaderboardRefresh!.IsRefreshing = false;
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
