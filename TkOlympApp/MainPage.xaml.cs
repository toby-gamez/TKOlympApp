using Microsoft.Maui.Controls;
using Microsoft.Extensions.Logging;
using System;
using System.Threading.Tasks;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.ViewModels;

namespace TkOlympApp;

public partial class MainPage : ContentPage
{
    private readonly ILogger _logger;
    private readonly MainPageViewModel _viewModel;
    private readonly IUserNotifier _notifier;
    private bool _suppressReloadOnNextAppearing = false;

    public MainPage(MainPageViewModel viewModel, IUserNotifier notifier)
    {
        _logger = LoggerService.CreateLogger<MainPage>();
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
        
        try
        {
            InitializeComponent();
            BindingContext = _viewModel;
        }
        catch (Exception ex)
        {
            _logger.LogCritical(ex, "Failed to initialize MainPage XAML");
            Content = new Microsoft.Maui.Controls.StackLayout
            {
                Children =
                {
                    new Microsoft.Maui.Controls.Label { Text = LocalizationService.Get("Error_Loading_Prefix") + ex.Message }
                }
            };
        }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        _logger.LogDebug("MainPage appearing");

        // Skip full initialization if it was suppressed (returning from child page)
        if (_suppressReloadOnNextAppearing)
        {
            _suppressReloadOnNextAppearing = false;
            _logger.LogDebug("Skipping reload (returning from child page)");
            return;
        }
        try
        {
            await _viewModel.OnAppearingAsync();
        }
        catch (OperationCanceledException)
        {
            _logger.LogInformation("Initialization cancelled");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to initialize MainPage");
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    LocalizationService.Get("Initialization_Error") ?? "Initialization failed",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<MainPage>("Failed to show initialization error to user: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    protected override async void OnDisappearing()
    {
        await _viewModel.OnDisappearingAsync();
        base.OnDisappearing();
        _logger.LogDebug("MainPage disappeared");
    }

    private async void OnEventCardTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is VisualElement ve && ve.BindingContext is EventInstance evt && evt.Event != null)
            {
                if (evt.IsCancelled) return;
                
                _logger.LogDebug("Navigating to event {EventId}", evt.Event.Id);
                _suppressReloadOnNextAppearing = true;
                await Shell.Current.GoToAsync($"EventPage?id={evt.Event.Id}");
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPage>("Failed to navigate to event page: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    LocalizationService.Get("Navigation_Error") ?? "Cannot open event details",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<MainPage>("Failed to show navigation error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    private async void OnGroupedRowTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Grid grid && grid.BindingContext is MainPageViewModel.GroupedEventRow row)
            {
                var inst = row.Instance;
                var evt = inst?.Event;
                if (inst?.IsCancelled ?? false) return;
                if (evt != null)
                {
                    _logger.LogDebug("Navigating to grouped event {EventId}", evt.Id);
                    _suppressReloadOnNextAppearing = true;
                    await Shell.Current.GoToAsync($"EventPage?id={evt.Id}");
                }
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPage>("Failed to navigate from grouped row: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    LocalizationService.Get("Navigation_Error") ?? "Cannot open event details",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<MainPage>("Failed to show navigation error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    private async void OnAnnouncementTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is VisualElement ve2 && ve2.BindingContext is Announcement announcement)
            {
                _logger.LogDebug("Navigating to announcement {AnnouncementId}", announcement.Id);
                await Shell.Current.GoToAsync($"NoticePage?id={announcement.Id}");
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPage>("Failed to navigate to announcement: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    LocalizationService.Get("Navigation_Error") ?? "Cannot open announcement",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<MainPage>("Failed to show navigation error: {0}", new object[] { notifyEx.Message });
            }
        }
    }

    private async void OnCampTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is VisualElement ve3 && ve3.BindingContext is MainPageViewModel.CampItem camp)
            {
                _logger.LogDebug("Navigating to camp event {EventId}", camp.EventId);
                _suppressReloadOnNextAppearing = true;
                await Shell.Current.GoToAsync($"EventPage?id={camp.EventId}");
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<MainPage>("Failed to navigate to camp: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    LocalizationService.Get("Navigation_Error") ?? "Cannot open camp details",
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<MainPage>("Failed to show navigation error: {0}", new object[] { notifyEx.Message });
            }
        }
    }
}
