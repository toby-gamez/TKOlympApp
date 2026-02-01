using Microsoft.Maui.Controls;
using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using System.ComponentModel;
using TkOlympApp.Services;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "eventId")]
public partial class EditRegistrationsPage : ContentPage
{
    private readonly IAuthService _authService;
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly ObservableCollection<RegGroup> _groups = new();
    private RegItem? _selected;
    private RegGroup? _selectedGroup;
    private long _eventId;
    private EventDetails? _currentEvent;

    public long EventId
    {
        get => _eventId;
        set
        {
            _eventId = value;
            _ = LoadAsync();
        }
    }

    public EditRegistrationsPage(IAuthService authService, IEventService eventService, IUserService userService)
    {
        _authService = authService;
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"EditRegistrationsPage XAML init error: {ex}");
            try
            {
                Dispatcher.Dispatch(async () =>
                {
                    try { await DisplayAlertAsync(LocalizationService.Get("XAML_Error_Title") ?? "XAML Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
                });
            }
            catch { }
        }

        try
        {
            RegistrationsCollection.ItemsSource = _groups;
        }
        catch { }
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        
        // Subscribe to events
        if (PageRefresh != null)
            PageRefresh.Refreshing += OnRefresh;
        if (RegistrationsCollection != null)
        {
            RegistrationsCollection.SelectionChanged += OnSelectionChanged;
            RegistrationsCollection.RemainingItemsThresholdReached += OnRegistrationsRemainingThresholdReached;
        }
        if (ConfirmButton != null)
            ConfirmButton.Clicked += OnConfirmClicked;
    }

    protected override void OnDisappearing()
    {
        // Unsubscribe from events to prevent memory leaks
        if (PageRefresh != null)
            PageRefresh.Refreshing -= OnRefresh;
        if (RegistrationsCollection != null)
        {
            RegistrationsCollection.SelectionChanged -= OnSelectionChanged;
            RegistrationsCollection.RemainingItemsThresholdReached -= OnRegistrationsRemainingThresholdReached;
        }
        if (ConfirmButton != null)
            ConfirmButton.Clicked -= OnConfirmClicked;
        
        base.OnDisappearing();
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        await LoadAsync();
        PageRefresh.IsRefreshing = false;
    }

    private sealed class RegItem
    {
        public string Id { get; set; } = string.Empty;
        public string Text { get; set; } = string.Empty;
        public string Secondary { get; set; } = string.Empty;
    }

    private sealed class RegGroup : ObservableCollection<RegItem>
    {
        private readonly System.Collections.Generic.List<RegItem> _all = new();
        public string Key { get; }
        public int AllCount => _all.Count;

        public RegGroup(string key)
        {
            Key = key;
        }

        public void AddToGroup(RegItem item)
        {
            _all.Add(item);
            if (this.Count == 0)
            {
                base.Add(item);
            }
        }

        public void RefreshVisible()
        {
            this.Clear();
            var first = _all.FirstOrDefault();
            if (first != null) base.Add(first);
        }

        public void RevealMore(int maxToAdd)
        {
            if (maxToAdd <= 0) return;
            int added = 0;
            int start = this.Count;
            for (int i = start; i < _all.Count && added < maxToAdd; i++)
            {
                base.Add(_all[i]);
                added++;
            }
        }

        public System.Collections.Generic.IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

        public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
    }

    private sealed class TrainerOption : INotifyPropertyChanged
    {
        private int _count;
        public string Name { get; set; }
        public string? Id { get; set; }
        public int OriginalCount { get; set; }
        public int Count
        {
            get => _count;
            set
            {
                if (_count != value)
                {
                    _count = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Count)));
                }
            }
        }

        public TrainerOption(string name, int count, string? id)
        {
            Name = name;
            _count = count;
            OriginalCount = count;
            Id = id;
        }

        public event PropertyChangedEventHandler? PropertyChanged;
    }

    private async Task LoadAsync()
    {
        _groups.Clear();
        try
        {
            if (EventId == 0) return;

            // Load event details (trainers)
            try
            {
                _currentEvent = await _eventService.GetEventAsync(EventId);
            }
            catch { _currentEvent = null; }

            // Fetch event instances and registrations (to get registration ids)
            var startRange = DateTime.Now.Date.AddYears(-1).ToString("o");
            var endRange = DateTime.Now.Date.AddYears(1).ToString("o");
            var queryObj = new
            {
                // Request person id as well so we can match registrations by id instead of fragile name matching
                query = "query($startRange: Datetime!, $endRange: Datetime!) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange) { id event { id name eventRegistrationsList { id person { id firstName lastName } couple { id man { firstName lastName } woman { firstName lastName } } } } } }",
                variables = new { startRange = startRange, endRange = endRange }
            };

            var json = JsonSerializer.Serialize(queryObj);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
            {
                await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title") ?? "Error", body, LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            using var doc = JsonDocument.Parse(body);
            if (!doc.RootElement.TryGetProperty("data", out var data)) return;
            if (!data.TryGetProperty("eventInstancesForRangeList", out var instances) || instances.ValueKind != JsonValueKind.Array) return;

            // Determine current user's identifiers to filter registrations to only "my" registrations
            await _userService.InitializeAsync();
            var myPersonId = string.Empty;
            try { myPersonId = _userService.CurrentPersonId ?? string.Empty; } catch { myPersonId = string.Empty; }
            var myCouples = new System.Collections.Generic.List<CoupleInfo>();
            try { myCouples = await _userService.GetActiveCouplesFromUsersAsync(); } catch { }
            var myCoupleIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var c in myCouples)
            {
                if (!string.IsNullOrWhiteSpace(c.Id)) myCoupleIds.Add(c.Id!);
            }
            var me = await _userService.GetCurrentUserAsync();
            var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
            var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
            var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : string.IsNullOrWhiteSpace(myLast) ? myFirst : (myFirst + " " + myLast).Trim();

            var seenRegIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var inst in instances.EnumerateArray())
            {
                try
                {
                    if (!inst.TryGetProperty("event", out var ev) || ev.ValueKind == JsonValueKind.Null) continue;
                    // filter by EventId
                    try
                    {
                        if (ev.TryGetProperty("id", out var evIdEl))
                        {
                            long parsedEvId = 0;
                            if (evIdEl.ValueKind == JsonValueKind.Number && evIdEl.TryGetInt64(out var n)) parsedEvId = n;
                            else parsedEvId = long.TryParse(evIdEl.GetRawText().Trim('"'), out var t) ? t : 0;
                            if (parsedEvId != 0 && parsedEvId != EventId) continue;
                        }
                    }
                    catch { }

                    var evtName = ev.TryGetProperty("name", out var en) ? en.GetString() ?? string.Empty : string.Empty;
                    if (!ev.TryGetProperty("eventRegistrationsList", out var regs) || regs.ValueKind != JsonValueKind.Array) continue;
                    foreach (var reg in regs.EnumerateArray())
                    {
                        try
                        {
                            var regId = reg.TryGetProperty("id", out var idEl) ? idEl.GetRawText().Trim('"') : null;
                            if (string.IsNullOrWhiteSpace(regId)) continue;
                            if (seenRegIds.Contains(regId)) continue;

                            // Decide whether this registration belongs to the current user
                            bool isMine = false;
                            // Check couple id match
                            if (reg.TryGetProperty("couple", out var coupleEl) && coupleEl.ValueKind != JsonValueKind.Null)
                            {
                                if (coupleEl.TryGetProperty("id", out var cidEl))
                                {
                                    var cid = cidEl.GetRawText().Trim('"');
                                    if (!string.IsNullOrWhiteSpace(cid) && myCoupleIds.Contains(cid)) isMine = true;
                                }
                            }

                            // Prefer matching by person id (when available), fall back to name match only if id missing
                            if (!isMine && reg.TryGetProperty("person", out var personEl) && personEl.ValueKind != JsonValueKind.Null)
                            {
                                // Try id match first
                                if (!string.IsNullOrWhiteSpace(myPersonId) && personEl.TryGetProperty("id", out var pidEl))
                                {
                                    var pid = pidEl.GetRawText().Trim('"');
                                    if (!string.IsNullOrWhiteSpace(pid) && string.Equals(pid, myPersonId, StringComparison.OrdinalIgnoreCase)) isMine = true;
                                }

                                // If still not matched, fall back to name matching (legacy)
                                if (!isMine && !string.IsNullOrWhiteSpace(myFull))
                                {
                                    var pf = personEl.TryGetProperty("firstName", out var pff) ? pff.GetString() ?? string.Empty : string.Empty;
                                    var pl = personEl.TryGetProperty("lastName", out var pll) ? pll.GetString() ?? string.Empty : string.Empty;
                                    var pFull = string.IsNullOrWhiteSpace(pf) ? pl : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                                    if (!string.IsNullOrWhiteSpace(pFull) && string.Equals(pFull, myFull, StringComparison.OrdinalIgnoreCase)) isMine = true;
                                }
                            }

                            if (!isMine) continue;
                            seenRegIds.Add(regId);

                            string display = string.Empty;
                            if (reg.TryGetProperty("person", out var personEl2) && personEl2.ValueKind != JsonValueKind.Null)
                            {
                                var fn = personEl2.TryGetProperty("firstName", out var fnEl) ? fnEl.GetString() ?? string.Empty : string.Empty;
                                var ln = personEl2.TryGetProperty("lastName", out var lnEl) ? lnEl.GetString() ?? string.Empty : string.Empty;
                                display = string.IsNullOrWhiteSpace(fn) ? ln : (string.IsNullOrWhiteSpace(ln) ? fn : (fn + " " + ln).Trim());
                            }
                            else if (reg.TryGetProperty("couple", out var coupleEl2) && coupleEl2.ValueKind != JsonValueKind.Null)
                            {
                                var manName = string.Empty;
                                var womanName = string.Empty;
                                if (coupleEl2.TryGetProperty("man", out var manEl) && manEl.ValueKind != JsonValueKind.Null)
                                {
                                    var manFn = manEl.TryGetProperty("firstName", out var mfn) ? mfn.GetString() ?? string.Empty : string.Empty;
                                    var manLn = manEl.TryGetProperty("lastName", out var mln) ? mln.GetString() ?? string.Empty : string.Empty;
                                    manName = string.IsNullOrWhiteSpace(manFn) ? manLn : (manFn + " " + manLn).Trim();
                                }
                                if (coupleEl2.TryGetProperty("woman", out var womEl) && womEl.ValueKind != JsonValueKind.Null)
                                {
                                    var womanFn = womEl.TryGetProperty("firstName", out var wfn) ? wfn.GetString() ?? string.Empty : string.Empty;
                                    var womanLn = womEl.TryGetProperty("lastName", out var wln) ? wln.GetString() ?? string.Empty : string.Empty;
                                    womanName = string.IsNullOrWhiteSpace(womanFn) ? womanLn : (womanFn + " " + womanLn).Trim();
                                }
                                display = !string.IsNullOrWhiteSpace(manName) && !string.IsNullOrWhiteSpace(womanName) ? (manName + " - " + womanName) : (manName + womanName);
                            }

                            var item = new RegItem { Id = regId!, Text = string.IsNullOrWhiteSpace(display) ? regId! : display, Secondary = evtName };
                            var groupKey = string.IsNullOrWhiteSpace(item.Text) ? "" : item.Text;
                            var grp = _groups.FirstOrDefault(g => string.Equals(g.Key, groupKey, StringComparison.OrdinalIgnoreCase));
                            if (grp == null)
                            {
                                grp = new RegGroup(groupKey);
                                _groups.Add(grp);
                            }
                            grp.AddToGroup(item);
                        }
                        catch { }
                    }
                }
                catch { }
            }
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK");
        }
    }

    private void OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        _selected = e.CurrentSelection?.Count > 0 ? e.CurrentSelection[0] as RegItem : null;
        if (_selected != null)
        {
            _selectedGroup = _groups.FirstOrDefault(g => g.Any(i => i.Id == _selected.Id));
            ConfirmButton.IsEnabled = true;
            _ = LoadTrainerSelectionAsync();
        }
        else
        {
            _selectedGroup = null;
            ConfirmButton.IsEnabled = false;
            TrainerSelectionCollection.IsVisible = false;
            TrainerSelectionHeader.IsVisible = false;
        }
    }

    private void OnRegistrationsRemainingThresholdReached(object? sender, EventArgs e)
    {
        try
        {
            const int toAddTotal = 10;
            int added = 0;
            // First pass: add one per group to spread visible items
            foreach (var g in _groups)
            {
                if (added >= toAddTotal) break;
                if (g.AllCount > g.Count)
                {
                    g.RevealMore(1);
                    added++;
                }
            }

            // Second pass: fill remaining need from groups sequentially
            if (added < toAddTotal)
            {
                foreach (var g in _groups)
                {
                    if (added >= toAddTotal) break;
                    var remaining = g.AllCount - g.Count;
                    if (remaining > 0)
                    {
                        var need = Math.Min(toAddTotal - added, remaining);
                        g.RevealMore(need);
                        added += need;
                    }
                }
            }
        }
        catch { }
    }

    private async Task LoadTrainerSelectionAsync()
    {
        try
        {
            if (EventId == 0) return;
            var ev = _currentEvent ?? await _eventService.GetEventAsync(EventId);
            _currentEvent = ev;
            if (ev == null || ev.EventTrainersList == null || ev.EventTrainersList.Count == 0)
            {
                TrainerSelectionCollection.IsVisible = false;
                TrainerSelectionHeader.IsVisible = false;
                return;
            }
            var options = new System.Collections.Generic.List<TrainerOption>();
            foreach (var t in ev.EventTrainersList.OrderBy(x => EventTrainerDisplayHelper.GetTrainerDisplayName(x)))
            {
                var trainerName = EventTrainerDisplayHelper.GetTrainerDisplayName(t) ?? string.Empty;
                options.Add(new TrainerOption(trainerName, 0, t?.Id));
            }

            TrainerSelectionCollection.ItemsSource = options;
            TrainerSelectionHeader.IsVisible = true;
            TrainerSelectionCollection.IsVisible = true;

            // Prefill existing demands for selected registration
            try
            {
                if (_selected != null)
                {
                    var gql = new
                    {
                        query = "query($id: BigInt!) { event(id: $id) { eventRegistrations { nodes { id eventLessonDemandsByRegistrationIdList { lessonCount trainer { id name } } } } } }",
                        variables = new { id = EventId }
                    };

                    var json2 = JsonSerializer.Serialize(gql);
                    using var content2 = new StringContent(json2, Encoding.UTF8, "application/json");
                    using var resp2 = await _authService.Http.PostAsync("", content2);
                    var body2 = await resp2.Content.ReadAsStringAsync();
                    if (resp2.IsSuccessStatusCode)
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(body2);
                            if (doc.RootElement.TryGetProperty("data", out var data) && data.TryGetProperty("event", out var evEl) && evEl.ValueKind != JsonValueKind.Null)
                            {
                                if (evEl.TryGetProperty("eventRegistrations", out var regsEl) && regsEl.TryGetProperty("nodes", out var nodes) && nodes.ValueKind == JsonValueKind.Array)
                                {
                                    foreach (var node in nodes.EnumerateArray())
                                    {
                                        try
                                        {
                                            if (!node.TryGetProperty("id", out var nid)) continue;
                                            var nidStr = nid.GetRawText().Trim('"');
                                            if (!string.Equals(nidStr, _selected.Id, StringComparison.OrdinalIgnoreCase)) continue;

                                            if (node.TryGetProperty("eventLessonDemandsByRegistrationIdList", out var demands) && demands.ValueKind == JsonValueKind.Array)
                                            {
                                                foreach (var d in demands.EnumerateArray())
                                                {
                                                    try
                                                    {
                                                        var cnt = d.TryGetProperty("lessonCount", out var lc) && lc.ValueKind == JsonValueKind.Number && lc.TryGetInt32(out var v) ? v : 0;
                                                        string? demandTrainerId = null;
                                                        string? demandTrainerName = null;
                                                        if (d.TryGetProperty("trainer", out var tr) && tr.ValueKind != JsonValueKind.Null)
                                                        {
                                                            if (tr.TryGetProperty("id", out var tid)) demandTrainerId = tid.GetRawText().Trim('"');
                                                            if (tr.TryGetProperty("name", out var tname)) demandTrainerName = tname.GetString();
                                                        }
                                                        // find matching option by id first, then by name
                                                        TrainerOption? match = null;
                                                        if (!string.IsNullOrWhiteSpace(demandTrainerId)) match = options.FirstOrDefault(o => !string.IsNullOrWhiteSpace(o.Id) && string.Equals(o.Id, demandTrainerId, StringComparison.OrdinalIgnoreCase));
                                                        if (match == null && !string.IsNullOrWhiteSpace(demandTrainerName)) match = options.FirstOrDefault(o => string.Equals(o.Name, demandTrainerName, StringComparison.OrdinalIgnoreCase));
                                                        if (match != null)
                                                        {
                                                            match.Count = cnt;
                                                            match.OriginalCount = cnt;
                                                        }
                                                    }
                                                    catch { }
                                                }
                                            }
                                        }
                                        catch { }
                                    }
                                }
                            }
                        }
                        catch { }
                    }
                }
            }
            catch { }
        }
        catch { }

    }

    private void OnTrainerPlusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is TrainerOption to)
            {
                if (to.Count < 100) to.Count = to.Count + 1;
            }
        }
        catch { }
    }

    private void OnTrainerMinusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is TrainerOption to)
            {
                if (to.Count > 0) to.Count = to.Count - 1;
            }
        }
        catch { }
    }

    private async void OnConfirmClicked(object? sender, EventArgs e)
    {
        if (_selected == null) return;

        // collect trainer demands
        var trainers = new System.Collections.Generic.List<TrainerOption>();
        try
        {
            if (TrainerSelectionCollection.ItemsSource is System.Collections.Generic.IEnumerable<TrainerOption> tos)
            {
                foreach (var t in tos)
                {
                    // Include trainers that currently have >0 or that had >0 originally (so we can send lessonCount=0)
                    if (t != null && (t.Count > 0 || t.OriginalCount > 0))
                    {
                        trainers.Add(t);
                    }
                }
            }
        }
        catch { }

        if (trainers.Count == 0)
        {
            await DisplayAlertAsync(LocalizationService.Get("EditRegistrations_NoTrainerSelected") ?? "Chyba", LocalizationService.Get("EditRegistrations_NoTrainerSelected_Text") ?? "Vyberte alespoň jednoho trenéra a počet lekcí.", LocalizationService.Get("Button_OK") ?? "OK");
            return;
        }

        try
        {
            foreach (var t in trainers)
            {
                var clientMutationId = Guid.NewGuid().ToString();
                var gql = new
                {
                    query = "mutation SetLessonDemand($input: SetLessonDemandInput!) { setLessonDemand(input: $input) { eventLessonDemand { id } } }",
                    variables = new { input = new { registrationId = _selected.Id, trainerId = t.Id, lessonCount = t.Count, clientMutationId = clientMutationId } }
                };

                var json = JsonSerializer.Serialize(gql);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await _authService.Http.PostAsync("", content);
                var body = await resp.Content.ReadAsStringAsync();
                if (!resp.IsSuccessStatusCode)
                {
                    await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", body, LocalizationService.Get("Button_OK") ?? "OK");
                    return;
                }

                using var doc = JsonDocument.Parse(body);
                // check for GraphQL errors
                if (doc.RootElement.TryGetProperty("errors", out var errs) && errs.ValueKind == JsonValueKind.Array && errs.GetArrayLength() > 0)
                {
                    var first = errs[0];
                    var msg = first.TryGetProperty("message", out var m) ? m.GetString() : body;
                    await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", msg ?? body, LocalizationService.Get("Button_OK") ?? "OK");
                    return;
                }

                // optionally verify data path exists
            }

            try { SuccessText.Text = LocalizationService.Get("EditRegistrations_Success_Text") ?? "Změny uloženy"; } catch { }
            SuccessOverlay.IsVisible = true;
            await Task.Delay(900);
            try { await Shell.Current.GoToAsync("..", true); } catch { try { await Shell.Current.Navigation.PopAsync(); } catch { } }
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK");
        }
    }
}
