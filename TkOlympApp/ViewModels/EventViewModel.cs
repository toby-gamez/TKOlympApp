using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.Controls.Shapes;
using System.Collections.ObjectModel;
using System.Net;
using System.Text.RegularExpressions;
using TkOlympApp.Converters;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Pages;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EventViewModel : ViewModelBase
{
    private readonly ILogger<EventViewModel> _logger;
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private long _eventId;
    private string? _lastDescriptionHtml;
    private string? _lastSummaryHtml;

    [ObservableProperty]
    private string _titleText = string.Empty;

    [ObservableProperty]
    private FormattedString? _descriptionFormatted;

    [ObservableProperty]
    private FormattedString? _summaryFormatted;

    [ObservableProperty]
    private string _eventTypeText = string.Empty;

    [ObservableProperty]
    private bool _eventTypeVisible = false;

    [ObservableProperty]
    private string _locationText = string.Empty;

    [ObservableProperty]
    private bool _locationVisible = false;

    [ObservableProperty]
    private string _dateRangeText = string.Empty;

    [ObservableProperty]
    private bool _dateRangeVisible = false;

    [ObservableProperty]
    private string _createdAtText = string.Empty;

    [ObservableProperty]
    private string _updatedAtText = string.Empty;

    [ObservableProperty]
    private bool _updatedAtVisible = false;

    [ObservableProperty]
    private bool _registrationOpenVisible = false;

    [ObservableProperty]
    private bool _publicVisible = false;

    [ObservableProperty]
    private bool _visibleVisible = false;

    [ObservableProperty]
    private string _capacityText = string.Empty;

    [ObservableProperty]
    private string _registrationsText = string.Empty;

    [ObservableProperty]
    private bool _trainersFrameVisible = false;

    [ObservableProperty]
    private bool _cohortsFrameVisible = false;

    [ObservableProperty]
    private bool _descFrameVisible = false;

    [ObservableProperty]
    private bool _summaryFrameVisible = false;

    [ObservableProperty]
    private bool _registrationsFrameVisible = false;

    [ObservableProperty]
    private bool _registerButtonVisible = false;

    [ObservableProperty]
    private bool _registrationActionsRowVisible = false;

    [ObservableProperty]
    private bool _editRegistrationButtonVisible = true;

    [ObservableProperty]
    private int _deleteRegistrationButtonColumn = 1;

    [ObservableProperty]
    private int _deleteRegistrationButtonColumnSpan = 1;

    [ObservableProperty]
    private bool _isRefreshing = false;

    public long EventId
    {
        get => _eventId;
        set
        {
            _eventId = value;
            _ = LoadEventAsync();
        }
    }

    public ObservableCollection<RegistrationRow> Registrations { get; } = new();
    public ObservableCollection<string> Trainers { get; } = new();
    public ObservableCollection<View> CohortDots { get; } = new();

    public EventViewModel(
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService)
    {
        _logger = LoggerService.CreateLogger<EventViewModel>();
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        if (EventId != 0)
        {
            await LoadEventAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        try
        {
            IsRefreshing = true;
            await LoadEventAsync();
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private async Task LoadEventAsync()
    {
        if (EventId == 0) return;
        
        try
        {
            IsBusy = true;
            _logger.LogInformation("Loading event details for EventId: {EventId}", EventId);
            
            var ev = await _eventService.GetEventAsync(EventId);
            if (ev == null)
            {
                _logger.LogWarning("Event {EventId} not found", EventId);
                // Ideally communicate this error to the view
                TitleText = LocalizationService.Get("NotFound_Event") ?? "Event not found";
                return;
            }

            // Get since/until from eventInstancesList
            DateTime? since = null;
            DateTime? until = null;
            if (ev.EventInstancesList != null && ev.EventInstancesList.Count > 0)
            {
                var firstInstance = ev.EventInstancesList[0];
                since = firstInstance.Since;
                until = firstInstance.Until;
            }
            _logger.LogDebug("Event loaded - Since: {Since}, Until: {Until}", 
                since?.ToString("o") ?? "(null)", 
                until?.ToString("o") ?? "(null)");

            // Set title
            if (!string.IsNullOrWhiteSpace(ev.Name))
            {
                var title = ev.Name;
                var lessonPrefix = LocalizationService.Get("Lesson_Prefix") ?? "Lekce: ";
                if (title.StartsWith(lessonPrefix, StringComparison.OrdinalIgnoreCase))
                {
                    title = title.Substring(lessonPrefix.Length).Trim();
                }
                TitleText = title;
            }
            else
            {
                var firstTrainer = EventTrainerDisplayHelper.GetTrainerDisplayName(ev.EventTrainersList?.FirstOrDefault())?.Trim();
                if (!string.IsNullOrWhiteSpace(firstTrainer))
                    TitleText = firstTrainer;
                else
                    TitleText = LocalizationService.Get("Lesson_Short") ?? "Lekce";
            }

            // Render HTML content
            DescriptionFormatted = HtmlHelpers.ToFormattedString(ev.Description);
            SummaryFormatted = HtmlHelpers.ToFormattedString(ev.Summary);
            _lastDescriptionHtml = ev.Description;
            _lastSummaryHtml = ev.Summary;

            // Event type
            if (!string.IsNullOrWhiteSpace(ev.Type))
            {
                var converter = new EventTypeToLabelConverter();
                var typeLabel = converter.Convert(ev.Type, typeof(string), null!, System.Globalization.CultureInfo.CurrentCulture) as string;
                EventTypeText = (LocalizationService.Get("Event_Type_Prefix") ?? "Typ: ") + (typeLabel ?? ev.Type);
                EventTypeVisible = true;
            }
            else
            {
                EventTypeText = string.Empty;
                EventTypeVisible = false;
            }

            // Location
            var locName = ev.LocationText;
            LocationText = string.IsNullOrWhiteSpace(locName) 
                ? string.Empty 
                : (LocalizationService.Get("Event_Location_Prefix") ?? "Místo konání: ") + locName;
            LocationVisible = !string.IsNullOrWhiteSpace(locName);

            // Date range
            var sinceText = DateHelpers.ToFriendlyDateTimeString(since);
            var untilText = DateHelpers.ToFriendlyDateTimeString(until);
            string? range = null;
            if (!string.IsNullOrWhiteSpace(sinceText) && !string.IsNullOrWhiteSpace(untilText))
                range = $"{sinceText} – {untilText}";
            else if (!string.IsNullOrWhiteSpace(sinceText))
                range = sinceText;
            else if (!string.IsNullOrWhiteSpace(untilText))
                range = untilText;
            DateRangeText = (LocalizationService.Get("Event_DateRange_Prefix") ?? "Termín: ") + (range ?? string.Empty);
            DateRangeVisible = !string.IsNullOrWhiteSpace(range);

            // Visibility flags
            DescFrameVisible = !string.IsNullOrWhiteSpace(ev.Description);
            SummaryFrameVisible = !string.IsNullOrWhiteSpace(ev.Summary);
            
            CreatedAtText = (LocalizationService.Get("Event_Created_Prefix") ?? "Vytvořeno: ") + 
                           ev.CreatedAt.ToString("dd.MM.yyyy HH:mm");
            
            if (ev.UpdatedAt.HasValue)
            {
                UpdatedAtText = (LocalizationService.Get("Event_Updated_Prefix") ?? "Aktualizováno: ") + 
                               ev.UpdatedAt.Value.ToString("dd.MM.yyyy HH:mm");
                UpdatedAtVisible = true;
            }
            else
            {
                UpdatedAtText = string.Empty;
                UpdatedAtVisible = false;
            }

            var isRegistrationOpen = ev.IsRegistrationOpen ?? true;
            RegistrationOpenVisible = isRegistrationOpen;

            // Count occupied spots
            var occupiedSpots = 0;
            foreach (var n in ev.EventRegistrations?.Nodes ?? new List<EventRegistrationNode>())
            {
                if (n.Couple != null)
                    occupiedSpots += 2;
                else if (n.Person != null)
                    occupiedSpots += 1;
            }

            var registered = ev.EventRegistrations?.TotalCount ?? 0;
            var capacity = ev.Capacity;
            
            PublicVisible = ev.IsPublic;
            VisibleVisible = ev.IsVisible;
            
            if (capacity.HasValue)
            {
                var availableSpots = Math.Max(0, capacity.Value - occupiedSpots);
                var fmt = LocalizationService.Get("Event_Capacity_Format") ?? "Kapacita: {0} (Volno: {1})";
                CapacityText = string.Format(fmt, capacity.Value, availableSpots);
            }
            else
            {
                CapacityText = LocalizationService.Get("Event_Capacity_NA") ?? "Kapacita: N/A";
            }

            RegistrationsText = string.Format(
                LocalizationService.Get("Event_Registered_Format") ?? "Registrováno: {0}", 
                registered);

            var isLesson = string.Equals(ev.Type, "LESSON", StringComparison.OrdinalIgnoreCase);

            // Trainers
            Trainers.Clear();
            foreach (var t in ev.EventTrainersList ?? new List<EventTrainer>())
            {
                if (t == null) continue;
                var name = EventTrainerDisplayHelper.GetTrainerDisplayName(t)?.Trim();
                if (!string.IsNullOrWhiteSpace(name)) Trainers.Add(name);
            }
            TrainersFrameVisible = Trainers.Count > 0 && !isLesson;

            // Edit button visibility based on trainer count
            var trainerCount = ev.EventTrainersList?.Count ?? 0;
            if (trainerCount == 1)
            {
                EditRegistrationButtonVisible = false;
                DeleteRegistrationButtonColumn = 0;
                DeleteRegistrationButtonColumnSpan = 2;
            }
            else
            {
                EditRegistrationButtonVisible = true;
                DeleteRegistrationButtonColumn = 1;
                DeleteRegistrationButtonColumnSpan = 1;
            }

            // Fetch current user info
            var myFull = string.Empty;
            var myCouples = new List<CoupleInfo>();
            try
            {
                await _userService.InitializeAsync();
                var me = await _userService.GetCurrentUserAsync();
                var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
                var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
                myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : 
                        string.IsNullOrWhiteSpace(myLast) ? myFirst : 
                        (myFirst + " " + myLast).Trim();
                myCouples = await _userService.GetActiveCouplesFromUsersAsync();
            }
            catch { }

            // Build registrations list
            Registrations.Clear();
            var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            foreach (var n in ev.EventRegistrations?.Nodes ?? new List<EventRegistrationNode>())
            {
                var man = n.Couple?.Man?.Name?.Trim() ?? string.Empty;
                var woman = n.Couple?.Woman?.Name?.Trim() ?? string.Empty;
                var personName = n.Person?.Name?.Trim() ?? string.Empty;
                var personFirst = n.Person?.FirstName?.Trim() ?? string.Empty;
                var personLast = n.Person?.LastName?.Trim() ?? string.Empty;

                // For LESSON type, use fallback
                if (isLesson && string.IsNullOrWhiteSpace(personName) && 
                    (!string.IsNullOrWhiteSpace(personFirst) || !string.IsNullOrWhiteSpace(personLast)))
                {
                    personName = string.IsNullOrWhiteSpace(personFirst)
                        ? personLast
                        : string.IsNullOrWhiteSpace(personLast)
                            ? personFirst
                            : $"{personFirst} {personLast}".Trim();
                }

                if (string.IsNullOrWhiteSpace(man) && string.IsNullOrWhiteSpace(woman) &&
                    string.IsNullOrWhiteSpace(personName))
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
                    text = personName;
                }

                var parts = new List<string>();
                var trainerParts = new List<string>();

                // Lesson demands
                var demands = n.EventLessonDemandsByRegistrationIdList;
                if (demands != null && demands.Count > 0)
                {
                    var demandParts = new List<string>();
                    foreach (var d in demands)
                    {
                        if (d == null) continue;
                        var cnt = d.LessonCount;
                        var lessonsText = LocalizationService.Get("Lessons_Count") ?? "lekcí";
                        demandParts.Add($"{cnt} {lessonsText}");
                    }
                    if (demandParts.Count > 0)
                        parts.Add(string.Join(", ", demandParts));
                }

                var secondary = parts.Count > 0 ? string.Join(" • ", parts) : null;
                var key = (text ?? string.Empty) + "|" + (secondary ?? string.Empty);
                
                if (!seen.Contains(key))
                {
                    seen.Add(key);

                    // Check if this is current user or their couple
                    bool isCurrentUserOrCouple = false;

                    var pFirst = n.Person?.FirstName?.Trim() ?? string.Empty;
                    var pLast = n.Person?.LastName?.Trim() ?? string.Empty;
                    var pFull = string.IsNullOrWhiteSpace(pFirst) ? pLast : 
                               string.IsNullOrWhiteSpace(pLast) ? pFirst : 
                               (pFirst + " " + pLast).Trim();
                    
                    if (!string.IsNullOrWhiteSpace(myFull) && !string.IsNullOrWhiteSpace(pFull) && 
                        string.Equals(myFull, pFull, StringComparison.OrdinalIgnoreCase))
                    {
                        isCurrentUserOrCouple = true;
                    }

                    if (!isCurrentUserOrCouple && (!string.IsNullOrWhiteSpace(man) || !string.IsNullOrWhiteSpace(woman)))
                    {
                        foreach (var c in myCouples)
                        {
                            var myMan = c.ManName?.Trim() ?? string.Empty;
                            var myWoman = c.WomanName?.Trim() ?? string.Empty;
                            bool manMatch = !string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(myMan) && 
                                          string.Equals(man, myMan, StringComparison.OrdinalIgnoreCase);
                            bool womanMatch = !string.IsNullOrWhiteSpace(woman) && !string.IsNullOrWhiteSpace(myWoman) && 
                                            string.Equals(woman, myWoman, StringComparison.OrdinalIgnoreCase);
                            
                            if ((manMatch && womanMatch) || 
                                (manMatch && string.IsNullOrWhiteSpace(myWoman)) || 
                                (womanMatch && string.IsNullOrWhiteSpace(myMan)))
                            {
                                isCurrentUserOrCouple = true;
                                break;
                            }
                        }
                    }

                    Registrations.Add(new RegistrationRow
                    {
                        Text = text ?? string.Empty,
                        Secondary = secondary,
                        Trainers = trainerParts,
                        CoupleId = n.Couple?.Id,
                        PersonId = n.Person?.Id,
                        IsCurrentUserOrCouple = isCurrentUserOrCouple
                    });
                }
            }

            RegistrationsFrameVisible = Registrations.Count > 0;

            // Render cohort dots
            await BuildCohortDotsAsync(ev.EventTargetCohortsList ?? new List<EventTargetCohortLink>());

            // Determine button visibility
            bool userRegistered = false;
            foreach (var node in ev.EventRegistrations?.Nodes ?? new List<EventRegistrationNode>())
            {
                if (node == null) continue;
                
                var pFirst = node.Person?.FirstName?.Trim() ?? string.Empty;
                var pLast = node.Person?.LastName?.Trim() ?? string.Empty;
                var pFull = string.IsNullOrWhiteSpace(pFirst) ? pLast : 
                           string.IsNullOrWhiteSpace(pLast) ? pFirst : 
                           (pFirst + " " + pLast).Trim();
                
                if (!string.IsNullOrWhiteSpace(myFull) && !string.IsNullOrWhiteSpace(pFull) && 
                    string.Equals(myFull, pFull, StringComparison.OrdinalIgnoreCase))
                {
                    userRegistered = true;
                    break;
                }

                var man = node.Couple?.Man?.Name?.Trim() ?? string.Empty;
                var woman = node.Couple?.Woman?.Name?.Trim() ?? string.Empty;
                
                if (!string.IsNullOrWhiteSpace(man) || !string.IsNullOrWhiteSpace(woman))
                {
                    foreach (var c in myCouples)
                    {
                        var myMan = c.ManName?.Trim() ?? string.Empty;
                        var myWoman = c.WomanName?.Trim() ?? string.Empty;
                        bool manMatch = !string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(myMan) && 
                                      string.Equals(man, myMan, StringComparison.OrdinalIgnoreCase);
                        bool womanMatch = !string.IsNullOrWhiteSpace(woman) && !string.IsNullOrWhiteSpace(myWoman) && 
                                        string.Equals(woman, myWoman, StringComparison.OrdinalIgnoreCase);
                        
                        if ((manMatch && womanMatch) || 
                            (manMatch && string.IsNullOrWhiteSpace(myWoman)) || 
                            (womanMatch && string.IsNullOrWhiteSpace(myMan)))
                        {
                            userRegistered = true;
                            break;
                        }

                        var combined = (!string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman)) 
                            ? (man + " - " + woman) 
                            : (man + woman);
                        var myCombined = (!string.IsNullOrWhiteSpace(myMan) && !string.IsNullOrWhiteSpace(myWoman)) 
                            ? (myMan + " - " + myWoman) 
                            : (myMan + myWoman);
                        
                        if (!string.IsNullOrWhiteSpace(combined) && !string.IsNullOrWhiteSpace(myCombined) && 
                            string.Equals(combined, myCombined, StringComparison.OrdinalIgnoreCase))
                        {
                            userRegistered = true;
                            break;
                        }
                    }
                    if (userRegistered) break;
                }
            }

            // Check if event is in the past
            var isPast = false;
            if (until.HasValue)
            {
                var untilUtc = until.Value.ToUniversalTime();
                var nowUtc = DateTime.UtcNow;
                isPast = untilUtc < nowUtc;
            }
            else if (since.HasValue)
            {
                var sinceUtc = since.Value.ToUniversalTime();
                var nowUtc = DateTime.UtcNow;
                isPast = sinceUtc.AddDays(1) < nowUtc;
            }

            if (isPast)
            {
                RegisterButtonVisible = false;
                RegistrationActionsRowVisible = false;
            }
            else
            {
                if (userRegistered)
                {
                    RegisterButtonVisible = false;
                    RegistrationActionsRowVisible = isRegistrationOpen;
                }
                else
                {
                    var allowRegistration = isRegistrationOpen;
                    RegisterButtonVisible = allowRegistration;
                    RegistrationActionsRowVisible = false;
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load event - EventId: {EventId}", EventId);
            TitleText = $"Error: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private async Task BuildCohortDotsAsync(List<EventTargetCohortLink> cohorts)
    {
        await MainThread.InvokeOnMainThreadAsync(() =>
        {
            CohortDots.Clear();
            foreach (var link in cohorts)
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

                    var nameLabel = new Label 
                    { 
                        Text = name, 
                        VerticalOptions = LayoutOptions.Center, 
                        HorizontalOptions = LayoutOptions.Start 
                    };
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

                    CohortDots.Add(row);
                }
                catch { }
            }

            CohortsFrameVisible = CohortDots.Count > 0;
        });
    }

    [RelayCommand]
    private async Task RegisterAsync()
    {
        if (EventId == 0) return;
        await _navigationService.NavigateToAsync($"{nameof(RegistrationPage)}", new Dictionary<string, object>
        {
            ["id"] = EventId
        });
    }

    [RelayCommand]
    private async Task EditRegistrationAsync()
    {
        if (EventId == 0) return;
        await _navigationService.NavigateToAsync($"{nameof(EditRegistrationsPage)}", new Dictionary<string, object>
        {
            ["eventId"] = EventId
        });
    }

    [RelayCommand]
    private async Task DeleteRegistrationAsync()
    {
        if (EventId == 0) return;
        await _navigationService.NavigateToAsync($"{nameof(DeleteRegistrationsPage)}", new Dictionary<string, object>
        {
            ["eventId"] = EventId
        });
    }

    [RelayCommand]
    private async Task CopyDescriptionAsync()
    {
        var text = HtmlToPlainText(_lastDescriptionHtml) ?? string.Empty;
        if (string.IsNullOrWhiteSpace(text))
        {
            // Notify user - ideally through a message service
            return;
        }

        await PlainTextPage.ShowAsync(text);
    }

    [RelayCommand]
    private async Task CopySummaryAsync()
    {
        var text = HtmlToPlainText(_lastSummaryHtml) ?? string.Empty;
        if (string.IsNullOrWhiteSpace(text))
        {
            // Notify user
            return;
        }

        await PlainTextPage.ShowAsync(text);
    }

    [RelayCommand]
    private async Task RegistrationSelectedAsync(RegistrationRow? selected)
    {
        if (selected == null) return;

        var personId = selected.PersonId;
        if (!string.IsNullOrWhiteSpace(personId))
        {
            await _navigationService.NavigateToAsync($"{nameof(PersonPage)}", new Dictionary<string, object>
            {
                ["personId"] = personId
            });
            return;
        }

        var coupleId = selected.CoupleId;
        if (!string.IsNullOrWhiteSpace(coupleId))
        {
            await _navigationService.NavigateToAsync($"{nameof(CouplePage)}", new Dictionary<string, object>
            {
                ["id"] = coupleId
            });
        }
    }

    private static string? HtmlToPlainText(string? html)
    {
        if (string.IsNullOrWhiteSpace(html)) return html;
        
        var text = html;
        text = Regex.Replace(text, "<br\\s*/?>", "<br>", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "(?:<br>\\s*){2,}", "<br>", RegexOptions.IgnoreCase);
        text = text.Replace("<br>", "\n");
        text = Regex.Replace(text, "</p>", "\n\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<p[^>]*>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<li[^>]*>", "\n• ", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</li>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</h[1-6]>", "\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<h[1-6][^>]*>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<[^>]+>", string.Empty);
        text = WebUtility.HtmlDecode(text);
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
                var digits = Regex.Matches(s, "\\d+");
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

    // Nested class
    public class RegistrationRow
    {
        public string Text { get; set; } = string.Empty;
        public string? Secondary { get; set; }
        public List<string> Trainers { get; set; } = new();
        public bool HasTrainers => Trainers != null && Trainers.Count > 0;
        public bool HasSecondary => !string.IsNullOrWhiteSpace(Secondary);
        public string? CoupleId { get; set; }
        public string? PersonId { get; set; }
        public bool IsCurrentUserOrCouple { get; set; }
    }
}
