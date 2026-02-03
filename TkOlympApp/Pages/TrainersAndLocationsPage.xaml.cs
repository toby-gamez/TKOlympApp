using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

public partial class TrainersAndLocationsPage : ContentPage
{
    private readonly TrainersAndLocationsViewModel _viewModel;

    public TrainersAndLocationsPage(TrainersAndLocationsViewModel viewModel)
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
}
