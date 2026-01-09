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

    public ObservableCollection<EventItem> Items { get; } = new();
    public ObservableCollection<EventItem> PlannedItems { get; } = new();
    public ObservableCollection<EventItem> OccurredItems { get; } = new();

    public EventsPage()
    {
        InitializeComponent();
        BindingContext = this;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _ = RefreshEventsAsync();
    }

    private async Task RefreshEventsAsync(CancellationToken ct = default)
    {
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        try
        {
            var query = new GraphQlRequest
            {
                Query = "query MyQuery { eventInstancesList { event { id name location { id name } isVisible type since until } since until location { id name } } }"
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

                // pick filled values preferring instance-level, fallback to event-level
                var locationName = it.Location?.Name ?? ev.Location?.Name ?? string.Empty;
                var since = it.Since ?? ev.Since;
                var until = it.Until ?? ev.Until;

                // Insert at 0 to show newest/last-received first
                var item = new EventItem
                {
                    Id = ev.Id,
                    EventId = ev.Id,
                    EventName = ev.Name ?? "(unknown)",
                    LocationName = locationName,
                    Since = since,
                    Until = until
                };

                // classify: occurred if until is set and before now
                if (item.Until.HasValue && item.Until.Value < DateTime.Now)
                {
                    OccurredItems.Insert(0, item);
                }
                else
                {
                    PlannedItems.Insert(0, item);
                }
            }
        }
        catch (Exception ex)
        {
            try { await DisplayAlertAsync(LocalizationService.Get("Error") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
            EventsRefresh.IsRefreshing = false;
        }
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        _ = RefreshEventsAsync();
    }

    private async void OnEventTapped(object? sender, EventArgs e)
    {
        if (sender is Border b && b.BindingContext is EventItem it)
        {
            try
            {
                await Shell.Current.GoToAsync($"{nameof(EventPage)}?id={it.EventId}");
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
        [JsonPropertyName("eventInstancesList")] public List<EventInstanceDto>? EventInstancesList { get; set; }
    }

    private sealed class EventInstanceDto
    {
        [JsonPropertyName("event")] public EventNode? Event { get; set; }
        [JsonPropertyName("since")] public DateTime? Since { get; set; }
        [JsonPropertyName("until")] public DateTime? Until { get; set; }
        [JsonPropertyName("location")] public EventLocation? Location { get; set; }
    }

    private sealed class EventNode
    {
        [JsonPropertyName("id")] public long Id { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
        [JsonPropertyName("location")] public EventLocation? Location { get; set; }
        [JsonPropertyName("isVisible")] public bool IsVisible { get; set; }
        [JsonPropertyName("since")] public DateTime? Since { get; set; }
        [JsonPropertyName("until")] public DateTime? Until { get; set; }
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
        public long EventId { get; set; }
        public string EventName { get; set; } = string.Empty;
        public string LocationName { get; set; } = string.Empty;
        public DateTime? Since { get; set; }
        public DateTime? Until { get; set; }
    }
}
