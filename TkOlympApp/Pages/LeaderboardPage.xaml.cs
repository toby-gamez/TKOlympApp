using System.Collections.ObjectModel;
using Microsoft.Maui.Controls;
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
                await DisplayAlert("Chyba", ex.Message, "OK");
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
            if (list.Count > 0)
            {
                // use the first item's totals as informational header (they are identical per response)
                EventTotalLabel.Text = list[0].EventTotalScore?.ToString() ?? string.Empty;
                GroupTotalLabel.Text = list[0].GroupTotalScore?.ToString() ?? string.Empty;
                LessonTotalLabel.Text = list[0].LessonTotalScore?.ToString() ?? string.Empty;
            }

            // Try to get current user to allow highlighting their row
            string? currentFullName = null;
            try
            {
                var current = await UserService.GetCurrentUserAsync();
                if (current != null)
                {
                    currentFullName = string.Join(' ', new[] { current.UJmeno, current.UPrijmeni }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
                }
            }
            catch
            {
                // ignore failures fetching current user; highlighting will be disabled
            }

            foreach (var item in list)
            {
                var row = new LeaderboardRow
                {
                    PersonDisplay = item.PersonDisplay,
                    RankingDisplay = string.IsNullOrEmpty(item.RankingDisplay) ? string.Empty : item.RankingDisplay + ".",
                    TotalScoreDisplay = string.IsNullOrEmpty(item.TotalScoreDisplay) ? string.Empty : item.TotalScoreDisplay + "b",
                    IsCurrentUser = !string.IsNullOrEmpty(currentFullName) &&
                                    string.Equals(item.PersonDisplay?.Trim(), currentFullName, StringComparison.OrdinalIgnoreCase)
                };

                _rows.Add(row);
            }
        }
        catch (Exception ex)
        {
            await DisplayAlert("Chyba", ex.Message, "OK");
        }
        finally
        {
            try
            {
                LoadingIndicator.IsRunning = false;
                LoadingIndicator.IsVisible = false;
            }
            catch
            {
                // ignore failures updating UI elements
            }
        }
    }

    private sealed class LeaderboardRow
    {
        public string PersonDisplay { get; init; } = string.Empty;
        public string RankingDisplay { get; init; } = string.Empty;
        public string TotalScoreDisplay { get; init; } = string.Empty;
        public bool IsCurrentUser { get; init; }
    }
}
