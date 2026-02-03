using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class DeleteRegistrationsViewModel : ViewModelBase
{
    private readonly IAuthService _authService;
    private readonly IUserService _userService;

    private RegGroup? _selectedGroup;

    public ObservableCollection<RegGroup> Groups { get; } = new();

    [ObservableProperty]
    private RegItem? _selectedItem;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _isDeleteEnabled;

    [ObservableProperty]
    private bool _isSuccessVisible;

    [ObservableProperty]
    private string _successText = LocalizationService.Get("DeleteRegistrations_Success_Text") ?? "Registrace smazána";

    [ObservableProperty]
    private long _eventId;

    public DeleteRegistrationsViewModel(IAuthService authService, IUserService userService)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
    }

    partial void OnEventIdChanged(long value)
    {
        _ = LoadAsync();
    }

    partial void OnSelectedItemChanged(RegItem? value)
    {
        if (value != null)
        {
            _selectedGroup = Groups.FirstOrDefault(g => g.Any(i => i.Id == value.Id));
            IsDeleteEnabled = true;
        }
        else
        {
            _selectedGroup = null;
            IsDeleteEnabled = false;
        }
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadAsync();
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadAsync();
    }

    [RelayCommand]
    private async Task DeleteAsync()
    {
        if (SelectedItem == null && _selectedGroup == null) return;

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

        var ok = await Application.Current?.MainPage?.DisplayAlert(
            LocalizationService.Get("Delete_Confirm_Title") ?? "Confirm",
            confirmText,
            LocalizationService.Get("Button_OK") ?? "OK",
            LocalizationService.Get("Button_Cancel") ?? "Cancel");

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
                idsToDelete.Add(SelectedItem!.Id);
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
                using var resp = await _authService.Http.PostAsync("", content);
                var body = await resp.Content.ReadAsStringAsync();
                if (!resp.IsSuccessStatusCode)
                {
                    await Application.Current?.MainPage?.DisplayAlert(
                        LocalizationService.Get("Error_Title") ?? "Error",
                        body,
                        LocalizationService.Get("Button_OK") ?? "OK");
                    return;
                }

                using var doc = JsonDocument.Parse(body);
                if (!(doc.RootElement.TryGetProperty("data", out var data) && data.TryGetProperty("deleteEventRegistration", out var del) && del.TryGetProperty("eventRegistration", out var er) && er.TryGetProperty("id", out var _)))
                {
                    if (doc.RootElement.TryGetProperty("errors", out var errs) && errs.ValueKind == JsonValueKind.Array && errs.GetArrayLength() > 0)
                    {
                        var first = errs[0];
                        var msg = first.TryGetProperty("message", out var m) ? m.GetString() : body;
                        await Application.Current?.MainPage?.DisplayAlert(
                            LocalizationService.Get("Error_Title") ?? "Error",
                            msg ?? body,
                            LocalizationService.Get("Button_OK") ?? "OK");
                        return;
                    }
                    else
                    {
                        await Application.Current?.MainPage?.DisplayAlert(
                            LocalizationService.Get("Error_Title") ?? "Error",
                            body,
                            LocalizationService.Get("Button_OK") ?? "OK");
                        return;
                    }
                }
            }

            SuccessText = LocalizationService.Get("DeleteRegistrations_Success_Text") ?? "Registrace smazána";
            IsSuccessVisible = true;
            await Task.Delay(900);
            IsSuccessVisible = false;
            await Shell.Current.GoToAsync("..", true);
        }
        catch (Exception ex)
        {
            await Application.Current?.MainPage?.DisplayAlert(
                LocalizationService.Get("Error_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }

    private async Task LoadAsync()
    {
        Groups.Clear();
        try
        {
            IsRefreshing = true;

            await _userService.InitializeAsync();
            var myCouples = new System.Collections.Generic.List<CoupleInfo>();
            try { myCouples = await _userService.GetActiveCouplesFromUsersAsync(); } catch { }
            var myCoupleIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var c in myCouples)
            {
                if (!string.IsNullOrWhiteSpace(c.Id)) myCoupleIds.Add(c.Id!);
            }

            var startRange = DateTime.Now.Date.AddYears(-1).ToString("o");
            var endRange = DateTime.Now.Date.AddYears(1).ToString("o");
            var queryObj = new
            {
                query = @"query($startRange: Datetime!, $endRange: Datetime!) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange) { id event { id name eventRegistrationsList { id person { firstName lastName } couple { id man { firstName lastName } woman { firstName lastName } } } } } }",
                variables = new { startRange = startRange, endRange = endRange }
            };

            var json = JsonSerializer.Serialize(queryObj);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
            {
                await Application.Current?.MainPage?.DisplayAlert(
                    LocalizationService.Get("Error_Loading_Title") ?? "Error",
                    body,
                    LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            using var doc = JsonDocument.Parse(body);
            if (!doc.RootElement.TryGetProperty("data", out var data)) return;
            if (!data.TryGetProperty("eventInstancesForRangeList", out var instances) || instances.ValueKind != JsonValueKind.Array) return;

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
                            if (string.IsNullOrWhiteSpace(regId)) continue;

                            bool isMine = false;
                            if (reg.TryGetProperty("couple", out var coupleEl) && coupleEl.ValueKind != JsonValueKind.Null)
                            {
                                if (coupleEl.TryGetProperty("id", out var cidEl))
                                {
                                    var cid = cidEl.GetRawText().Trim('"');
                                    if (!string.IsNullOrWhiteSpace(cid) && myCoupleIds.Contains(cid)) isMine = true;
                                }
                            }

                            if (!isMine && !string.IsNullOrWhiteSpace(myFull) && reg.TryGetProperty("person", out var personEl) && personEl.ValueKind != JsonValueKind.Null)
                            {
                                var pf = personEl.TryGetProperty("firstName", out var pff) ? pff.GetString() ?? string.Empty : string.Empty;
                                var pl = personEl.TryGetProperty("lastName", out var pll) ? pll.GetString() ?? string.Empty : string.Empty;
                                var pFull = string.IsNullOrWhiteSpace(pf) ? pl : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                                if (!string.IsNullOrWhiteSpace(pFull) && string.Equals(pFull, myFull, StringComparison.OrdinalIgnoreCase)) isMine = true;
                            }

                            if (isMine)
                            {
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
                                var grp = Groups.FirstOrDefault(g => string.Equals(g.Key, groupKey, StringComparison.OrdinalIgnoreCase));
                                if (grp == null)
                                {
                                    grp = new RegGroup(groupKey);
                                    Groups.Add(grp);
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
            await Application.Current?.MainPage?.DisplayAlert(
                LocalizationService.Get("Error_Loading_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    public sealed class RegItem
    {
        public string Id { get; set; } = string.Empty;
        public string Text { get; set; } = string.Empty;
        public string Secondary { get; set; } = string.Empty;
    }

    public sealed class RegGroup : ObservableCollection<RegItem>
    {
        private readonly System.Collections.Generic.List<RegItem> _all = new();
        public string Key { get; }

        public RegGroup(string key)
        {
            Key = key;
        }

        public void AddToGroup(RegItem item)
        {
            _all.Add(item);
            if (Count == 0)
            {
                base.Add(item);
            }
        }

        public System.Collections.Generic.IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

        public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
    }
}
