using Microsoft.Maui.Controls;
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
            var events = await EventService.GetMyEventInstancesForRangeAsync(start, end, onlyMine: onlyMine);
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
        if (selected?.Event?.Id is long eventId)
        {
            // Clear selection to avoid repeated triggers
            EventsCollection.SelectedItem = null;
            await Shell.Current.GoToAsync($"EventPage?id={eventId}");
        }
    }

    private async void OnEventCardTapped(object? sender, TappedEventArgs e)
    {
        if (sender is Frame frame && frame.BindingContext is EventService.EventInstance instance && instance.Event?.Id is long eventId)
        {
            await Shell.Current.GoToAsync($"EventPage?id={eventId}");
        }
    }
}