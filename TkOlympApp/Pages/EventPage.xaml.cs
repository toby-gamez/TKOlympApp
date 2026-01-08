using Microsoft.Maui.Controls;
using System.Collections.ObjectModel;
using System.Net;
using System.Text.RegularExpressions;
using System.Globalization;
using System.Diagnostics;
using TkOlympApp.Services;
using TkOlympApp.Helpers;
using TkOlympApp.Converters;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Controls.Shapes;

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
    private string? _lastDescriptionHtml;
    private string? _lastSummaryHtml;

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
        public string? CoupleId { get; set; }
        public string? PersonId { get; set; }
    }

    private readonly ObservableCollection<RegistrationRow> _registrations = new();
    private readonly ObservableCollection<string> _trainers = new();

    public EventPage()
    {
        InitializeComponent();
        RegistrationsCollection.ItemsSource = _registrations;
        TrainersCollection.ItemsSource = _trainers;
    }

    private async void OnRegistrationSelected(object? sender, SelectionChangedEventArgs e)
    {
        try
        {
            if (e.CurrentSelection == null || e.CurrentSelection.Count == 0) return;
            var selected = e.CurrentSelection.FirstOrDefault() as RegistrationRow;
            if (selected == null) return;
            // Clear selection for UX
            try { RegistrationsCollection.SelectedItem = null; } catch { }

            var personId = selected.PersonId;
            if (!string.IsNullOrWhiteSpace(personId))
            {
                // Navigate to PersonPage with personId
                await Shell.Current.GoToAsync($"{nameof(PersonPage)}?personId={Uri.EscapeDataString(personId)}");
                return;
            }

            var coupleId = selected.CoupleId;
            if (!string.IsNullOrWhiteSpace(coupleId))
            {
                // Navigate to CouplePage with id
                await Shell.Current.GoToAsync($"{nameof(CouplePage)}?id={Uri.EscapeDataString(coupleId)}");
            }
        }
        catch
        {
            // ignore navigation errors
        }
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
                await DisplayAlertAsync(LocalizationService.Get("NotFound_Title"), LocalizationService.Get("NotFound_Event"), LocalizationService.Get("Button_OK"));
                return;
            }

            // Use explicit naming: prefer event name, otherwise fall back to localized "Lesson" prefix or short form
            if (!string.IsNullOrWhiteSpace(ev.Name))
            {
                TitleLabel.Text = ev.Name;
            }
            else
            {
                var firstTrainer = ev.EventTrainersList?.FirstOrDefault()?.Name?.Trim();
                if (!string.IsNullOrWhiteSpace(firstTrainer))
                    TitleLabel.Text = (LocalizationService.Get("Lesson_Prefix") ?? "Lekce: ") + firstTrainer;
                else
                    TitleLabel.Text = LocalizationService.Get("Lesson_Short") ?? "Lekce";
            }
            // Render HTML content with richer formatting
            DescLabel.FormattedText = HtmlHelpers.ToFormattedString(ev.Description);
            SummaryLabel.FormattedText = HtmlHelpers.ToFormattedString(ev.Summary);
            // keep original HTML for copy/plain-text view
            _lastDescriptionHtml = ev.Description;
            _lastSummaryHtml = ev.Summary;
            var locName = ev.LocationText;
            LocationLabel.Text = string.IsNullOrWhiteSpace(locName) ? string.Empty : (LocalizationService.Get("Event_Location_Prefix") ?? "Místo konání: ") + locName;
            LocationLabel.IsVisible = !string.IsNullOrWhiteSpace(locName);
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
                    var instances = await EventService.GetMyEventInstancesForRangeAsync(startRange, endRange);
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
            DateRangeLabel.Text = (LocalizationService.Get("Event_DateRange_Prefix") ?? "Termín: ") + (range ?? string.Empty);
            DateRangeLabel.IsVisible = !string.IsNullOrWhiteSpace(range);
            // When using FormattedText the Label.Text remains empty; check original HTML fields instead
            DescFrame.IsVisible = !string.IsNullOrWhiteSpace(ev.Description);
            SummaryFrame.IsVisible = !string.IsNullOrWhiteSpace(ev.Summary);
            CreatedAtLabel.Text = (LocalizationService.Get("Event_Created_Prefix") ?? "Vytvořeno: ") + ev.CreatedAt.ToString("dd.MM.yyyy HH:mm");
            if (ev.UpdatedAt.HasValue)
            {
                UpdatedAtLabel.Text = (LocalizationService.Get("Event_Updated_Prefix") ?? "Aktualizováno: ") + ev.UpdatedAt.Value.ToString("dd.MM.yyyy HH:mm");
                UpdatedAtLabel.IsVisible = true;
            }
            else
            {
                UpdatedAtLabel.Text = string.Empty;
                UpdatedAtLabel.IsVisible = false;
            }
            RegistrationOpenLabel.IsVisible = ev.IsRegistrationOpen;
            var registered = ev.EventRegistrations?.TotalCount ?? 0;
            var capacity = ev.Capacity;
            PublicLabel.IsVisible = ev.IsPublic;
            VisibleLabel.IsVisible = ev.IsVisible;
            if (capacity.HasValue)
            {
                var available = Math.Max(0, capacity.Value - registered);
                var fmt = LocalizationService.Get("Event_Capacity_Format") ?? "Kapacita: {0} (Volno: {1})";
                CapacityLabel.Text = string.Format(fmt, capacity.Value, available);
            }
            else
            {
                CapacityLabel.Text = LocalizationService.Get("Event_Capacity_NA") ?? "Kapacita: N/A";
            }

            RegistrationsLabel.Text = string.Format(LocalizationService.Get("Event_Registered_Format") ?? "Registrováno: {0}", registered);

            // Trainers (event level)
            _trainers.Clear();
            foreach (var t in ev.EventTrainersList ?? new List<TkOlympApp.Services.EventService.EventTrainer>())
            {
                if (t == null) continue;
                var name = t.Name?.Trim();
                var price = t.LessonPrice;
                string line = name ?? string.Empty;
                if (price != null && price.Amount != 0)
                {
                    var cur = price.Currency;
                    var priceText = $"{price.Amount:0.##} {cur}";
                    line = string.IsNullOrWhiteSpace(name) ? priceText : $"{name} {priceText}";
                }
                if (!string.IsNullOrWhiteSpace(line)) _trainers.Add(line);
            }
            TrainersFrame.IsVisible = _trainers.Count > 0;

            // Hide edit-registration button when trainer reservations are not allowed
            // (depend only on trainer count: single trainer => hide edit)
            try
            {
                var trainerCount = ev.EventTrainersList?.Count ?? 0;
                if (trainerCount == 1)
                {
                    EditRegistrationButton.IsVisible = false;
                    Grid.SetColumn(DeleteRegistrationButton, 0);
                    Grid.SetColumnSpan(DeleteRegistrationButton, 2);
                }
                else
                {
                    EditRegistrationButton.IsVisible = true;
                    Grid.SetColumn(DeleteRegistrationButton, 1);
                    Grid.SetColumnSpan(DeleteRegistrationButton, 1);
                }
            }
            catch { }
            _registrations.Clear();
            var seen = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var n in ev.EventRegistrations?.Nodes ?? new List<EventService.EventRegistrationNode>())
            {
                var man = n.Couple?.Man?.Name?.Trim() ?? string.Empty;
                var woman = n.Couple?.Woman?.Name?.Trim() ?? string.Empty;
                var personFirst = n.Person?.FirstName?.Trim() ?? string.Empty;
                var personLast = n.Person?.LastName?.Trim() ?? string.Empty;

                // If both couple and person are empty, skip
                if (string.IsNullOrWhiteSpace(man) && string.IsNullOrWhiteSpace(woman)
                    && string.IsNullOrWhiteSpace(personFirst) && string.IsNullOrWhiteSpace(personLast))
                    continue;

                string text;
                if (!string.IsNullOrWhiteSpace(man) || !string.IsNullOrWhiteSpace(woman))
                {
                    text = !string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman)
                        ? $"{man} - {woman}"
                        : (string.IsNullOrWhiteSpace(man) ? woman : man);
                }
                else
                {
                    // Single person entry
                    text = string.IsNullOrWhiteSpace(personFirst)
                        ? personLast
                        : string.IsNullOrWhiteSpace(personLast)
                            ? personFirst
                            : $"{personFirst} {personLast}".Trim();
                }

                var parts = new List<string>();
                if (n.Couple?.Active == false) parts.Add(LocalizationService.Get("Couple_Inactive") ?? "Neaktivní");

                // Collect instance trainers for couple (person entries may not have these)
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

                // Lesson demands attached to this registration
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
                var key = (text ?? string.Empty) + "|" + (secondary ?? string.Empty);
                if (!seen.Contains(key))
                {
                    seen.Add(key);
                    _registrations.Add(new RegistrationRow
                    {
                        Text = text ?? string.Empty,
                        Secondary = secondary,
                        Trainers = trainerParts,
                        CoupleId = n.Couple?.Id,
                        PersonId = n.Person?.Id
                    });
                }
            }

            RegistrationsFrame.IsVisible = _registrations.Count > 0;

            // Render cohort color dots (if any) by building children manually (reliable rendering)
            try
            {
                CohortDots.Children.Clear();
                var list = ev.EventTargetCohortsList ?? new System.Collections.Generic.List<EventService.EventTargetCohortLink>();
                foreach (var link in list)
                {
                    try
                    {
                        var c = link?.Cohort;
                        if (c == null) continue;
                        var name = c.Name ?? string.Empty;
                        var colorBrush = TryParseColorBrush(c.ColorRgb) ?? new SolidColorBrush(Colors.LightGray);

                        var row = new Grid { VerticalOptions = LayoutOptions.Center, HorizontalOptions = LayoutOptions.Fill };
                        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });
                        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                        var nameLabel = new Label { Text = name, VerticalOptions = LayoutOptions.Center, HorizontalOptions = LayoutOptions.Start };
                        row.Add(nameLabel);

                        var dot = new Border
                        {
                            WidthRequest = 20,
                            HeightRequest = 20,
                            Padding = 0,
                            Margin = new Thickness(0),
                            HorizontalOptions = LayoutOptions.End,
                            VerticalOptions = LayoutOptions.Center,
                            Background = colorBrush,
                            Stroke = null,
                            StrokeShape = new RoundRectangle { CornerRadius = 10 }
                        };
                        row.Add(dot, 1, 0);

                        CohortDots.Children.Add(row);
                    }
                    catch { }
                }

                CohortDots.IsVisible = CohortDots.Children.Count > 0;
                try { CohortsFrame.IsVisible = CohortDots.IsVisible; } catch { CohortsFrame.IsVisible = false; }
            }
            catch { CohortDots.IsVisible = false; try { CohortsFrame.IsVisible = false; } catch { } }

            // Determine whether current user (or one of their active couples) is registered for this event
            try
            {
                await UserService.InitializeAsync();
                var me = await UserService.GetCurrentUserAsync();
                var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
                var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
                var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : string.IsNullOrWhiteSpace(myLast) ? myFirst : (myFirst + " " + myLast).Trim();

                var myCouples = new List<UserService.CoupleInfo>();
                try { myCouples = await UserService.GetActiveCouplesFromUsersAsync(); } catch { }

                bool userRegistered = false;

                foreach (var node in ev.EventRegistrations?.Nodes ?? new List<EventService.EventRegistrationNode>())
                {
                    if (node == null) continue;
                    // Check person-based registration (match by first/last name)
                    var pFirst = node.Person?.FirstName?.Trim() ?? string.Empty;
                    var pLast = node.Person?.LastName?.Trim() ?? string.Empty;
                    var pFull = string.IsNullOrWhiteSpace(pFirst) ? pLast : string.IsNullOrWhiteSpace(pLast) ? pFirst : (pFirst + " " + pLast).Trim();
                    if (!string.IsNullOrWhiteSpace(myFull) && !string.IsNullOrWhiteSpace(pFull) && string.Equals(myFull, pFull, StringComparison.OrdinalIgnoreCase))
                    {
                        userRegistered = true;
                        break;
                    }

                    // Check couple-based registration (match by man/woman names against user's active couples)
                    var man = node.Couple?.Man?.Name?.Trim() ?? string.Empty;
                    var woman = node.Couple?.Woman?.Name?.Trim() ?? string.Empty;
                    if (!string.IsNullOrWhiteSpace(man) || !string.IsNullOrWhiteSpace(woman))
                    {
                        foreach (var c in myCouples)
                        {
                            try
                            {
                                var myMan = c.ManName?.Trim() ?? string.Empty;
                                var myWoman = c.WomanName?.Trim() ?? string.Empty;
                                // Compare trimmed case-insensitive both members if available, otherwise compare single-name entries
                                bool manMatch = !string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(myMan) && string.Equals(man, myMan, StringComparison.OrdinalIgnoreCase);
                                bool womanMatch = !string.IsNullOrWhiteSpace(woman) && !string.IsNullOrWhiteSpace(myWoman) && string.Equals(woman, myWoman, StringComparison.OrdinalIgnoreCase);
                                if ((manMatch && womanMatch) || (manMatch && string.IsNullOrWhiteSpace(myWoman)) || (womanMatch && string.IsNullOrWhiteSpace(myMan)))
                                {
                                    userRegistered = true;
                                    break;
                                }
                                // Fallback: compare combined "man - woman" style
                                var combined = (!string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman)) ? (man + " - " + woman) : (man + woman);
                                var myCombined = (!string.IsNullOrWhiteSpace(myMan) && !string.IsNullOrWhiteSpace(myWoman)) ? (myMan + " - " + myWoman) : (myMan + myWoman);
                                if (!string.IsNullOrWhiteSpace(combined) && !string.IsNullOrWhiteSpace(myCombined) && string.Equals(combined, myCombined, StringComparison.OrdinalIgnoreCase))
                                {
                                    userRegistered = true;
                                    break;
                                }
                            }
                            catch { }
                        }
                        if (userRegistered) break;
                    }
                }

                RegistrationActionsRow.IsVisible = userRegistered;

                // If the event already finished (end date older than today), hide all registration UI
                try
                {
                    // Compare only the date portion to avoid time-of-day/offset issues.
                    // Use UTC dates for a stable comparison regardless of Kind/local conversion.
                    var isPast = false;
                    if (until.HasValue)
                    {
                        var untilDateUtc = until.Value.ToUniversalTime().Date;
                        var todayUtc = DateTime.UtcNow.Date;
                        isPast = untilDateUtc < todayUtc;
                    }
                    else if (since.HasValue)
                    {
                        var sinceDateUtc = since.Value.ToUniversalTime().Date;
                        var todayUtc = DateTime.UtcNow.Date;
                        isPast = sinceDateUtc < todayUtc;
                    }

                    if (isPast)
                    {
                        RegisterButton.IsVisible = false;
                        RegistrationActionsRow.IsVisible = false;
                        EditRegistrationButton.IsVisible = false;
                        DeleteRegistrationButton.IsVisible = false;
                    }
                    else
                    {
                        // Ensure register button remains visible when event is not past
                        RegisterButton.IsVisible = true;
                    }
                }
                catch { }
            }
            catch { }
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
        }
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        try
        {
            await LoadAsync();
        }
        finally
        {
            try { RefreshViewControl.IsRefreshing = false; } catch { }
        }
    }

    private async void OnRegisterButtonClicked(object? sender, EventArgs e)
    {
        try
        {
            if (EventId == 0) return;
            await Shell.Current.GoToAsync($"{nameof(RegistrationPage)}?id={EventId}");
        }
        catch { }
    }

    private async void OnCopyDescriptionClicked(object? sender, EventArgs e)
    {
        try
        {
            var text = HtmlToPlainText(_lastDescriptionHtml) ?? string.Empty;
            if (string.IsNullOrWhiteSpace(text))
            {
                try { await DisplayAlertAsync(LocalizationService.Get("PlainText_Empty_Title") ?? "Prázdný text", LocalizationService.Get("PlainText_Empty_Body") ?? "Žádný text ke zobrazení", LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
                return;
            }

            try
            {
                // Use PlainTextPage.ShowAsync to avoid any URI/XAML aggregation issues
                await PlainTextPage.ShowAsync(text);
            }
            catch (Exception ex)
            {
                try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
            }
        }
        catch (Exception ex)
        {
            try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
    }

    private async void OnCopySummaryClicked(object? sender, EventArgs e)
    {
        try
        {
            var text = HtmlToPlainText(_lastSummaryHtml) ?? string.Empty;
            if (string.IsNullOrWhiteSpace(text))
            {
                try { await DisplayAlertAsync(LocalizationService.Get("PlainText_Empty_Title") ?? "Prázdný text", LocalizationService.Get("PlainText_Empty_Body") ?? "Žádný text ke zobrazení", LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
                return;
            }

            await PlainTextPage.ShowAsync(text);
        }
        catch (Exception ex)
        {
            try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
    }

    private async void OnDeleteRegistrationsClicked(object? sender, EventArgs e)
    {
            try
            {
                await Shell.Current.GoToAsync($"{nameof(DeleteRegistrationsPage)}?eventId={EventId}");
            }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Navigation to DeleteRegistrationsPage failed: {ex}");
            try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
    }

    private async void OnEditRegistrationClicked(object? sender, EventArgs e)
    {
        try
        {
            if (EventId == 0) return;
            await Shell.Current.GoToAsync($"{nameof(EditRegistrationsPage)}?eventId={EventId}");
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Navigation to EditRegistrationsPage failed: {ex}");
            try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
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

    private Brush? TryParseColorBrush(string? colorRgb)
    {
        if (string.IsNullOrWhiteSpace(colorRgb)) return null;
        var s = colorRgb.Trim();
        try
        {
            if (s.StartsWith("#"))
                return new SolidColorBrush(Color.FromArgb(s));

            if (s.Length == 6)
                return new SolidColorBrush(Color.FromArgb("#" + s));

            if (s.StartsWith("rgb", StringComparison.OrdinalIgnoreCase))
            {
                var digits = System.Text.RegularExpressions.Regex.Matches(s, "\\d+");
                if (digits.Count >= 3)
                {
                    var r = int.Parse(digits[0].Value);
                    var g = int.Parse(digits[1].Value);
                    var b = int.Parse(digits[2].Value);
                    return new SolidColorBrush(Color.FromRgb(r, g, b));
                }
            }
        }
        catch { }
        return null;
    }
}
