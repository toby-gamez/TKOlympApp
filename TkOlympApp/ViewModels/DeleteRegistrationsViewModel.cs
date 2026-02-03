using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class DeleteRegistrationsViewModel : ViewModelBase
{
    private readonly IEventService _eventService;
    private readonly IUserService _userService;
    private readonly IUserNotifier _notifier;

    private DeleteRegistrationsRegGroup? _selectedGroup;

    public ObservableCollection<DeleteRegistrationsRegGroup> Groups { get; } = new();

    [ObservableProperty]
    private DeleteRegistrationsRegItem? _selectedItem;

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

    public DeleteRegistrationsViewModel(IEventService eventService, IUserService userService, IUserNotifier notifier)
    {
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    partial void OnEventIdChanged(long value)
    {
        _ = LoadAsync();
    }

    partial void OnSelectedItemChanged(DeleteRegistrationsRegItem? value)
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
            try { confirmText = string.Format(template, _selectedGroup.Key); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<DeleteRegistrationsViewModel>("Failed to format delete group confirm text: {0}", new object[] { ex.Message });
                confirmText = $"Smazat všechny registrace pro {_selectedGroup.Key}?";
            }
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
                var okDelete = await _eventService.DeleteEventRegistrationAsync(id);
                if (!okDelete)
                {
                    throw new InvalidOperationException(LocalizationService.Get("DeleteRegistrations_DeleteFailed") ?? "Smazání registrace se nezdařilo.");
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
            LoggerService.SafeLogWarning<DeleteRegistrationsViewModel>("Delete failed: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<DeleteRegistrationsViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
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
            try { myCouples = await _userService.GetActiveCouplesFromUsersAsync(); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<DeleteRegistrationsViewModel>("Failed to load couples: {0}", new object[] { ex.Message });
            }
            var myCoupleIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var c in myCouples)
            {
                if (!string.IsNullOrWhiteSpace(c.Id)) myCoupleIds.Add(c.Id!);
            }

            var startRange = DateTime.Now.Date.AddYears(-1);
            var endRange = DateTime.Now.Date.AddYears(1);
            var instances = await _eventService.GetEventInstancesForRangeListAsync(startRange, endRange);

            var me = await _userService.GetCurrentUserAsync();
            var myFirst = me?.UJmeno?.Trim() ?? string.Empty;
            var myLast = me?.UPrijmeni?.Trim() ?? string.Empty;
            var myFull = string.IsNullOrWhiteSpace(myFirst) ? myLast : string.IsNullOrWhiteSpace(myLast) ? myFirst : (myFirst + " " + myLast).Trim();

            var seenRegIds = new System.Collections.Generic.HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var inst in instances)
            {
                var ev = inst.Event;
                if (ev == null) continue;

                if (EventId != 0 && ev.Id != EventId) continue;

                var evtName = ev.Name ?? string.Empty;
                var regs = ev.EventRegistrationsList ?? new System.Collections.Generic.List<EventRegistrationShort>();
                foreach (var reg in regs)
                {
                    var regId = reg.Id;
                    if (string.IsNullOrWhiteSpace(regId)) continue;

                    bool isMine = false;
                    var coupleId = reg.Couple?.Id;
                    if (!string.IsNullOrWhiteSpace(coupleId) && myCoupleIds.Contains(coupleId))
                    {
                        isMine = true;
                    }

                    if (!isMine && !string.IsNullOrWhiteSpace(myFull))
                    {
                        var pf = reg.Person?.FirstName ?? string.Empty;
                        var pl = reg.Person?.LastName ?? string.Empty;
                        var pFull = string.IsNullOrWhiteSpace(pf) ? pl : (string.IsNullOrWhiteSpace(pl) ? pf : (pf + " " + pl).Trim());
                        if (!string.IsNullOrWhiteSpace(pFull) && string.Equals(pFull, myFull, StringComparison.OrdinalIgnoreCase))
                        {
                            isMine = true;
                        }
                    }

                    if (!isMine) continue;
                    if (seenRegIds.Contains(regId)) continue;
                    seenRegIds.Add(regId);

                    string display = string.Empty;
                    if (reg.Person != null)
                    {
                        var fn = reg.Person.FirstName ?? string.Empty;
                        var ln = reg.Person.LastName ?? string.Empty;
                        display = string.IsNullOrWhiteSpace(fn) ? (string.IsNullOrWhiteSpace(ln) ? reg.Person.Name ?? string.Empty : ln)
                            : (string.IsNullOrWhiteSpace(ln) ? fn : (fn + " " + ln).Trim());
                    }
                    else if (reg.Couple != null)
                    {
                        var manFn = reg.Couple.Man?.FirstName ?? string.Empty;
                        var manLn = reg.Couple.Man?.LastName ?? string.Empty;
                        var womanFn = reg.Couple.Woman?.FirstName ?? string.Empty;
                        var womanLn = reg.Couple.Woman?.LastName ?? string.Empty;
                        var manName = string.IsNullOrWhiteSpace(manFn) ? manLn : (manFn + " " + manLn).Trim();
                        var womanName = string.IsNullOrWhiteSpace(womanFn) ? womanLn : (womanFn + " " + womanLn).Trim();
                        display = !string.IsNullOrWhiteSpace(manName) && !string.IsNullOrWhiteSpace(womanName)
                            ? (manName + " - " + womanName)
                            : (manName + womanName);
                    }

                    var item = new DeleteRegistrationsRegItem { Id = regId, Text = string.IsNullOrWhiteSpace(display) ? regId : display, Secondary = evtName };
                    var groupKey = string.IsNullOrWhiteSpace(item.Text) ? "" : item.Text;
                    var grp = Groups.FirstOrDefault(g => string.Equals(g.Key, groupKey, StringComparison.OrdinalIgnoreCase));
                    if (grp == null)
                    {
                        grp = new DeleteRegistrationsRegGroup(groupKey);
                        Groups.Add(grp);
                    }
                    grp.AddToGroup(item);
                }
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<DeleteRegistrationsViewModel>("Failed to load registrations: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Loading_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<DeleteRegistrationsViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
        finally
        {
            IsRefreshing = false;
        }
    }

}
