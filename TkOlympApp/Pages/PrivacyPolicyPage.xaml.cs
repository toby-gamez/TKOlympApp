using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

public partial class PrivacyPolicyPage : ContentPage
{
    private readonly PrivacyPolicyViewModel _viewModel;

    public PrivacyPolicyPage(PrivacyPolicyViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        InitializeComponent();
        BindingContext = _viewModel;
    }
}
