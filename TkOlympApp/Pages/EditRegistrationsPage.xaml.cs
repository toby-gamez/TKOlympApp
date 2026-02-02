using Microsoft.Maui.Controls;
using System;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "eventId")]
public partial class EditRegistrationsPage : ContentPage
{
    private readonly EditRegistrationsViewModel _viewModel;

    public long EventId
    {
        get => _viewModel?.EventId ?? 0;
        set
        {
            if (_viewModel != null)
                _viewModel.EventId = value;
        }
    }

    public EditRegistrationsPage(EditRegistrationsViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        
        try
        {
            InitializeComponent();
            BindingContext = _viewModel;
            RegistrationsCollection.ItemsSource = _viewModel.Groups;
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"EditRegistrationsPage XAML init error: {ex}");
        }
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

    private void OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        var sel = e.CurrentSelection?.FirstOrDefault();
        _viewModel.OnSelectionChanged(sel as EditRegistrationsViewModel.RegItem);
    }

    private void OnRegistrationsRemainingThresholdReached(object? sender, EventArgs e)
    {
        _viewModel.OnRemainingItemsThresholdReached();
    }

    // Keep trainer increment/decrement button handlers
    private void OnTrainerPlusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is EditRegistrationsViewModel.TrainerOption to)
            {
                if (to.Count < 100) to.Count = to.Count + 1;
            }
        }
        catch { }
    }

    private void OnTrainerMinusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is EditRegistrationsViewModel.TrainerOption to)
            {
                if (to.Count > 0) to.Count = to.Count - 1;
            }
        }
        catch { }
    }
}
