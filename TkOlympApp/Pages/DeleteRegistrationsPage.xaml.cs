using Microsoft.Maui.Controls;
using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "eventId")]
public partial class DeleteRegistrationsPage : ContentPage
{
    private readonly ObservableCollection<RegGroup> _groups = new();
    private RegItem? _selected;
    private RegGroup? _selectedGroup;
    private long _eventId;

    public long EventId
    {
        get => _eventId;
        set
        {
            _eventId = value;
            // reload when event id is set
            _ = LoadAsync();
        }
    }

    public DeleteRegistrationsPage()
    {
        try
        {
            InitializeComponent();
        }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"DeleteRegistrationsPage XAML init error: {ex}");
                try
                {
                    Device.BeginInvokeOnMainThread(async () =>
                    {
                        try { await DisplayAlert(LocalizationService.Get("XAML_Error_Title") ?? "XAML Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
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

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private sealed class RegItem
    {
        public string Id { get; set; } = string.Empty; // BigInt as string
        public string Text { get; set; } = string.Empty; // person/couple display
        public string Secondary { get; set; } = string.Empty; // event or extra info
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

        public System.Collections.Generic.IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

        public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
    }

    private async Task LoadAsync()
    {
        _groups.Clear();
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        try
        {
            // Determine current user's identifiers (personId and couple ids)
            await UserService.InitializeAsync();
            var myPersonId = UserService.CurrentPersonId;
            var myCouples = new System.Collections.Generic.List<UserService.CoupleInfo>();
            try { myCouples = await UserService.GetActiveCouplesFromUsersAsync(); } catch { }
            var myCoupleIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var c in myCouples)
            {
                if (!string.IsNullOrWhiteSpace(c.Id)) myCoupleIds.Add(c.Id!);
            }

            // Fetch event instances in a wide range and request registration ids
            var startRange = DateTime.Now.Date.AddYears(-1).ToString("o");
            var endRange = DateTime.Now.Date.AddYears(1).ToString("o");
            var queryObj = new
            {
                query = @"query($startRange: Datetime!, $endRange: Datetime!) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange) { id event { id name eventRegistrationsList { id person { firstName lastName } couple { id man { firstName lastName } woman { firstName lastName } } } } } }",
                variables = new { startRange = startRange, endRange = endRange }
            };

            var json = JsonSerializer.Serialize(queryObj);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await AuthService.Http.PostAsync("", content);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
            {
                await DisplayAlert(LocalizationService.Get("Error_Loading_Title") ?? "Error", body, LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            using var doc = JsonDocument.Parse(body);
            if (!doc.RootElement.TryGetProperty("data", out var data)) return;
            if (!data.TryGetProperty("eventInstancesForRangeList", out var instances) || instances.ValueKind != JsonValueKind.Array) return;

            // Get current user's name for person matching
            var me = await UserService.GetCurrentUserAsync();
            var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
            var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
            var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : string.IsNullOrWhiteSpace(myLast) ? myFirst : (myFirst + " " + myLast).Trim();

            var seenRegIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var inst in instances.EnumerateArray())
            {
                try
                {
                    if (!inst.TryGetProperty("event", out var ev) || ev.ValueKind == JsonValueKind.Null) continue;
                    // filter by EventId if provided
                    try
                    {
                        if (EventId != 0 && ev.TryGetProperty("id", out var evIdEl))
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
                            if (string.IsNullOrWhiteSpace(regId)) continue; // we need id to delete

                            bool isMine = false;
                            // check couple id match
                            if (reg.TryGetProperty("couple", out var coupleEl) && coupleEl.ValueKind != JsonValueKind.Null)
                            {
                                if (coupleEl.TryGetProperty("id", out var cidEl))
                                {
                                    var cid = cidEl.GetRawText().Trim('"');
                                    if (!string.IsNullOrWhiteSpace(cid) && myCoupleIds.Contains(cid)) isMine = true;
                                }
                            }

                            // check person name match
                            if (!isMine && !string.IsNullOrWhiteSpace(myFull) && reg.TryGetProperty("person", out var personEl) && personEl.ValueKind != JsonValueKind.Null)
                            {
                                var pf = personEl.TryGetProperty("firstName", out var pff) ? pff.GetString() ?? string.Empty : string.Empty;
                                var pl = personEl.TryGetProperty("lastName", out var pll) ? pll.GetString() ?? string.Empty : string.Empty;
                                var pFull = string.IsNullOrWhiteSpace(pf) ? pl : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                                if (!string.IsNullOrWhiteSpace(pFull) && string.Equals(pFull, myFull, StringComparison.OrdinalIgnoreCase)) isMine = true;
                            }

                            if (isMine)
                            {
                                if (string.IsNullOrWhiteSpace(regId)) continue;
                                if (seenRegIds.Contains(regId)) continue;
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
                                    var manFn = coupleEl2.TryGetProperty("man", out var manEl) && manEl.ValueKind != JsonValueKind.Null ? (manEl.TryGetProperty("firstName", out var mfn) ? mfn.GetString() ?? string.Empty : string.Empty) : string.Empty;
                                    var manLn = coupleEl2.TryGetProperty("man", out var manEl2) && manEl2.ValueKind != JsonValueKind.Null ? (manEl2.TryGetProperty("lastName", out var mln) ? mln.GetString() ?? string.Empty : string.Empty) : string.Empty;
                                    var womanFn = coupleEl2.TryGetProperty("woman", out var womEl) && womEl.ValueKind != JsonValueKind.Null ? (womEl.TryGetProperty("firstName", out var wfn) ? wfn.GetString() ?? string.Empty : string.Empty) : string.Empty;
                                    var womanLn = coupleEl2.TryGetProperty("woman", out var womEl2) && womEl2.ValueKind != JsonValueKind.Null ? (womEl2.TryGetProperty("lastName", out var wln) ? wln.GetString() ?? string.Empty : string.Empty) : string.Empty;
                                    var manName = string.IsNullOrWhiteSpace(manFn) ? manLn : (manFn + " " + manLn).Trim();
                                    var womanName = string.IsNullOrWhiteSpace(womanFn) ? womanLn : (womanFn + " " + womanLn).Trim();
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
                        }
                        catch { }
                    }
                }
                catch { }
            }
        }
        catch (Exception ex)
        {
            await DisplayAlert(LocalizationService.Get("Error_Loading_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK");
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
        }
    }

    private void OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        _selected = e.CurrentSelection?.Count > 0 ? e.CurrentSelection[0] as RegItem : null;
        if (_selected != null)
        {
            // find the group that contains this item
            _selectedGroup = _groups.FirstOrDefault(g => g.Any(i => i.Id == _selected.Id));
            DeleteButton.IsEnabled = true;
        }
        else
        {
            _selectedGroup = null;
            DeleteButton.IsEnabled = false;
        }
    }

    

    private async void OnDeleteClicked(object? sender, EventArgs e)
    {
        // Deletion supports either a single selected RegItem or a selected RegGroup (delete all group's registrations)
        if (_selected == null && _selectedGroup == null) return;

        string confirmText;
        if (_selectedGroup != null)
        {
            var template = LocalizationService.Get("Delete_Confirm_Message_Group") ?? "Smazat všechny registrace pro {0}?";
            try { confirmText = string.Format(template, _selectedGroup.Key); } catch { confirmText = $"Smazat všechny registrace pro {_selectedGroup.Key}?"; }
        }
        else
        {
            confirmText = LocalizationService.Get("Delete_Confirm_Message") ?? "Smazat registraci?";
        }

        var ok = await DisplayAlert(LocalizationService.Get("Delete_Confirm_Title") ?? "Confirm", confirmText, LocalizationService.Get("Button_OK") ?? "OK", LocalizationService.Get("Button_Cancel") ?? "Cancel");
        if (!ok) return;

        try
        {
            var idsToDelete = new System.Collections.Generic.List<string>();
            if (_selectedGroup != null)
            {
                var firstId = _selectedGroup.GetAllIds().FirstOrDefault();
                if (!string.IsNullOrWhiteSpace(firstId)) idsToDelete.Add(firstId);
            }
            else
            {
                idsToDelete.Add(_selected!.Id);
            }
            foreach (var id in idsToDelete)
            {
                var clientMutationId = Guid.NewGuid().ToString();
                var gql = new
                {
                    query = "mutation DeleteReg($input: DeleteEventRegistrationInput!) { deleteEventRegistration(input: $input) { eventRegistration { id } } }",
                    variables = new { input = new { id = id, clientMutationId = clientMutationId } }
                };

                var json = JsonSerializer.Serialize(gql);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await AuthService.Http.PostAsync("", content);
                var body = await resp.Content.ReadAsStringAsync();
                if (!resp.IsSuccessStatusCode)
                {
                    await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", body, LocalizationService.Get("Button_OK") ?? "OK");
                    return;
                }

                using var doc = JsonDocument.Parse(body);
                if (!(doc.RootElement.TryGetProperty("data", out var data) && data.TryGetProperty("deleteEventRegistration", out var del) && del.TryGetProperty("eventRegistration", out var er) && er.TryGetProperty("id", out var idEl)))
                {
                    if (doc.RootElement.TryGetProperty("errors", out var errs) && errs.ValueKind == JsonValueKind.Array && errs.GetArrayLength() > 0)
                    {
                        var first = errs[0];
                        var msg = first.TryGetProperty("message", out var m) ? m.GetString() : body;
                        await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", msg ?? body, LocalizationService.Get("Button_OK") ?? "OK");
                        return;
                    }
                    else
                    {
                        await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", body, LocalizationService.Get("Button_OK") ?? "OK");
                        return;
                    }
                }
            }

            // show success overlay briefly then go back
            try
            {
                SuccessText.Text = LocalizationService.Get("DeleteRegistrations_Success_Text") ?? "Registrace smazána";
            }
            catch { }
            SuccessOverlay.IsVisible = true;
            await Task.Delay(900);
            try { await Shell.Current.GoToAsync("..", true); } catch { try { await Shell.Current.Navigation.PopAsync(); } catch { } }
        }
        catch (Exception ex)
        {
            await DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK");
        }
    }
}
