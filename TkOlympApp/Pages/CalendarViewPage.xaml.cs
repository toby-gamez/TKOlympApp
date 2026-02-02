using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

/// <summary>
/// Code-behind for CalendarViewPage. Business logic is in CalendarViewViewModel.
/// </summary>
public partial class CalendarViewPage : ContentPage
{
    private readonly CalendarViewViewModel _viewModel;

    public CalendarViewPage(CalendarViewViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        InitializeComponent();
        BindingContext = _viewModel;

        // Wire up UI references that ViewModel needs
        _viewModel.TimelineLayout = TimelineLayout;
        _viewModel.TimeLabelsStack = TimeLabelsStack;
        _viewModel.SetTopTabVisualsAction = SetTopTabVisuals;
        _viewModel.UpdateViewButtonsVisualsAction = UpdateViewButtonsVisuals;

        // Initialize tab visuals
        try { SetTopTabVisuals(_viewModel.OnlyMine); } catch { }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await _viewModel.OnAppearingAsync();
    }

    protected override async void OnDisappearing()
    {
        base.OnDisappearing();
        await _viewModel.OnDisappearingAsync();
    }

    private void SetTopTabVisuals(bool myActive)
    {
        try
        {
            var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
            if (theme == AppTheme.Light)
            {
                if (myActive)
                {
                    TabMyButton.BackgroundColor = Colors.Black;
                    TabMyButton.TextColor = Colors.White;
                    TabAllButton.BackgroundColor = Colors.Transparent;
                    TabAllButton.TextColor = Colors.Black;
                }
                else
                {
                    TabAllButton.BackgroundColor = Colors.Black;
                    TabAllButton.TextColor = Colors.White;
                    TabMyButton.BackgroundColor = Colors.Transparent;
                    TabMyButton.TextColor = Colors.Black;
                }
            }
            else
            {
                if (myActive)
                {
                    TabMyButton.BackgroundColor = Colors.LightGray;
                    TabMyButton.TextColor = Colors.Black;
                    TabAllButton.BackgroundColor = Colors.Transparent;
                    TabAllButton.TextColor = Colors.White;
                }
                else
                {
                    TabAllButton.BackgroundColor = Colors.LightGray;
                    TabAllButton.TextColor = Colors.Black;
                    TabMyButton.BackgroundColor = Colors.Transparent;
                    TabMyButton.TextColor = Colors.White;
                }
            }
        }
        catch { }
    }

    private void UpdateViewButtonsVisuals()
    {
        // ViewModel already updates enabled states via properties
        // This is kept for any future platform-specific UI updates
    }
}


