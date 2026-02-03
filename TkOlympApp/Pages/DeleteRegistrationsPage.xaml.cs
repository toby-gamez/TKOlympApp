using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "eventId")]
public partial class DeleteRegistrationsPage : ContentPage
{
    private readonly DeleteRegistrationsViewModel _viewModel;

    public long EventId
    {
        get => _viewModel?.EventId ?? 0;
        set
        {
            if (_viewModel != null)
                _viewModel.EventId = value;
        }
    }

    public DeleteRegistrationsPage(DeleteRegistrationsViewModel viewModel)
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
