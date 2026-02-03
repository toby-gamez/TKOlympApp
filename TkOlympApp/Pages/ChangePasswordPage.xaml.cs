using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

public partial class ChangePasswordPage : ContentPage
{
    private readonly ChangePasswordViewModel _viewModel;

    public ChangePasswordPage(ChangePasswordViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        InitializeComponent();
        BindingContext = _viewModel;
    }
}
