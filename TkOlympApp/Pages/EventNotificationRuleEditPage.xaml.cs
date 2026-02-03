using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(SettingId), "id")]
public partial class EventNotificationRuleEditPage : ContentPage
{
    private readonly EventNotificationRuleEditViewModel _viewModel;

    public string? SettingId
    {
        get => _viewModel?.SettingId;
        set
        {
            if (_viewModel != null)
                _viewModel.SettingId = value;
        }
    }

    public EventNotificationRuleEditPage(EventNotificationRuleEditViewModel viewModel)
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
