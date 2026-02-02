using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Text;
using System.Text.Json;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EditRegistrationsViewModel : ViewModelBase
{
    private readonly IAuthService _authService;
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly INavigationService _navigationService;
    private EventDetails? _currentEvent;
    private RegItem? _selected;
    private RegGroup? _selectedGroup;

    [ObservableProperty]
    private long _eventId;

    [ObservableProperty]
    private bool _confirmEnabled = false;

    [ObservableProperty]
    private bool _trainerSelectionVisible = false;

    [ObservableProperty]
    private bool _trainerSelectionHeaderVisible = false;

    [ObservableProperty]
    private bool _successOverlayVisible = false;

    [ObservableProperty]
    private string _successText = string.Empty;

    [ObservableProperty]
    private bool _isRefreshing = false;

    public ObservableCollection<RegGroup> Groups { get; } = new();
    public ObservableCollection<TrainerOption> TrainerOptions { get; } = new();

    public EditRegistrationsViewModel(
        IAuthService authService,
        IEventService eventService,
        IUserService userService,
        INavigationService navigationService)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    partial void OnEventIdChanged(long value)
    {
        if (value != 0)
        {
            _ = LoadAsync();
        }
    }

    public override async Task InitializeAsync()
    {
        await base.InitializeAsync();
        if (EventId != 0)
        {
            await LoadAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        IsRefreshing = true;
        await LoadAsync();
        IsRefreshing = false;
    }

    public void OnSelectionChanged(RegItem? selected)
    {
        _selected = selected;
        if (_selected != null)
        {
            _selectedGroup = Groups.FirstOrDefault(g => g.Any(i => i.Id == _selected.Id));
            ConfirmEnabled = true;
            _ = LoadTrainerSelectionAsync();
        }
        else
        {
            _selectedGroup = null;
            ConfirmEnabled = false;
            TrainerSelectionVisible = false;
            TrainerSelectionHeaderVisible = false;
        }
    }

    public void OnRemainingItemsThresholdReached()
    {
        try
        {
            const int toAddTotal = 10;
            int added = 0;
            
            // First pass: add one per group
            foreach (var g in Groups)
            {
                if (added >= toAddTotal) break;
                if (g.AllCount > g.Count)
                {
                    g.RevealMore(1);
                    added++;
                }
            }

            // Second pass: fill remaining
            if (added < toAddTotal)
            {
                foreach (var g in Groups)
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

    private async Task LoadAsync()
    {
        Groups.Clear();
        
        try
        {
            IsBusy = true;
            if (EventId == 0) return;

            // Load event details
            try
            {
                _currentEvent = await _eventService.GetEventAsync(EventId);
            }
            catch { _currentEvent = null; }

            // Fetch registrations
            var startRange = DateTime.Now.Date.AddYears(-1).ToString("o");
            var endRange = DateTime.Now.Date.AddYears(1).ToString("o");
            var queryObj = new
            {
                query = "query($startRange: Datetime!, $endRange: Datetime!) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange) { id event { id name eventRegistrationsList { id person { id firstName lastName } couple { id man { firstName lastName } woman { firstName lastName } } } } } }",
                variables = new { startRange = startRange, endRange = endRange }
            };

            var json = JsonSerializer.Serialize(queryObj);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);
            var body = await resp.Content.ReadAsStringAsync();
            
            if (!resp.IsSuccessStatusCode)
            {
                // Error handling - ideally communicate to view
                return;
            }

            using var doc = JsonDocument.Parse(body);
            if (!doc.RootElement.TryGetProperty("data", out var data)) return;
            if (!data.TryGetProperty("eventInstancesForRangeList", out var instances) || 
                instances.ValueKind != JsonValueKind.Array) return;

            // Determine current user's identifiers
            await _userService.InitializeAsync();
            var myPersonId = string.Empty;
            try { myPersonId = _userService.CurrentPersonId ?? string.Empty; } catch { myPersonId = string.Empty; }
            
            var myCouples = new List<CoupleInfo>();
            try { myCouples = await _userService.GetActiveCouplesFromUsersAsync(); } catch { }
            
            var myCoupleIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var c in myCouples)
            {
                if (!string.IsNullOrWhiteSpace(c.Id)) myCoupleIds.Add(c.Id!);
            }
            
            var me = await _userService.GetCurrentUserAsync();
            var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
            var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
            var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : 
                        string.IsNullOrWhiteSpace(myLast) ? myFirst : 
                        (myFirst + " " + myLast).Trim();

            var seenRegIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            
            foreach (var inst in instances.EnumerateArray())
            {
                try
                {
                    if (!inst.TryGetProperty("event", out var ev) || ev.ValueKind == JsonValueKind.Null) continue;
                    
                    // Filter by EventId
                    try
                    {
                        if (ev.TryGetProperty("id", out var evIdEl))
                        {
                            long parsedEvId = 0;
                            if (evIdEl.ValueKind == JsonValueKind.Number && evIdEl.TryGetInt64(out var n)) 
                                parsedEvId = n;
                            else 
                                parsedEvId = long.TryParse(evIdEl.GetRawText().Trim('"'), out var t) ? t : 0;
                            if (parsedEvId != 0 && parsedEvId != EventId) continue;
                        }
                    }
                    catch { }

                    var evtName = ev.TryGetProperty("name", out var en) ? en.GetString() ?? string.Empty : string.Empty;
                    if (!ev.TryGetProperty("eventRegistrationsList", out var regs) || 
                        regs.ValueKind != JsonValueKind.Array) continue;
                    
                    foreach (var reg in regs.EnumerateArray())
                    {
                        try
                        {
                            var regId = reg.TryGetProperty("id", out var idEl) ? idEl.GetRawText().Trim('"') : null;
                            if (string.IsNullOrWhiteSpace(regId)) continue;
                            if (seenRegIds.Contains(regId)) continue;

                            // Check if this registration belongs to current user
                            bool isMine = false;
                            
                            // Check couple id match
                            if (reg.TryGetProperty("couple", out var coupleEl) && coupleEl.ValueKind != JsonValueKind.Null)
                            {
                                if (coupleEl.TryGetProperty("id", out var cidEl))
                                {
                                    var cid = cidEl.GetRawText().Trim('"');
                                    if (!string.IsNullOrWhiteSpace(cid) && myCoupleIds.Contains(cid)) 
                                        isMine = true;
                                }
                            }

                            // Check person id match
                            if (!isMine && reg.TryGetProperty("person", out var personEl) && 
                                personEl.ValueKind != JsonValueKind.Null)
                            {
                                // Try id match first
                                if (!string.IsNullOrWhiteSpace(myPersonId) && personEl.TryGetProperty("id", out var pidEl))
                                {
                                    var pid = pidEl.GetRawText().Trim('"');
                                    if (!string.IsNullOrWhiteSpace(pid) && 
                                        string.Equals(pid, myPersonId, StringComparison.OrdinalIgnoreCase)) 
                                        isMine = true;
                                }

                                // Fallback to name matching
                                if (!isMine && !string.IsNullOrWhiteSpace(myFull))
                                {
                                    var pf = personEl.TryGetProperty("firstName", out var pff) ? 
                                            pff.GetString() ?? string.Empty : string.Empty;
                                    var pl = personEl.TryGetProperty("lastName", out var pll) ? 
                                            pll.GetString() ?? string.Empty : string.Empty;
                                    var pFull = string.IsNullOrWhiteSpace(pf) ? pl : 
                                               (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                                    if (!string.IsNullOrWhiteSpace(pFull) && 
                                        string.Equals(pFull, myFull, StringComparison.OrdinalIgnoreCase)) 
                                        isMine = true;
                                }
                            }

                            if (!isMine) continue;
                            seenRegIds.Add(regId);

                            string display = string.Empty;
                            if (reg.TryGetProperty("person", out var personEl2) && personEl2.ValueKind != JsonValueKind.Null)
                            {
                                var fn = personEl2.TryGetProperty("firstName", out var fnEl) ? 
                                        fnEl.GetString() ?? string.Empty : string.Empty;
                                var ln = personEl2.TryGetProperty("lastName", out var lnEl) ? 
                                        lnEl.GetString() ?? string.Empty : string.Empty;
                                display = string.IsNullOrWhiteSpace(fn) ? ln : 
                                         (string.IsNullOrWhiteSpace(ln) ? fn : (fn + " " + ln).Trim());
                            }
                            else if (reg.TryGetProperty("couple", out var coupleEl2) && coupleEl2.ValueKind != JsonValueKind.Null)
                            {
                                var manName = string.Empty;
                                var womanName = string.Empty;
                                
                                if (coupleEl2.TryGetProperty("man", out var manEl) && manEl.ValueKind != JsonValueKind.Null)
                                {
                                    var manFn = manEl.TryGetProperty("firstName", out var mfn) ? 
                                               mfn.GetString() ?? string.Empty : string.Empty;
                                    var manLn = manEl.TryGetProperty("lastName", out var mln) ? 
                                               mln.GetString() ?? string.Empty : string.Empty;
                                    manName = string.IsNullOrWhiteSpace(manFn) ? manLn : (manFn + " " + manLn).Trim();
                                }
                                
                                if (coupleEl2.TryGetProperty("woman", out var womEl) && womEl.ValueKind != JsonValueKind.Null)
                                {
                                    var womanFn = womEl.TryGetProperty("firstName", out var wfn) ? 
                                                 wfn.GetString() ?? string.Empty : string.Empty;
                                    var womanLn = womEl.TryGetProperty("lastName", out var wln) ? 
                                                 wln.GetString() ?? string.Empty : string.Empty;
                                    womanName = string.IsNullOrWhiteSpace(womanFn) ? womanLn : (womanFn + " " + womanLn).Trim();
                                }
                                
                                display = !string.IsNullOrWhiteSpace(manName) && !string.IsNullOrWhiteSpace(womanName) 
                                    ? (manName + " - " + womanName) 
                                    : (manName + womanName);
                            }

                            var item = new RegItem 
                            { 
                                Id = regId!, 
                                Text = string.IsNullOrWhiteSpace(display) ? regId! : display, 
                                Secondary = evtName 
                            };
                            
                            var groupKey = string.IsNullOrWhiteSpace(item.Text) ? "" : item.Text;
                            var grp = Groups.FirstOrDefault(g => string.Equals(g.Key, groupKey, StringComparison.OrdinalIgnoreCase));
                            if (grp == null)
                            {
                                grp = new RegGroup(groupKey);
                                Groups.Add(grp);
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
            // Error handling
            System.Diagnostics.Debug.WriteLine($"LoadAsync error: {ex}");
        }
        finally
        {
            IsBusy = false;
        }
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
                TrainerSelectionVisible = false;
                TrainerSelectionHeaderVisible = false;
                return;
            }
            
            TrainerOptions.Clear();
            foreach (var t in ev.EventTrainersList.OrderBy(x => EventTrainerDisplayHelper.GetTrainerDisplayName(x)))
            {
                var trainerName = EventTrainerDisplayHelper.GetTrainerDisplayName(t) ?? string.Empty;
                TrainerOptions.Add(new TrainerOption(trainerName, 0, t?.Id));
            }

            TrainerSelectionHeaderVisible = true;
            TrainerSelectionVisible = true;

            // Prefill existing demands
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
                        using var doc = JsonDocument.Parse(body2);
                        if (doc.RootElement.TryGetProperty("data", out var data) && 
                            data.TryGetProperty("event", out var evEl) && 
                            evEl.ValueKind != JsonValueKind.Null)
                        {
                            if (evEl.TryGetProperty("eventRegistrations", out var regsEl) && 
                                regsEl.TryGetProperty("nodes", out var nodes) && 
                                nodes.ValueKind == JsonValueKind.Array)
                            {
                                foreach (var node in nodes.EnumerateArray())
                                {
                                    try
                                    {
                                        if (!node.TryGetProperty("id", out var nid)) continue;
                                        var nidStr = nid.GetRawText().Trim('"');
                                        if (!string.Equals(nidStr, _selected.Id, StringComparison.OrdinalIgnoreCase)) 
                                            continue;

                                        if (node.TryGetProperty("eventLessonDemandsByRegistrationIdList", out var demands) && 
                                            demands.ValueKind == JsonValueKind.Array)
                                        {
                                            foreach (var d in demands.EnumerateArray())
                                            {
                                                try
                                                {
                                                    var cnt = d.TryGetProperty("lessonCount", out var lc) && 
                                                             lc.ValueKind == JsonValueKind.Number && 
                                                             lc.TryGetInt32(out var v) ? v : 0;
                                                    string? demandTrainerId = null;
                                                    string? demandTrainerName = null;
                                                    
                                                    if (d.TryGetProperty("trainer", out var tr) && tr.ValueKind != JsonValueKind.Null)
                                                    {
                                                        if (tr.TryGetProperty("id", out var tid)) 
                                                            demandTrainerId = tid.GetRawText().Trim('"');
                                                        if (tr.TryGetProperty("name", out var tname)) 
                                                            demandTrainerName = tname.GetString();
                                                    }
                                                    
                                                    // Find matching option
                                                    TrainerOption? match = null;
                                                    if (!string.IsNullOrWhiteSpace(demandTrainerId)) 
                                                        match = TrainerOptions.FirstOrDefault(o => 
                                                            !string.IsNullOrWhiteSpace(o.Id) && 
                                                            string.Equals(o.Id, demandTrainerId, StringComparison.OrdinalIgnoreCase));
                                                    
                                                    if (match == null && !string.IsNullOrWhiteSpace(demandTrainerName)) 
                                                        match = TrainerOptions.FirstOrDefault(o => 
                                                            string.Equals(o.Name, demandTrainerName, StringComparison.OrdinalIgnoreCase));
                                                    
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
                }
            }
            catch { }
        }
        catch { }
    }

    [RelayCommand]
    private async Task ConfirmAsync()
    {
        if (_selected == null) return;

        // Collect trainer demands
        var trainers = new List<TrainerOption>();
        foreach (var t in TrainerOptions)
        {
            if (t != null && (t.Count > 0 || t.OriginalCount > 0))
            {
                trainers.Add(t);
            }
        }

        if (trainers.Count == 0)
        {
            // Error - need to communicate to view
            return;
        }

        try
        {
            IsBusy = true;
            
            foreach (var t in trainers)
            {
                var clientMutationId = Guid.NewGuid().ToString();
                var gql = new
                {
                    query = "mutation SetLessonDemand($input: SetLessonDemandInput!) { setLessonDemand(input: $input) { eventLessonDemand { id } } }",
                    variables = new 
                    { 
                        input = new 
                        { 
                            registrationId = _selected.Id, 
                            trainerId = t.Id, 
                            lessonCount = t.Count, 
                            clientMutationId = clientMutationId 
                        } 
                    }
                };

                var json = JsonSerializer.Serialize(gql);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await _authService.Http.PostAsync("", content);
                var body = await resp.Content.ReadAsStringAsync();
                
                if (!resp.IsSuccessStatusCode)
                {
                    // Error
                    return;
                }

                using var doc = JsonDocument.Parse(body);
                if (doc.RootElement.TryGetProperty("errors", out var errs) && 
                    errs.ValueKind == JsonValueKind.Array && 
                    errs.GetArrayLength() > 0)
                {
                    var first = errs[0];
                    var msg = first.TryGetProperty("message", out var m) ? m.GetString() : body;
                    // Error
                    return;
                }
            }

            SuccessText = LocalizationService.Get("EditRegistrations_Success_Text") ?? "Změny uloženy";
            SuccessOverlayVisible = true;
            
            await Task.Delay(900);
            await _navigationService.GoBackAsync();
        }
        catch (Exception ex)
        {
            // Error handling
            System.Diagnostics.Debug.WriteLine($"ConfirmAsync error: {ex}");
        }
        finally
        {
            IsBusy = false;
        }
    }

    // Nested classes
    public sealed class RegItem
    {
        public string Id { get; set; } = string.Empty;
        public string Text { get; set; } = string.Empty;
        public string Secondary { get; set; } = string.Empty;
    }

    public sealed class RegGroup : ObservableCollection<RegItem>
    {
        private readonly List<RegItem> _all = new();
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

        public IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

        public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
    }

    public sealed class TrainerOption : INotifyPropertyChanged
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
}
