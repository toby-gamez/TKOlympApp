using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using System.ComponentModel;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(SettingId), "id")]
public partial class EventNotificationRuleEditPage : ContentPage
{
    public string? SettingId { get; set; }

    class TypeItem : INotifyPropertyChanged
    {
        public string Code { get; set; } = "";
        public string Label { get; set; } = "";
        bool _selected;
        public bool Selected
        {
            get => _selected;
            set
            {
                if (_selected == value) return;
                _selected = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Selected)));
            }
        }

        public event PropertyChangedEventHandler? PropertyChanged;
    }

    class TrainerItem : INotifyPropertyChanged
    {
        public string Id { get; set; } = "";
        public string Label { get; set; } = "";
        bool _selected;
        public bool Selected
        {
            get => _selected;
            set
            {
                if (_selected == value) return;
                _selected = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Selected)));
            }
        }

        public event PropertyChangedEventHandler? PropertyChanged;
    }

    EventNotificationSetting? _setting;
    List<TypeItem> _types = new();
    List<TrainerItem> _trainers = new();

    public EventNotificationRuleEditPage()
    {
        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"EventNotificationRuleEditPage ctor failed: {ex}");
            // Show a minimal UI fallback to avoid hard crash
            try
            {
                Content = new Microsoft.Maui.Controls.Label { Text = "Failed to load notification editor." };
            }
            catch { }
        }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();

        try
        {
            // Initialize event types from converter known set
            _types = new List<TypeItem>
            {
                new TypeItem{ Code = "CAMP", Label = LocalizationService.Get("EventType_Camp") ?? "soustředění" },
                new TypeItem{ Code = "LESSON", Label = LocalizationService.Get("EventType_Lesson") ?? "lekce" },
                new TypeItem{ Code = "HOLIDAY", Label = LocalizationService.Get("EventType_Holiday") ?? "prázdniny" },
                new TypeItem{ Code = "RESERVATION", Label = LocalizationService.Get("EventType_Reservation") ?? "rezervace" },
                new TypeItem{ Code = "GROUP", Label = LocalizationService.Get("EventType_Group") ?? "vedená" }
            };

            if (TypesCollection != null)
                TypesCollection.ItemsSource = _types;

            // Load trainers from TenantService
                try
                {
                    var (locations, trainers) = await TenantService.GetLocationsAndTrainersAsync();
                    _trainers = trainers?
                        .Select(t => new TrainerItem
                        {
                            Id = t.Person?.Id ?? string.Empty,
                            Label = string.Join(' ', new[] { t.Person?.FirstName, t.Person?.LastName }.Where(s => !string.IsNullOrWhiteSpace(s)))
                        })
                        .Where(tr => !string.IsNullOrWhiteSpace(tr.Id) && !string.IsNullOrWhiteSpace(tr.Label))
                        .ToList() ?? new List<TrainerItem>();
                }
            catch
            {
                _trainers = new List<TrainerItem>();
            }

            if (TrainersCollection != null)
                TrainersCollection.ItemsSource = _trainers;

            // Load setting by query id if provided
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
                catch { }
            }

            // Fallback to BindingContext if provided
            if (_setting == null && BindingContext is EventNotificationSetting set)
            {
                _setting = set;
            }

            if (_setting != null)
            {
                if (NameEntry != null) NameEntry.Text = _setting.Name;
                if (MinutesEntry != null) MinutesEntry.Text = ((int)_setting.TimeBefore.TotalMinutes).ToString();
                if (_setting.EventTypes != null)
                {
                    foreach (var t in _types) if (_setting.EventTypes.Contains(t.Code)) t.Selected = true;
                }
                if (_setting.TrainerIds != null)
                {
                    foreach (var tr in _trainers) if (_setting.TrainerIds.Contains(tr.Id)) tr.Selected = true;
                }
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"EventNotificationRuleEditPage OnAppearing failed: {ex}");
        }
    }

    private void OnTypeCheckedChanged(object? sender, CheckedChangedEventArgs e)
    {
        // nothing - model updated by binding on save
    }

    private void OnTrainerCheckedChanged(object? sender, CheckedChangedEventArgs e)
    {
        // nothing - model updated on save
    }

    private async void OnSaveClicked(object? sender, EventArgs e)
    {
        try
        {
            if (_setting == null)
            {
                _setting = new EventNotificationSetting();
            }

            var name = NameEntry.Text ?? _setting.Name;
            var minutes = 15;
            try
            {
                if (MinutesEntry != null && int.TryParse(MinutesEntry.Text, out var m)) minutes = m;
            }
            catch { }

            var selectedTypes = _types.Where(t => t.Selected).Select(t => t.Code).ToList();
            var selectedTrainers = _trainers.Where(t => t.Selected).Select(t => t.Id).Where(id => !string.IsNullOrWhiteSpace(id)).ToList();

            var updated = _setting with { Name = name, TimeBefore = TimeSpan.FromMinutes(minutes), EventTypes = selectedTypes.Count==0? null : selectedTypes, TrainerIds = selectedTrainers.Count==0? null : selectedTrainers };
            NotificationSettingsService.AddOrUpdate(updated);

            // Pop back
            await Shell.Current.GoToAsync("..", true);
        }
        catch (Exception ex)
        {
            await DisplayAlert(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
    }
}
