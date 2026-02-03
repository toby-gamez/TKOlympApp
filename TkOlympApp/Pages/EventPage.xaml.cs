using Microsoft.Maui.Controls;
using System;
using TkOlympApp.Services;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class EventPage : ContentPage
{
    private readonly EventViewModel _viewModel;

    public long EventId
    {
        get => _viewModel?.EventId ?? 0;
        set
        {
            if (_viewModel != null)
                _viewModel.EventId = value;
        }
    }

    public EventPage(EventViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        InitializeComponent();
        BindingContext = _viewModel;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await _viewModel.OnAppearingAsync();
    }

    protected override async void OnDisappearing()
    {
        await _viewModel.OnDisappearingAsync();
        base.OnDisappearing();
    }

    private async void OnRegistrationSelected(object? sender, SelectionChangedEventArgs e)
    {
        try
        {
            if (e.CurrentSelection == null || e.CurrentSelection.Count == 0) return;
            var selected = e.CurrentSelection.FirstOrDefault() as EventViewModel.RegistrationRow;
            if (selected == null) return;
            
            // Clear selection for UX
            try { RegistrationsCollection.SelectedItem = null; }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<EventPage>("Failed to clear registration selection: {0}", new object[] { ex.Message });
            }

            await _viewModel.RegistrationSelectedCommand.ExecuteAsync(selected);
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EventPage>("Registration selection failed: {0}", new object[] { ex.Message });
        }
    }
}
