using Microsoft.Maui.Controls;
using System.Collections.ObjectModel;
using System.Net;
using System.Text.RegularExpressions;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class EventPage : ContentPage
{
    public long EventId { get; set; }

    private class RegistrationRow
    {
        public string Text { get; set; } = string.Empty;
        public string? Secondary { get; set; }
        public bool HasSecondary => !string.IsNullOrWhiteSpace(Secondary);
    }

    private readonly ObservableCollection<RegistrationRow> _registrations = new();
    private readonly ObservableCollection<string> _trainers = new();

    public EventPage()
    {
        InitializeComponent();
        RegistrationsCollection.ItemsSource = _registrations;
        TrainersCollection.ItemsSource = _trainers;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        if (EventId == 0) return;
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        try
        {
            var ev = await EventService.GetEventAsync(EventId);
            if (ev == null)
            {
                await DisplayAlert("Nenalezeno", "Událost nebyla nalezena.", "OK");
                return;
            }

            TitleLabel.Text = string.IsNullOrWhiteSpace(ev.Name) ? ev.LocationText ?? "Událost" : ev.Name;
            DescLabel.Text = HtmlToPlainText(ev.Description);
            SummaryLabel.Text = HtmlToPlainText(ev.Summary);
            var locName = ev.LocationText;
            LocationLabel.Text = string.IsNullOrWhiteSpace(locName) ? null : $"Místo konání: {locName}";
            LocationLabel.IsVisible = !string.IsNullOrWhiteSpace(LocationLabel.Text);
            DescFrame.IsVisible = !string.IsNullOrWhiteSpace(DescLabel.Text);
            SummaryFrame.IsVisible = !string.IsNullOrWhiteSpace(SummaryLabel.Text);
            CreatedAtLabel.Text = $"Vytvořeno: {ev.CreatedAt:dd.MM.yyyy HH:mm}";
            RegistrationOpenLabel.IsVisible = ev.IsRegistrationOpen;
            PublicLabel.IsVisible = ev.IsPublic;
            VisibleLabel.IsVisible = ev.IsVisible;
            CapacityLabel.Text = ev.Capacity.HasValue ? $"Kapacita: {ev.Capacity}" : "Kapacita: N/A";
            RegistrationsLabel.Text = $"Registrováno: {ev.EventRegistrations?.TotalCount ?? 0}";

            // Trainers (event level)
            _trainers.Clear();
            foreach (var t in ev.EventTrainersList ?? new List<TkOlympApp.Services.EventService.EventTrainer>())
            {
                if (t == null) continue;
                var name = t.Name?.Trim();
                var price = t.LessonPrice;
                string line = name;
                if (price != null && price.Amount != 0)
                {
                    var cur = price.Currency;
                    var priceText = $"{price.Amount:0.##} {cur}";
                    line = string.IsNullOrWhiteSpace(name) ? priceText : $"{name} {priceText}";
                }
                if (!string.IsNullOrWhiteSpace(line)) _trainers.Add(line);
            }
            TrainersFrame.IsVisible = _trainers.Count > 0;

            _registrations.Clear();
            foreach (var n in ev.EventRegistrations?.Nodes ?? new List<EventService.EventRegistrationNode>())
            {
                var man = n.Couple?.Man?.Name?.Trim() ?? string.Empty;
                var woman = n.Couple?.Woman?.Name?.Trim() ?? string.Empty;
                if (string.IsNullOrWhiteSpace(man) && string.IsNullOrWhiteSpace(woman))
                    continue; // drop empty rows

                string text = !string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman)
                    ? $"{man} - {woman}"
                    : (string.IsNullOrWhiteSpace(man) ? woman : man);

                var parts = new List<string>();
                var status = n.Couple?.Status;
                if (!string.IsNullOrWhiteSpace(status)) parts.Add($"Stav: {status}");
                if (n.Couple?.Active == false) parts.Add("Neaktivní");

                var trainers = n.Couple?.Man?.EventInstanceTrainersList;
                if (trainers != null && trainers.Count > 0)
                {
                    var trainerParts = new List<string>();
                    foreach (var t in trainers)
                    {
                        if (t == null) continue;
                        var tn = (t.Name ?? string.Empty).Trim();
                        var price = t.LessonPrice;
                        if (price != null && price.Amount != 0)
                        {
                            var cur = price.Currency;
                            trainerParts.Add(string.IsNullOrWhiteSpace(tn)
                                ? $"{price.Amount:0.##} {cur}"
                                : $"{tn} {price.Amount:0.##} {cur}");
                        }
                        else if (!string.IsNullOrWhiteSpace(tn))
                        {
                            trainerParts.Add(tn);
                        }
                    }
                    if (trainerParts.Count > 0)
                        parts.Add($"Trenéři: {string.Join(", ", trainerParts)}");
                }

                var secondary = parts.Count > 0 ? string.Join(" • ", parts) : null;
                _registrations.Add(new RegistrationRow { Text = text, Secondary = secondary });
            }

            RegistrationsFrame.IsVisible = _registrations.Count > 0;
        }
        catch (Exception ex)
        {
            await DisplayAlert("Chyba načtení", ex.Message, "OK");
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
        }
    }

    private async void OnReloadClicked(object? sender, EventArgs e)
    {
        await LoadAsync();
    }

    private static string? HtmlToPlainText(string? html)
    {
        if (string.IsNullOrWhiteSpace(html)) return html;
        // Normalize line breaks for common block/line elements
        var text = html;
        // 1) Normalize <br> variants and collapse consecutive <br><br> to a single <br>
        text = Regex.Replace(text, "<br\\s*/?>", "<br>", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "(?:<br>\\s*){2,}", "<br>", RegexOptions.IgnoreCase);
        // 2) Convert remaining single <br> to newline
        text = text.Replace("<br>", "\n");
        text = Regex.Replace(text, "</p>", "\n\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<p[^>]*>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<li[^>]*>", "\n• ", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</li>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</h[1-6]>", "\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<h[1-6][^>]*>", string.Empty, RegexOptions.IgnoreCase);
        // Remove any remaining tags
        text = Regex.Replace(text, "<[^>]+>", string.Empty);
        // Decode HTML entities
        text = WebUtility.HtmlDecode(text);
        // Trim excess whitespace
        return text.Trim();
    }
}
