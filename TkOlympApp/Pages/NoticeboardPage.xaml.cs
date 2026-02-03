using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

public partial class NoticeboardPage : ContentPage
{
    private readonly NoticeboardViewModel _viewModel;

    public NoticeboardPage(NoticeboardViewModel viewModel)
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
