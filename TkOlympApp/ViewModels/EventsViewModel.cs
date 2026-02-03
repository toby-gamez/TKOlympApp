using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EventsViewModel : ViewModelBase
{
    private readonly IEventService _eventService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    public ObservableCollection<EventItem> PlannedItems { get; } = new();
    public ObservableCollection<EventItem> OccurredItems { get; } = new();
    public ObservableCollection<EventItem> VisibleItems { get; } = new();

    [ObservableProperty]
    private bool _isPlannedActive = true;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _suppressReloadOnNextAppearing;

    [ObservableProperty]
    private Color _tabPlannedBackgroundColor = Colors.Black;

    [ObservableProperty]
    private Color _tabPlannedTextColor = Colors.White;

    [ObservableProperty]
    private Color _tabOccurredBackgroundColor = Colors.Transparent;

    [ObservableProperty]
    private Color _tabOccurredTextColor = Colors.Black;

    public EventsViewModel(IEventService eventService, INavigationService navigationService, IUserNotifier notifier)
    {
        _eventService = eventService ?? throw new ArgumentNullException(nameof(eventService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    public void UpdateTabVisuals(AppTheme theme)
    {
        if (theme == AppTheme.Light)
        {
            if (IsPlannedActive)
            {
                TabPlannedBackgroundColor = Colors.Black;
                TabPlannedTextColor = Colors.White;
                TabOccurredBackgroundColor = Colors.Transparent;
                TabOccurredTextColor = Colors.Black;
            }
            else
            {
                TabOccurredBackgroundColor = Colors.Black;
                TabOccurredTextColor = Colors.White;
                TabPlannedBackgroundColor = Colors.Transparent;
                TabPlannedTextColor = Colors.Black;
            }
        }
        else
        {
            if (IsPlannedActive)
            {
                TabPlannedBackgroundColor = Colors.LightGray;
                TabPlannedTextColor = Colors.Black;
                TabOccurredBackgroundColor = Colors.Transparent;
                TabOccurredTextColor = Colors.White;
            }
            else
            {
                TabOccurredBackgroundColor = Colors.LightGray;
                TabOccurredTextColor = Colors.Black;
                TabPlannedBackgroundColor = Colors.Transparent;
                TabPlannedTextColor = Colors.White;
            }
        }
    }

    partial void OnIsPlannedActiveChanged(bool value)
    {
        UpdateVisibleItems();
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();

        if (SuppressReloadOnNextAppearing)
        {
            SuppressReloadOnNextAppearing = false;
            return;
        }

        await RefreshAsync();
    }

    [RelayCommand]
    private Task ShowPlannedAsync()
    {
        IsPlannedActive = true;
        return Task.CompletedTask;
    }

    [RelayCommand]
    private Task ShowOccurredAsync()
    {
        IsPlannedActive = false;
        return Task.CompletedTask;
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        if (IsBusy) return;

        try
        {
            IsBusy = true;
            IsRefreshing = true;

            var startRange = DateTime.Now.AddMonths(-12);
            var endRange = DateTime.Now.AddMonths(12);

            var list = await _eventService.GetEventInstancesForRangeListAsync(
                startRange,
                endRange,
                onlyType: "CAMP");

            PlannedItems.Clear();
            OccurredItems.Clear();

            foreach (var it in list)
            {
                var ev = it.Event;
                if (ev == null) continue;

                if (string.IsNullOrWhiteSpace(ev.Name)) continue;
                if (!ev.IsVisible) continue;
                if (!string.Equals(ev.Type, "CAMP", StringComparison.OrdinalIgnoreCase)) continue;

                var locationName = ev.Location?.Name ?? string.Empty;

                var item = new EventItem
                {
                    EventInstanceId = it.Id,
                    EventId = ev.Id,
                    EventName = ev.Name ?? "(unknown)",
                    LocationName = locationName,
                    Since = it.Since,
                    Until = it.Until,
                    IsCancelled = it.IsCancelled
                };

                if (item.Until.HasValue && item.Until.Value < DateTime.Now)
                {
                    OccurredItems.Insert(0, item);
                }
                else
                {
                    PlannedItems.Add(item);
                }
            }

            var sortedPlanned = PlannedItems.OrderBy(x => x.Since ?? DateTime.MaxValue).ToList();
            PlannedItems.Clear();
            foreach (var item in sortedPlanned)
            {
                PlannedItems.Add(item);
            }

            UpdateVisibleItems();
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EventsViewModel>("Refresh failed: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Loading_Title") ?? "Chyba",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<EventsViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
        finally
        {
            IsRefreshing = false;
            IsBusy = false;
        }
    }

    [RelayCommand]
    private async Task OpenEventAsync(EventItem? item)
    {
        if (item == null) return;
        if (item.IsCancelled) return;
        if (item.EventId <= 0) return;

        SuppressReloadOnNextAppearing = true;
        await _navigationService.NavigateToAsync(nameof(TkOlympApp.Pages.EventPage), new Dictionary<string, object>
        {
            ["id"] = item.EventId
        });
    }

    private void UpdateVisibleItems()
    {
        VisibleItems.Clear();
        var source = IsPlannedActive ? PlannedItems : OccurredItems;
        foreach (var item in source)
        {
            VisibleItems.Add(item);
        }
    }


    public sealed class EventItem
    {
        public long EventInstanceId { get; set; }
        public long EventId { get; set; }
        public string EventName { get; set; } = string.Empty;
        public string LocationName { get; set; } = string.Empty;
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
        public bool IsCancelled { get; set; }
    }
}
