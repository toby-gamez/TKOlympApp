using Microsoft.Maui.Controls;
using System.Collections.ObjectModel;
using System.Net;
using System.Text.RegularExpressions;
using System.Globalization;
using System.Diagnostics;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
[QueryProperty(nameof(SinceParam), "since")]
[QueryProperty(nameof(UntilParam), "until")]
public partial class EventPage : ContentPage
{
    private long _eventId;
    private string? _sinceParam;
    private string? _untilParam;
    private bool _appeared;
    private bool _loadRequested;

    public long EventId
    {
        get => _eventId;
        set
        {
            _eventId = value;
            _loadRequested = true;
            if (_appeared) _ = LoadAsync();
        }
    }

    public string? SinceParam
    {
        get => _sinceParam;
        set
        {
            _sinceParam = value;
            _loadRequested = true;
            if (_appeared) _ = LoadAsync();
        }
    }

    public string? UntilParam
    {
        get => _untilParam;
        set
        {
            _untilParam = value;
            _loadRequested = true;
            if (_appeared) _ = LoadAsync();
        }
    }

    private class RegistrationRow
    {
        public string Text { get; set; } = string.Empty;
        public string? Secondary { get; set; }
        public List<string> Trainers { get; set; } = new();
        public bool HasTrainers => Trainers != null && Trainers.Count > 0;
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
        _appeared = true;
        if (_loadRequested)
            await LoadAsync();
    }

    private async Task LoadAsync()
    {
        if (EventId == 0) return;
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        try
        {
            // Debug: log incoming navigation params and event-level times
            try
            {
                var a = SinceParam ?? "(null)";
                var b = UntilParam ?? "(null)";
                Debug.WriteLine($"Debug - params: SinceParam: {a} | UntilParam: {b}");
            }
            catch { }

            var ev = await EventService.GetEventAsync(EventId);
            if (ev == null)
            {
                await DisplayAlert("Nenalezeno", "Událost nebyla nalezena.", "OK");
                return;
            }

            // Use explicit naming: prefer event name, otherwise fall back to "Lekce: {trainer}" or "Lekce"
            if (!string.IsNullOrWhiteSpace(ev.Name))
            {
                TitleLabel.Text = ev.Name;
            }
            else
            {
                var firstTrainer = ev.EventTrainersList?.FirstOrDefault()?.Name?.Trim();
                if (!string.IsNullOrWhiteSpace(firstTrainer))
                    TitleLabel.Text = $"Lekce: {firstTrainer}";
                else
                    TitleLabel.Text = "Lekce";
            }
            // Render HTML content with richer formatting
            DescLabel.FormattedText = HtmlHelpers.ToFormattedString(ev.Description);
            SummaryLabel.FormattedText = HtmlHelpers.ToFormattedString(ev.Summary);
            var locName = ev.LocationText;
            LocationLabel.Text = string.IsNullOrWhiteSpace(locName) ? null : $"Místo konání: {locName}";
            LocationLabel.IsVisible = !string.IsNullOrWhiteSpace(LocationLabel.Text);
            // Prefer since/until passed from instance navigation; otherwise use event-level values
            DateTime? since = ev.Since;
            DateTime? until = ev.Until;

            // Try to parse incoming params; they may be URL-encoded or in different ISO forms.
            var sinceRaw = SinceParam;
            var untilRaw = UntilParam;
            try { if (!string.IsNullOrWhiteSpace(sinceRaw)) sinceRaw = Uri.UnescapeDataString(sinceRaw); } catch { }
            try { if (!string.IsNullOrWhiteSpace(untilRaw)) untilRaw = Uri.UnescapeDataString(untilRaw); } catch { }

            if (!string.IsNullOrWhiteSpace(sinceRaw))
            {
                // Normalize common malformed form like 2025-12-28T0800:00.00000000 -> 2025-12-28T08:00:00.00000000
                try
                {
                    // Insert missing colon after hour when pattern like T0800:... appears
                    sinceRaw = Regex.Replace(sinceRaw, @"T(\d{2})(?=\d{2}:)", "T$1:");
                }
                catch { }

                if (DateTime.TryParseExact(sinceRaw, "o", CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out var sp))
                    since = sp;
                else if (DateTime.TryParse(sinceRaw, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out var sp2))
                    since = sp2;
            }

            if (!string.IsNullOrWhiteSpace(untilRaw))
            {
                try
                {
                    untilRaw = Regex.Replace(untilRaw, @"T(\d{2})(?=\d{2}:)", "T$1:");
                }
                catch { }

                if (DateTime.TryParseExact(untilRaw, "o", CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out var up))
                    until = up;
                else if (DateTime.TryParse(untilRaw, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out var up2))
                    until = up2;
            }

            // Debug: show raw params and final values used
            try
            {
                var rawSince = sinceRaw ?? "(null)";
                var rawUntil = untilRaw ?? "(null)";
                var s = since.HasValue ? since.Value.ToString("o") : "(null)";
                var u = until.HasValue ? until.Value.ToString("o") : "(null)";
                Debug.WriteLine($"Debug - event: rawSince: {rawSince}\nrawUntil: {rawUntil}\nusedSince: {s}\nusedUntil: {u}");
            }
            catch { }

            // If we still don't have instance-level since/until, try to fetch instances to derive them
            if (!since.HasValue && !until.HasValue)
            {
                try
                {
                    var startRange = DateTime.Now.Date.AddDays(-30);
                    var endRange = DateTime.Now.Date.AddDays(365);
                    var instances = await EventService.GetMyEventInstancesForRangeAsync(startRange, endRange, onlyMine: true);
                    var match = instances.FirstOrDefault(i => i.Event?.Id == EventId);
                    if (match != null)
                    {
                        since = match.Since;
                        until = match.Until;
                        try { Debug.WriteLine($"Debug - fallback: Found instance fallback: since={since?.ToString("o") ?? "(null)"}, until={until?.ToString("o") ?? "(null)"}"); } catch { }
                    }
                }
                catch { }
            }

            var sinceText = DateHelpers.ToFriendlyDateTimeString(since);
            var untilText = DateHelpers.ToFriendlyDateTimeString(until);
            string? range = null;
            if (!string.IsNullOrWhiteSpace(sinceText) && !string.IsNullOrWhiteSpace(untilText))
                range = $"{sinceText} – {untilText}";
            else if (!string.IsNullOrWhiteSpace(sinceText))
                range = sinceText;
            else if (!string.IsNullOrWhiteSpace(untilText))
                range = untilText;
            DateRangeLabel.Text = "Termín: " + range;
            DateRangeLabel.IsVisible = !string.IsNullOrWhiteSpace(range);
            // When using FormattedText the Label.Text remains empty; check original HTML fields instead
            DescFrame.IsVisible = !string.IsNullOrWhiteSpace(ev.Description);
            SummaryFrame.IsVisible = !string.IsNullOrWhiteSpace(ev.Summary);
            CreatedAtLabel.Text = $"Vytvořeno: {ev.CreatedAt:dd.MM.yyyy HH:mm}";
            if (ev.UpdatedAt.HasValue)
            {
                UpdatedAtLabel.Text = $"Aktualizováno: {ev.UpdatedAt:dd.MM.yyyy HH:mm}";
                UpdatedAtLabel.IsVisible = true;
            }
            else
            {
                UpdatedAtLabel.Text = null;
                UpdatedAtLabel.IsVisible = false;
            }
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
                if (n.Couple?.Active == false) parts.Add("Neaktivní");

                // Collect instance trainers separately to render them stacked in UI
                var trainerParts = new List<string>();
                var trainers = n.Couple?.Man?.EventInstanceTrainersList;
                if (trainers != null && trainers.Count > 0)
                {
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
                }

                // Lesson demands attached to this registration (new)
                var demands = n.EventLessonDemandsByRegistrationIdList;
                if (demands != null && demands.Count > 0)
                {
                    var demandParts = new List<string>();
                    foreach (var d in demands)
                    {
                        if (d == null) continue;
                        var cnt = d.LessonCount;
                        var tname = d.Trainer?.Name?.Trim();
                        if (!string.IsNullOrWhiteSpace(tname))
                            demandParts.Add($"{cnt}× {tname}");
                        else
                            demandParts.Add($"{cnt} lekcí");
                    }
                    if (demandParts.Count > 0)
                        parts.Add(string.Join(", ", demandParts));
                }

                var secondary = parts.Count > 0 ? string.Join(" • ", parts) : null;
                _registrations.Add(new RegistrationRow { Text = text, Secondary = secondary, Trainers = trainerParts });
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
