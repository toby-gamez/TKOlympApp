using System;
using System.Collections.ObjectModel;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class EventNotificationSettingsPage : ContentPage
{
    class RuleViewModel
    {
        public EventNotificationSetting Setting { get; }
        public string Name => Setting?.Name ?? string.Empty;
        public string DisplayTimeBefore { get; set; }
        public string DisplayTypes { get; set; }
        public string DisplayTrainers { get; set; }

        public RuleViewModel(EventNotificationSetting s)
        {
            Setting = s;
            DisplayTimeBefore = (int)s.TimeBefore.TotalMinutes + " min";
            DisplayTypes = string.Empty;
            DisplayTrainers = string.Empty;
        }
    }

    ObservableCollection<RuleViewModel> _items = new();

    public EventNotificationSettingsPage()
    {
        InitializeComponent();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadItemsAsync();
    }

    async System.Threading.Tasks.Task LoadItemsAsync()
    {
        var all = NotificationSettingsService.GetAll();

        // fetch trainers to map ids to short names
        Dictionary<string, string> trainerNames = new();
        try
        {
            var (locations, trainers) = await TenantService.GetLocationsAndTrainersAsync();
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

        var list = new List<RuleViewModel>();
        foreach (var s in all)
        {
            var vm = new RuleViewModel(s);

            // types
            if (s.EventTypes == null || s.EventTypes.Count == 0)
            {
                vm.DisplayTypes = LocalizationService.Get("Notification_AllTypes") ?? "všechny typy";
            }
            else
            {
                var labels = s.EventTypes.Select(t => converter.Convert(t, typeof(string), null, System.Globalization.CultureInfo.CurrentUICulture)?.ToString() ?? t);
                vm.DisplayTypes = string.Join(", ", labels);
            }

            // trainers
            if (s.TrainerIds == null || s.TrainerIds.Count == 0)
            {
                vm.DisplayTrainers = LocalizationService.Get("Notification_AllTrainers") ?? "všichni trenéři";
            }
            else
            {
                var names = s.TrainerIds.Select(id => trainerNames.ContainsKey(id) ? trainerNames[id] : id);
                vm.DisplayTrainers = string.Join(", ", names);
            }

            // time
            vm.DisplayTimeBefore = (int)s.TimeBefore.TotalMinutes + " min";

            list.Add(vm);
        }

        _items = new ObservableCollection<RuleViewModel>(list);
        RulesCollectionView.ItemsSource = _items;
    }

    private async void OnAddClicked(object? sender, EventArgs e)
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

            try
            {
                NotificationSettingsService.AddOrUpdate(item);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"OnAddClicked: failed to save new rule: {ex}");
                await DisplayAlert(LocalizationService.Get("Error_Title"), LocalizationService.Get("Error_SaveFailed") ?? "Failed to save rule.", LocalizationService.Get("Button_OK"));
                return;
            }

            try
            {
                await Shell.Current.GoToAsync($"{nameof(EventNotificationRuleEditPage)}?id={item.Id}");
            }
            catch (Exception ex)
            {
                // Shell navigation failed in some environments — fallback to PushAsync with BindingContext
                System.Diagnostics.Debug.WriteLine($"OnAddClicked: Shell.GoToAsync failed: {ex}");
                try
                {
                    var page = new EventNotificationRuleEditPage();
                    page.BindingContext = item;
                    await Shell.Current.Navigation.PushAsync(page);
                }
                catch (Exception ex2)
                {
                    System.Diagnostics.Debug.WriteLine($"OnAddClicked: PushAsync fallback failed: {ex2}");
                    await DisplayAlert(LocalizationService.Get("Error_Title"), ex2.Message, LocalizationService.Get("Button_OK"));
                }
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"OnAddClicked: unexpected error: {ex}");
            try { await DisplayAlert(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK")); } catch { }
        }
    }

    private void OnEnabledToggled(object? sender, ToggledEventArgs e)
    {
        try
        {
            if (sender is Switch sw && sw.BindingContext is RuleViewModel vm)
            {
                var setting = vm.Setting;
                var updated = setting with { Enabled = sw.IsToggled };
                NotificationSettingsService.AddOrUpdate(updated);
                var idx = _items.IndexOf(vm);
                if (idx >= 0) _items[idx] = new RuleViewModel(updated);
            }
        }
        catch { }
    }

    private async void OnEditTimeClicked(object? sender, EventArgs e)
    {
        try
        {
                if (sender is Button btn && btn.BindingContext is RuleViewModel vm)
                {
                    // Navigate to rule edit page using Shell route with setting id
                    await Shell.Current.GoToAsync($"{nameof(EventNotificationRuleEditPage)}?id={vm.Setting.Id}");
                }
        }
        catch { }
    }

    private void OnDeleteClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button btn && btn.BindingContext is RuleViewModel vm)
            {
                NotificationSettingsService.Remove(vm.Setting.Id);
                _items.Remove(vm);
            }
        }
        catch { }
    }
}
