using System.Collections.ObjectModel;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EventsViewModel : ViewModelBase
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly IAuthService _authService;
    private readonly INavigationService _navigationService;

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

    public EventsViewModel(IAuthService authService, INavigationService navigationService)
    {
        _authService = authService ?? throw new ArgumentNullException(nameof(authService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
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

            var query = new GraphQlRequest
            {
                Query =
                    "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $onlyType: EventType) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange, onlyType: $onlyType) { id isCancelled event { id name location { id name } isVisible type } since until } }",
                Variables = new Dictionary<string, object>
                {
                    { "startRange", startRange.ToString("o") },
                    { "endRange", endRange.ToString("o") },
                    { "onlyType", "CAMP" }
                }
            };

            var json = JsonSerializer.Serialize(query, Options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await _authService.Http.PostAsync("", content);
            if (!resp.IsSuccessStatusCode)
            {
                return;
            }

            var body = await resp.Content.ReadAsStringAsync();
            var data = JsonSerializer.Deserialize<GraphQlResponse<EventInstancesData>>(body, Options);
            if (data?.Errors != null && data.Errors.Count > 0)
            {
                return;
            }

            PlannedItems.Clear();
            OccurredItems.Clear();

            var list = data?.Data?.EventInstancesList ?? new List<EventInstanceDto>();
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
        catch
        {
            // Intentionally swallowed (ViewModels can't show alerts directly).
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

    private sealed class GraphQlRequest
    {
        public string Query { get; set; } = string.Empty;
        public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        public T? Data { get; set; }
        public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        public string? Message { get; set; }
    }

    private sealed class EventInstancesData
    {
        [JsonPropertyName("eventInstancesForRangeList")] public List<EventInstanceDto>? EventInstancesList { get; set; }
    }

    private sealed class EventInstanceDto
    {
        [JsonPropertyName("id")] public long Id { get; set; }
        [JsonPropertyName("event")] public EventNode? Event { get; set; }
        [JsonPropertyName("isCancelled")] public bool IsCancelled { get; set; }
        [JsonPropertyName("since")] public DateTime? Since { get; set; }
        [JsonPropertyName("until")] public DateTime? Until { get; set; }
    }

    private sealed class EventNode
    {
        [JsonPropertyName("id")] public long Id { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
        [JsonPropertyName("location")] public EventLocation? Location { get; set; }
        [JsonPropertyName("isVisible")] public bool IsVisible { get; set; }
        [JsonPropertyName("type")] public string? Type { get; set; }
    }

    private sealed class EventLocation
    {
        [JsonPropertyName("id")] public long? Id { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
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
