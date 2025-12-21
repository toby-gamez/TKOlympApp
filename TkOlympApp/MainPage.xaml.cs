using Microsoft.Maui.Controls;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class MainPage : ContentPage
{
    private readonly ObservableCollection<EventService.EventInstance> _events = new();
    private bool _isLoading;

    public MainPage()
    {
        InitializeComponent();
        EventsCollection.ItemsSource = _events;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadEventsAsync();
    }

    private async Task LoadEventsAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        EventsCollection.IsVisible = false;
        try
        {
            var today = DateTime.Now.Date;
            var start = today;
            var end = today.AddDays(30);
            var onlyMine = OnlyMineSwitch?.IsToggled ?? true;
            List<EventService.EventInstance> events;
            if (onlyMine)
            {
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end, onlyMine: true);
            }
            else
            {
                // Use the global eventInstancesForRangeList query when the switch is off
                events = await EventService.GetEventInstancesForRangeListAsync(start, end);
            }
            _events.Clear();
            foreach (var e in events)
            {
                _events.Add(e);
            }
        }
        catch (Exception ex)
        {
            await DisplayAlert("Chyba načtení", ex.Message, "OK");
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
            EventsCollection.IsVisible = true;
            _isLoading = false;
        }
    }

    private async void OnlyMineSwitch_Toggled(object? sender, ToggledEventArgs e)
    {
        await LoadEventsAsync();
    }

    private async void EventsCollection_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        var selected = e.CurrentSelection?.FirstOrDefault() as EventService.EventInstance;
            if (selected != null)
        {
            // Clear selection to avoid repeated triggers
            EventsCollection.SelectedItem = null;
            if (selected.IsCancelled)
            {
                return;
            }
            if (selected.Event?.Id is long eventId)
        {
                var since = selected.Since.HasValue ? selected.Since.Value.ToString("o") : null;
                var until = selected.Until.HasValue ? selected.Until.Value.ToString("o") : null;
                var uri = $"EventPage?id={eventId}" +
                          (since != null ? $"&since={Uri.EscapeDataString(since)}" : string.Empty) +
                          (until != null ? $"&until={Uri.EscapeDataString(until)}" : string.Empty);
                await Shell.Current.GoToAsync(uri);
        }
        }
    }

    private async void OnEventCardTapped(object? sender, TappedEventArgs e)
    {
        if (sender is Border frame && frame.BindingContext is EventService.EventInstance instance && instance.Event?.Id is long eventId)
        {
            if (instance.IsCancelled)
            {
                return;
            }
            var since = instance.Since.HasValue ? instance.Since.Value.ToString("o") : null;
            var until = instance.Until.HasValue ? instance.Until.Value.ToString("o") : null;
            var uri = $"EventPage?id={eventId}" +
                      (since != null ? $"&since={Uri.EscapeDataString(since)}" : string.Empty) +
                      (until != null ? $"&until={Uri.EscapeDataString(until)}" : string.Empty);
            await Shell.Current.GoToAsync(uri);
        }
    }

    private async void OnReloadClicked(object? sender, EventArgs e)
    {
        await LoadEventsAsync();
    }
}