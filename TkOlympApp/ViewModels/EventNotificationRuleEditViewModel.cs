using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EventNotificationRuleEditViewModel : ViewModelBase
{
    private readonly ITenantService _tenantService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    private EventNotificationSetting? _setting;

    public ObservableCollection<EventNotificationRuleTypeItem> Types { get; } = new();
    public ObservableCollection<EventNotificationRuleTrainerItem> Trainers { get; } = new();

    [ObservableProperty]
    private string _name = string.Empty;

    [ObservableProperty]
    private string _minutes = "15";

    [ObservableProperty]
    private string? _settingId;

    public EventNotificationRuleEditViewModel(ITenantService tenantService, INavigationService navigationService, IUserNotifier notifier)
    {
        _tenantService = tenantService ?? throw new ArgumentNullException(nameof(tenantService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            Types.Clear();
            Types.Add(new EventNotificationRuleTypeItem("CAMP", LocalizationService.Get("EventType_Camp") ?? "soustředění"));
            Types.Add(new EventNotificationRuleTypeItem("LESSON", LocalizationService.Get("EventType_Lesson") ?? "lekce"));
            Types.Add(new EventNotificationRuleTypeItem("HOLIDAY", LocalizationService.Get("EventType_Holiday") ?? "prázdniny"));
            Types.Add(new EventNotificationRuleTypeItem("RESERVATION", LocalizationService.Get("EventType_Reservation") ?? "rezervace"));
            Types.Add(new EventNotificationRuleTypeItem("GROUP", LocalizationService.Get("EventType_Group") ?? "vedená"));

            Trainers.Clear();
            try
            {
                var (_, trainers) = await _tenantService.GetLocationsAndTrainersAsync();
                var list = trainers?
                    .Select(t => new EventNotificationRuleTrainerItem(
                        t.Person?.Id ?? string.Empty,
                        string.Join(' ', new[] { t.Person?.FirstName, t.Person?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s)))))
                    .Where(tr => !string.IsNullOrWhiteSpace(tr.Id) && !string.IsNullOrWhiteSpace(tr.Label))
                    .ToList() ?? new System.Collections.Generic.List<EventNotificationRuleTrainerItem>();

                foreach (var t in list) Trainers.Add(t);
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<EventNotificationRuleEditViewModel>("Failed to load trainers: {0}", new object[] { ex.Message });
            }

            if (!string.IsNullOrWhiteSpace(SettingId) && _setting == null)
            {
                try
                {
                    var all = NotificationSettingsService.GetAll();
                    if (Guid.TryParse(SettingId, out var gid))
                    {
                        var found = all.FirstOrDefault(x => x.Id == gid);
                        if (found != null) _setting = found;
                    }
                }
                catch (Exception ex)
                {
                    LoggerService.SafeLogWarning<EventNotificationRuleEditViewModel>("Failed to resolve notification setting: {0}", new object[] { ex.Message });
                }
            }

            if (_setting != null)
            {
                Name = _setting.Name ?? string.Empty;
                Minutes = ((int)_setting.TimeBefore.TotalMinutes).ToString();

                if (_setting.EventTypes != null)
                {
                    foreach (var t in Types) if (_setting.EventTypes.Contains(t.Code)) t.Selected = true;
                }
                if (_setting.TrainerIds != null)
                {
                    foreach (var tr in Trainers) if (_setting.TrainerIds.Contains(tr.Id)) tr.Selected = true;
                }
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EventNotificationRuleEditViewModel>("Failed to load notification rule: {0}", new object[] { ex.Message });
        }
    }

    [RelayCommand]
    private async Task SaveAsync()
    {
        try
        {
            if (_setting == null)
            {
                _setting = new EventNotificationSetting();
            }

            var name = string.IsNullOrWhiteSpace(Name) ? _setting.Name : Name;
            var minutes = 15;
            if (int.TryParse(Minutes, out var m)) minutes = m;

            var selectedTypes = Types.Where(t => t.Selected).Select(t => t.Code).ToList();
            var selectedTrainers = Trainers.Where(t => t.Selected).Select(t => t.Id).Where(id => !string.IsNullOrWhiteSpace(id)).ToList();

            var updated = _setting with
            {
                Name = name,
                TimeBefore = TimeSpan.FromMinutes(minutes),
                EventTypes = selectedTypes.Count == 0 ? null : selectedTypes,
                TrainerIds = selectedTrainers.Count == 0 ? null : selectedTrainers
            };

            NotificationSettingsService.AddOrUpdate(updated);
            await _navigationService.NavigateToAsync("..", true);
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EventNotificationRuleEditViewModel>("Save failed: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<EventNotificationRuleEditViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

}
