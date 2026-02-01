using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class EventsPage : ContentPage
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    private bool _isPlannedActive = true;
    private bool _suppressReloadOnNextAppearing = false;

    public ObservableCollection<EventItem> Items { get; } = new();
    public ObservableCollection<EventItem> PlannedItems { get; } = new();
    public ObservableCollection<EventItem> OccurredItems { get; } = new();
    public ObservableCollection<EventItem> VisibleItems { get; } = new();

    public EventsPage()
    {
        InitializeComponent();
        BindingContext = this;
        SetTabVisuals(true);
    }

    private void SetTabVisuals(bool isPlanned)
    {
        _isPlannedActive = isPlanned;
        var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
        if (theme == AppTheme.Light)
        {
            if (isPlanned)
            {
                TabPlannedButton.BackgroundColor = Colors.Black;
                TabPlannedButton.TextColor = Colors.White;
                TabOccurredButton.BackgroundColor = Colors.Transparent;
                TabOccurredButton.TextColor = Colors.Black;
            }
            else
            {
                TabOccurredButton.BackgroundColor = Colors.Black;
                TabOccurredButton.TextColor = Colors.White;
                TabPlannedButton.BackgroundColor = Colors.Transparent;
                TabPlannedButton.TextColor = Colors.Black;
            }
        }
        else
        {
            if (isPlanned)
            {
                TabPlannedButton.BackgroundColor = Colors.LightGray;
                TabPlannedButton.TextColor = Colors.Black;
                TabOccurredButton.BackgroundColor = Colors.Transparent;
                TabOccurredButton.TextColor = Colors.White;
            }
            else
            {
                TabOccurredButton.BackgroundColor = Colors.LightGray;
                TabOccurredButton.TextColor = Colors.Black;
                TabPlannedButton.BackgroundColor = Colors.Transparent;
                TabPlannedButton.TextColor = Colors.White;
            }
        }
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        
        // Subscribe to events
        if (TabPlannedButton != null)
            TabPlannedButton.Clicked += OnTabPlannedClicked;
        if (TabOccurredButton != null)
            TabOccurredButton.Clicked += OnTabOccurredClicked;
        if (EventsRefresh != null)
            EventsRefresh.Refreshing += OnRefresh;
        
        if (_suppressReloadOnNextAppearing)
        {
            _suppressReloadOnNextAppearing = false;
            return;
        }
        _ = RefreshEventsAsync();
    }

    protected override void OnDisappearing()
    {
        // Unsubscribe from events to prevent memory leaks
        if (TabPlannedButton != null)
            TabPlannedButton.Clicked -= OnTabPlannedClicked;
        if (TabOccurredButton != null)
            TabOccurredButton.Clicked -= OnTabOccurredClicked;
        if (EventsRefresh != null)
            EventsRefresh.Refreshing -= OnRefresh;
        
        base.OnDisappearing();
    }

    private async Task RefreshEventsAsync(CancellationToken ct = default)
    {
        try
        {
            // NOTE: eventInstancesList is unbounded and can be very slow.
            // Use a bounded range query to keep responses under the default timeout.
            var startRange = DateTime.Now.AddMonths(-12);
            var endRange = DateTime.Now.AddMonths(12);

            var query = new GraphQlRequest
            {
                Query = "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $onlyType: EventType) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange, onlyType: $onlyType) { id isCancelled event { id name location { id name } isVisible type } since until } }",
                Variables = new Dictionary<string, object>
                {
                    {"startRange", startRange.ToString("o")},
                    {"endRange", endRange.ToString("o")},
                    {"onlyType", "CAMP"}
                }
            };

            var json = JsonSerializer.Serialize(query, Options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await AuthService.Http.PostAsync("", content, ct);
            if (!resp.IsSuccessStatusCode)
            {
                var bodyErr = await resp.Content.ReadAsStringAsync(ct);
                await DisplayAlertAsync(LocalizationService.Get("Error") ?? "Error", bodyErr, LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            var body = await resp.Content.ReadAsStringAsync(ct);
            var data = JsonSerializer.Deserialize<GraphQlResponse<EventInstancesData>>(body, Options);
            if (data?.Errors != null && data.Errors.Count > 0)
            {
                var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
                await DisplayAlertAsync(LocalizationService.Get("Error") ?? "Error", msg, LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            Items.Clear();
            PlannedItems.Clear();
            OccurredItems.Clear();
            var list = data?.Data?.EventInstancesList ?? new List<EventInstanceDto>();
            foreach (var it in list)
            {
                var ev = it.Event;
                if (ev == null) continue;
                // skip events without a name, not visible, or not of type CAMP
                if (string.IsNullOrWhiteSpace(ev.Name)) continue;
                if (!ev.IsVisible) continue;
                if (!string.Equals(ev.Type, "CAMP", StringComparison.OrdinalIgnoreCase)) continue;

                var locationName = ev.Location?.Name ?? string.Empty;
                var since = it.Since;
                var until = it.Until;

                // Insert at 0 to show newest/last-received first
                var item = new EventItem
                {
                    Id = ev.Id,
                    EventInstanceId = it.Id,
                    EventId = ev.Id,
                    EventName = ev.Name ?? "(unknown)",
                    LocationName = locationName,
                    Since = since,
                    Until = until
                    ,IsCancelled = it.IsCancelled
                };

                // classify: occurred if until is set and before now
                if (item.Until.HasValue && item.Until.Value < DateTime.Now)
                {
                    OccurredItems.Insert(0, item);
                }
                else
                {
                    PlannedItems.Add(item);
                }
            }

            // Sort PlannedItems by Since date (earliest first)
            var sortedPlanned = PlannedItems.OrderBy(x => x.Since ?? DateTime.MaxValue).ToList();
            PlannedItems.Clear();
            foreach (var item in sortedPlanned)
            {
                PlannedItems.Add(item);
            }

            // Update the visible collection based on active tab
            UpdateVisibleItems();
        }
        catch (Exception ex)
        {
            try { await DisplayAlertAsync(LocalizationService.Get("Error") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
        finally
        {
            EventsRefresh.IsRefreshing = false;
        }
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        _ = RefreshEventsAsync();
    }

    private void OnTabPlannedClicked(object? sender, EventArgs e)
    {
        SetTabVisuals(true);
        UpdateVisibleItems();
    }

    private void OnTabOccurredClicked(object? sender, EventArgs e)
    {
        SetTabVisuals(false);
        UpdateVisibleItems();
    }

    private void UpdateVisibleItems()
    {
        VisibleItems.Clear();
        var source = _isPlannedActive ? PlannedItems : OccurredItems;
        foreach (var item in source)
        {
            VisibleItems.Add(item);
        }
    }

    private async void OnEventTapped(object? sender, EventArgs e)
    {
        if (sender is VisualElement ve && ve.BindingContext is EventItem it)
        {
            try
            {
                if (it.IsCancelled) return;
                if (it.EventId > 0)
                {
                    _suppressReloadOnNextAppearing = true;
                    await Shell.Current.GoToAsync($"{nameof(EventPage)}?id={it.EventId}");
                }
            }
            catch { }
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

    private sealed class GraphQlError { public string? Message { get; set; } }

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
        public long Id { get; set; }
        public long EventInstanceId { get; set; }
        public long EventId { get; set; }
        public string EventName { get; set; } = string.Empty;
        public string LocationName { get; set; } = string.Empty;
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
        public bool IsCancelled { get; set; }
    }
}
