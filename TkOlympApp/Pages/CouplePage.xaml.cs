using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(CoupleId), "id")]
public partial class CouplePage : ContentPage
{
    private readonly CoupleViewModel _viewModel;

    public string? CoupleId
    {
        get => _viewModel?.CoupleId;
        set
        {
            if (_viewModel != null)
                _viewModel.CoupleId = value;
        }
    }

    public CouplePage(CoupleViewModel viewModel)
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
