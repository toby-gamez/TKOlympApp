using Microsoft.Maui.Controls;
using System;
using TkOlympApp.Services;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class RegistrationPage : ContentPage
{
    private readonly RegistrationViewModel _viewModel;

    public long EventId
    {
        get => _viewModel?.EventId ?? 0;
        set
        {
            if (_viewModel != null)
                _viewModel.EventId = value;
        }
    }

    public RegistrationPage(RegistrationViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        
        try
        {
            InitializeComponent();
            BindingContext = _viewModel;
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"RegistrationPage XAML init error: {ex}");
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

    // Keep trainer increment/decrement button handlers (they access BindingContext directly)
    private void OnTrainerPlusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is RegistrationTrainerOption to)
            {
                if (to.Count < 10) to.Count = to.Count + 1;
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<RegistrationPage>("Failed to increment trainer count: {0}", new object[] { ex.Message });
        }
    }

    private void OnTrainerMinusClicked(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Button b && b.BindingContext is RegistrationTrainerOption to)
            {
                if (to.Count > 0) to.Count = to.Count - 1;
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<RegistrationPage>("Failed to decrement trainer count: {0}", new object[] { ex.Message });
        }
    }
}
