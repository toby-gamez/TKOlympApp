using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

public partial class LoginPage : ContentPage
{
    private readonly LoginViewModel _viewModel;

    public LoginPage(LoginViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        InitializeComponent();
        Shell.SetTabBarIsVisible(this, false);
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

    private void OnUsernameBorderTapped(object? sender, EventArgs e)
    {
        UsernameEntry?.Focus();
    }

    private void OnPasswordBorderTapped(object? sender, EventArgs e)
    {
        PasswordEntry?.Focus();
    }

    protected override bool OnBackButtonPressed()
    {
        return true;
    }
}
