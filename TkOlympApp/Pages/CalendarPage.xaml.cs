using Microsoft.Extensions.Logging;
using Microsoft.Maui.Controls;
using System;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

/// <summary>
/// CalendarPage - Displays weekly calendar of events with My/All toggle and week navigation.
/// Business logic is handled by <see cref="CalendarViewModel"/>.
/// </summary>
public partial class CalendarPage : ContentPage
{
    private readonly ILogger<CalendarPage> _logger;
    private readonly CalendarViewModel _viewModel;
    private readonly IUserNotifier _notifier;

    public CalendarPage(CalendarViewModel viewModel, IUserNotifier notifier)
    {
        _logger = LoggerService.CreateLogger<CalendarPage>();
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));

        try
        {
            _logger.LogTrace("CalendarPage constructor started");
            InitializeComponent();
            BindingContext = _viewModel;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "CalendarPage InitializeComponent failed");
            Content = new StackLayout
            {
                Children =
                {
                    new Label { Text = (LocalizationService.Get("Error_Loading_Prefix") ?? string.Empty) + ex.Message }
                }
            };
        }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        _logger.LogTrace("CalendarPage OnAppearing");

        try
        {
            await _viewModel.OnAppearingAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "ViewModel.OnAppearingAsync failed");
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Loading_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<CalendarPage>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    protected override async void OnDisappearing()
    {
        try
        {
            await _viewModel.OnDisappearingAsync();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "ViewModel.OnDisappearingAsync failed");
        }

        base.OnDisappearing();
        _logger.LogTrace("CalendarPage OnDisappearing");
    }

    /// <summary>
    /// Public method to request a refresh from external callers (e.g. AppShell after auth).
    /// </summary>
    public Task RefreshEventsAsync() => _viewModel.RefreshEventsAsync();
}
