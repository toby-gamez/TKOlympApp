using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(AnnouncementId), "id")]
public partial class NoticePage : ContentPage
{
    public long AnnouncementId
    {
        get => _viewModel?.AnnouncementId ?? 0;
        set
        {
            if (_viewModel != null)
                _viewModel.AnnouncementId = value;
        }
    }

    private readonly NoticeViewModel _viewModel;

    public NoticePage(NoticeViewModel viewModel)
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
