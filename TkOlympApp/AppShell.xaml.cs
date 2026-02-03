using Microsoft.Maui.Controls;
using Microsoft.Extensions.Logging;
using MauiIcons.Core;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Storage;
using TkOlympApp.Pages;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Microsoft.Extensions.DependencyInjection;

namespace TkOlympApp;

public partial class AppShell : Shell, IDisposable
{
    private readonly ILogger _logger;
    private readonly IServiceProvider _services;
    private readonly IAuthService _authService;
    private readonly INoticeboardService _noticeboardService;
    private readonly IEventNotificationService _eventNotificationService;
    private CancellationTokenSource? _pollCts;
    private CancellationTokenSource? _startupCts;
    private long _lastSeenAnnouncementId;
    private long _lastSeenStickyId;
    private readonly TimeSpan _pollInterval = TimeSpan.FromMinutes(5);
    
    public AppShell(IServiceProvider services, IAuthService authService, INoticeboardService noticeboardService, IEventNotificationService eventNotificationService)
    {
        _logger = LoggerService.CreateLogger<AppShell>();
        _services = services;
        _authService = authService;
        _noticeboardService = noticeboardService;
        _eventNotificationService = eventNotificationService;
        
        InitializeComponent();
        // Workaround for URL-based XAML namespace resolution issues
        _ = new MauiIcon();

        // Ensure tab/root pages are created via DI (not via XAML type activation).
        // This allows constructor injection and removes the parameterless-ctor requirement.
        // Guard DI resolution so startup doesn't crash if a page fails to resolve.
        OverviewShellContent.ContentTemplate = new DataTemplate(() =>
        {
            try { return _services.GetRequiredService<MainPage>(); }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to resolve MainPage via DI; falling back to simple error page");
                return new ContentPage { Content = new Label { Text = "Error loading page" } };
            }
        });

        CalendarShellContent.ContentTemplate = new DataTemplate(() =>
        {
            try { return _services.GetRequiredService<CalendarPage>(); }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to resolve CalendarPage via DI; falling back to simple error page");
                return new ContentPage { Content = new Label { Text = "Error loading page" } };
            }
        });

        NoticeboardShellContent.ContentTemplate = new DataTemplate(() =>
        {
            try { return _services.GetRequiredService<NoticeboardPage>(); }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to resolve NoticeboardPage via DI; falling back to simple error page");
                return new ContentPage { Content = new Label { Text = "Error loading page" } };
            }
        });

        EventsShellContent.ContentTemplate = new DataTemplate(() =>
        {
            try { return _services.GetRequiredService<TkOlympApp.Pages.EventsPage>(); }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to resolve EventsPage via DI; falling back to simple error page");
                return new ContentPage { Content = new Label { Text = "Error loading page" } };
            }
        });

        OtherShellContent.ContentTemplate = new DataTemplate(() =>
        {
            try { return _services.GetRequiredService<OtherPage>(); }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to resolve OtherPage via DI; falling back to simple error page");
                return new ContentPage { Content = new Label { Text = "Error loading page" } };
            }
        });
        
        // Set default route to MainPage (Overview) to prevent navigation flash
        CurrentItem = Items[0];

        // Register routes and navigate conditionally on startup
        Routing.RegisterRoute(nameof(LoginPage), new DiRouteFactory(_services, typeof(LoginPage)));
        Routing.RegisterRoute(nameof(FirstRunPage), new DiRouteFactory(_services, typeof(FirstRunPage)));
        Routing.RegisterRoute(nameof(AboutMePage), new DiRouteFactory(_services, typeof(AboutMePage)));
        Routing.RegisterRoute(nameof(EditSelfPage), new DiRouteFactory(_services, typeof(EditSelfPage)));
        Routing.RegisterRoute(nameof(CouplePage), new DiRouteFactory(_services, typeof(Pages.CouplePage)));
        Routing.RegisterRoute(nameof(EventPage), new DiRouteFactory(_services, typeof(EventPage)));
        Routing.RegisterRoute(nameof(Pages.EventsPage), new DiRouteFactory(_services, typeof(Pages.EventsPage)));
        Routing.RegisterRoute(nameof(PlainTextPage), new DiRouteFactory(_services, typeof(Pages.PlainTextPage)));
        Routing.RegisterRoute(nameof(RegistrationPage), new DiRouteFactory(_services, typeof(RegistrationPage)));
        Routing.RegisterRoute(nameof(DeleteRegistrationsPage), new DiRouteFactory(_services, typeof(DeleteRegistrationsPage)));
        Routing.RegisterRoute(nameof(EditRegistrationsPage), new DiRouteFactory(_services, typeof(Pages.EditRegistrationsPage)));
        Routing.RegisterRoute(nameof(TrainersAndLocationsPage), new DiRouteFactory(_services, typeof(TrainersAndLocationsPage)));
        Routing.RegisterRoute(nameof(CohortGroupsPage), new DiRouteFactory(_services, typeof(CohortGroupsPage)));
        Routing.RegisterRoute(nameof(PeoplePage), new DiRouteFactory(_services, typeof(PeoplePage)));
        Routing.RegisterRoute(nameof(LanguagePage), new DiRouteFactory(_services, typeof(TkOlympApp.Pages.LanguagePage)));
        Routing.RegisterRoute(nameof(AboutAppPage), new DiRouteFactory(_services, typeof(AboutAppPage)));
        Routing.RegisterRoute(nameof(PrivacyPolicyPage), new DiRouteFactory(_services, typeof(PrivacyPolicyPage)));
        Routing.RegisterRoute(nameof(PersonPage), new DiRouteFactory(_services, typeof(PersonPage)));
        Routing.RegisterRoute(nameof(ChangePasswordPage), new DiRouteFactory(_services, typeof(ChangePasswordPage)));
        Routing.RegisterRoute(nameof(Pages.CalendarPage), new DiRouteFactory(_services, typeof(Pages.CalendarPage)));
        Routing.RegisterRoute(nameof(Pages.CalendarViewPage), new DiRouteFactory(_services, typeof(Pages.CalendarViewPage)));
        Routing.RegisterRoute(nameof(LeaderboardPage), new DiRouteFactory(_services, typeof(Pages.LeaderboardPage)));
        Routing.RegisterRoute(nameof(NoticePage), new DiRouteFactory(_services, typeof(NoticePage)));
        Routing.RegisterRoute(nameof(EventNotificationSettingsPage), new DiRouteFactory(_services, typeof(Pages.EventNotificationSettingsPage)));
        Routing.RegisterRoute(nameof(EventNotificationRuleEditPage), new DiRouteFactory(_services, typeof(Pages.EventNotificationRuleEditPage)));

        _logger.LogDebug("AppShell initialized, starting authentication check");
        _startupCts = new CancellationTokenSource();
        Dispatcher.Dispatch(async () =>
        {
            var startupOperationId = Guid.NewGuid().ToString("N")[..8];
            using var scope = _logger.BeginScope(new Dictionary<string, object?>
            {
                ["OperationId"] = startupOperationId,
                ["TenantId"] = AppConstants.TenantId,
                ["Phase"] = "Startup"
            });

            try
            {
                // Show first-run page if needed before further startup navigation
                try
                {
                    if (!FirstRunHelper.HasSeen())
                    {
                        _logger.LogInformation("First run detected, showing FirstRunPage");
                        try 
                        { 
                            await GoToAsync(nameof(FirstRunPage)); 
                        }
                        catch (Exception ex)
                        {
                            _logger.LogError(ex, "Failed to navigate to FirstRunPage");
                        }
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to check first-run status");
                }

                await _authService.InitializeAsync(_startupCts.Token);
                var hasToken = await _authService.HasTokenAsync(_startupCts.Token);
                
                if (!hasToken)
                {
                    _logger.LogInformation("No valid token found, navigating to LoginPage");
                    // Show login without resetting Shell root to avoid route issues
                    // But do not override the FirstRunPage if it is currently shown
                    var current = Shell.Current?.CurrentPage;
                    if (!(current is FirstRunPage))
                    {
                        await GoToAsync(nameof(LoginPage));
                    }
                }
                else
                {
                    _logger.LogInformation("User authenticated, initializing notifications");
                    // Request notification permissions after successful authentication
                    try
                    {
                        await _eventNotificationService.RequestNotificationPermissionAsync(_startupCts.Token);
                        
                        // Initialize background change detection (checks every 1 hour)
                        _eventNotificationService.InitializeBackgroundChangeDetection();
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning(ex, "Failed to initialize notification permissions");
                    }

                    // CurrentItem is already set to MainPage in constructor, no need to navigate
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Unexpected error during AppShell startup");
            }
        });

        // Subscribe to notifications to navigate to the noticeboard when received
        try
        {
            TkOlympApp.Services.NotificationManagerService.EnsureInitialized();
            var mgr = TkOlympApp.Services.NotificationManagerService.Instance;
            if (mgr != null)
            {
                mgr.NotificationReceived += (_, __) =>
                {
                    MainThread.BeginInvokeOnMainThread(async () =>
                    {
                        try
                        {
                            _logger.LogDebug("Notification received, navigating to NoticeBoardPage");
                            await Shell.Current.GoToAsync(nameof(NoticeboardPage));
                        }
                        catch (Exception ex)
                        {
                            _logger.LogWarning(ex, "Failed to navigate to NoticeboardPage from notification");
                        }
                    });
                };
                _logger.LogDebug("Subscribed to notification manager events");
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to subscribe to notification manager");
        }
        
        // Start client-side polling for new announcements
        StartAnnouncementPolling();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _logger.LogDebug("AppShell appearing");
        StartAnnouncementPolling();
    }

    protected override void OnDisappearing()
    {
        base.OnDisappearing();
        _logger.LogDebug("AppShell disappearing");
        StopAnnouncementPolling();
    }

    private void StartAnnouncementPolling()
    {
        if (_pollCts != null) return;
        _pollCts = new CancellationTokenSource();
        _logger.LogInformation("Starting announcement polling (interval: {Interval})", _pollInterval);
        _ = Task.Run(() => PollLoopAsync(_pollCts.Token));
    }

    private void StopAnnouncementPolling()
    {
        try
        {
            _pollCts?.Cancel();
            _pollCts = null;
            _logger.LogInformation("Announcement polling stopped");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Error stopping announcement polling");
        }
    }

    private async Task PollLoopAsync(CancellationToken ct)
    {
        _lastSeenAnnouncementId = Preferences.Get("lastSeenAnnouncementId", 0L);
        _lastSeenStickyId = Preferences.Get("lastSeenStickyId", 0L);
        _logger.LogDebug("Poll loop started (last seen: announcement={AnnouncementId}, sticky={StickyId})", 
                        _lastSeenAnnouncementId, _lastSeenStickyId);

        while (!ct.IsCancellationRequested)
        {
            try
            {
                _logger.LogDebug("Polling for new announcements");
                var announcements = await _noticeboardService.GetMyAnnouncementsAsync(ct: ct);
                var newest = announcements?.OrderByDescending(a => a.CreatedAt).FirstOrDefault();
                if (newest != null && newest.Id > _lastSeenAnnouncementId)
                {
                    _logger.LogInformation("New announcement detected: {AnnouncementId}", newest.Id);
                    NotificationManagerService.EnsureInitialized();
                    var title = LocalizationService.Get("Notification_NewAnnouncement_Title") ?? "Nová aktualita";
                    var message = newest.Title ?? LocalizationService.Get("Notification_NewAnnouncement_Message") ?? "Nová aktualita";
                    NotificationManagerService.Instance?.SendNotification(title, message);
                    _lastSeenAnnouncementId = newest.Id;
                    Preferences.Set("lastSeenAnnouncementId", _lastSeenAnnouncementId);
                }

                var sticky = await _noticeboardService.GetStickyAnnouncementsAsync(ct);
                var newestSticky = sticky?.OrderByDescending(s => s.CreatedAt).FirstOrDefault();
                if (newestSticky != null && newestSticky.Id > _lastSeenStickyId)
                {
                    _logger.LogInformation("New sticky announcement detected: {StickyId}", newestSticky.Id);
                    NotificationManagerService.EnsureInitialized();
                    var titleSticky = LocalizationService.Get("Notification_NewAnnouncement_Title") ?? "Nová aktualita";
                    var messageSticky = newestSticky.Title ?? LocalizationService.Get("Notification_NewAnnouncement_Message") ?? "Nová aktualita";
                    NotificationManagerService.Instance?.SendNotification(titleSticky, messageSticky);
                    _lastSeenStickyId = newestSticky.Id;
                    Preferences.Set("lastSeenStickyId", _lastSeenStickyId);
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error polling for announcements");
            }

            try 
            { 
                await Task.Delay(_pollInterval, ct); 
            } 
            catch (TaskCanceledException) 
            { 
                _logger.LogDebug("Poll loop cancelled");
                break; 
            }
        }
        
        _logger.LogDebug("Poll loop exited");
    }

    public void Dispose()
    {
        _logger.LogDebug("Disposing AppShell");
        StopAnnouncementPolling();
        _startupCts?.Cancel();
        _startupCts?.Dispose();
        _startupCts = null;
        _pollCts?.Dispose();
        _pollCts = null;
    }
}