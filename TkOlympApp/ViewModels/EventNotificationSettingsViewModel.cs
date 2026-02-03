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

public partial class EventNotificationSettingsViewModel : ViewModelBase
{
    private readonly ITenantService _tenantService;
    private readonly INavigationService _navigationService;

    public ObservableCollection<RuleItem> Items { get; } = new();

    public EventNotificationSettingsViewModel(ITenantService tenantService, INavigationService navigationService)
    {
        _tenantService = tenantService ?? throw new ArgumentNullException(nameof(tenantService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadItemsAsync();
    }

    [RelayCommand]
    private async Task AddAsync()
    {
        try
        {
            var item = new EventNotificationSetting
            {
                Id = Guid.NewGuid(),
                Name = LocalizationService.Get("Notification_DefaultRuleName") ?? "Moje upozornění",
                Enabled = true,
                TimeBefore = TimeSpan.FromMinutes(15)
            };

            NotificationSettingsService.AddOrUpdate(item);

            await _navigationService.NavigateToAsync(nameof(Pages.EventNotificationRuleEditPage), new System.Collections.Generic.Dictionary<string, object>
            {
                ["id"] = item.Id.ToString()
            });
        }
        catch (Exception ex)
        {
            await Application.Current?.MainPage?.DisplayAlert(
                LocalizationService.Get("Error_Title") ?? "Error",
                ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }

    [RelayCommand]
    private async Task EditAsync(RuleItem? item)
    {
        try
        {
            if (item == null) return;
            await _navigationService.NavigateToAsync(nameof(Pages.EventNotificationRuleEditPage), new System.Collections.Generic.Dictionary<string, object>
            {
                ["id"] = item.Setting.Id.ToString()
            });
        }
        catch { }
    }

    [RelayCommand]
    private void Delete(RuleItem? item)
    {
        try
        {
            if (item == null) return;
            NotificationSettingsService.Remove(item.Setting.Id);
            Items.Remove(item);
        }
        catch { }
    }

    private async Task LoadItemsAsync()
    {
        var all = NotificationSettingsService.GetAll();

        var trainerNames = new System.Collections.Generic.Dictionary<string, string>();
        try
        {
            var (_, trainers) = await _tenantService.GetLocationsAndTrainersAsync();
            foreach (var t in trainers)
            {
                var id = t.Person?.Id;
                if (string.IsNullOrWhiteSpace(id)) continue;
                var first = t.Person?.FirstName ?? string.Empty;
                var last = t.Person?.LastName ?? string.Empty;
                string shortName;
                if (!string.IsNullOrWhiteSpace(last))
                {
                    var initial = !string.IsNullOrWhiteSpace(first) ? first[0] + "." : string.Empty;
                    shortName = (initial + " " + last).Trim();
                }
                else shortName = first.Trim();
                trainerNames[id] = shortName;
            }
        }
        catch { }

        var converter = new TkOlympApp.Converters.EventTypeToLabelConverter();

        Items.Clear();
        foreach (var s in all)
        {
            string displayTypes;
            if (s.EventTypes == null || s.EventTypes.Count == 0)
            {
                displayTypes = LocalizationService.Get("Notification_AllTypes") ?? "všechny typy";
            }
            else
            {
                var labels = s.EventTypes.Select(t => converter.Convert(t, typeof(string), null, System.Globalization.CultureInfo.CurrentUICulture)?.ToString() ?? t);
                displayTypes = string.Join(", ", labels);
            }

            string displayTrainers;
            if (s.TrainerIds == null || s.TrainerIds.Count == 0)
            {
                displayTrainers = LocalizationService.Get("Notification_AllTrainers") ?? "všichni trenéři";
            }
            else
            {
                var names = s.TrainerIds.Select(id => trainerNames.ContainsKey(id) ? trainerNames[id] : id);
                displayTrainers = string.Join(", ", names);
            }

            var item = new RuleItem(s, displayTypes, displayTrainers);
            Items.Add(item);
        }
    }

    public partial class RuleItem : ObservableObject
    {
        public RuleItem(EventNotificationSetting setting, string displayTypes, string displayTrainers)
        {
            Setting = setting;
            DisplayTypes = displayTypes;
            DisplayTrainers = displayTrainers;
            _isEnabled = setting.Enabled;
        }

        public EventNotificationSetting Setting { get; private set; }
        public string Name => Setting.Name ?? string.Empty;
        public string DisplayTimeBefore => (int)Setting.TimeBefore.TotalMinutes + " min";
        public string DisplayTypes { get; }
        public string DisplayTrainers { get; }

        [ObservableProperty]
        private bool _isEnabled;

        partial void OnIsEnabledChanged(bool value)
        {
            try
            {
                Setting = Setting with { Enabled = value };
                NotificationSettingsService.AddOrUpdate(Setting);
            }
            catch { }
        }
    }
}
