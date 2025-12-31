using Microsoft.Maui.Controls;
using MauiIcons.Core;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Storage;
using TkOlympApp.Pages;
using TkOlympApp.Services;

namespace TkOlympApp;

public partial class AppShell : Shell
{
    private CancellationTokenSource? _pollCts;
    private long _lastSeenAnnouncementId;
    private long _lastSeenStickyId;
    private readonly TimeSpan _pollInterval = TimeSpan.FromMinutes(5);
    public AppShell()
    {
        InitializeComponent();
        // Workaround for URL-based XAML namespace resolution issues
        _ = new MauiIcon();

        // Register routes and navigate conditionally on startup
        Routing.RegisterRoute(nameof(LoginPage), typeof(LoginPage));
        Routing.RegisterRoute(nameof(AboutMePage), typeof(AboutMePage));
        Routing.RegisterRoute(nameof(CouplePage), typeof(Pages.CouplePage));
        Routing.RegisterRoute(nameof(EventPage), typeof(EventPage));
        Routing.RegisterRoute(nameof(PlainTextPage), typeof(Pages.PlainTextPage));
        Routing.RegisterRoute(nameof(RegistrationPage), typeof(RegistrationPage));
        Routing.RegisterRoute(nameof(DeleteRegistrationsPage), typeof(DeleteRegistrationsPage));
        Routing.RegisterRoute(nameof(EditRegistrationsPage), typeof(Pages.EditRegistrationsPage));
        Routing.RegisterRoute(nameof(TrainersAndLocationsPage), typeof(TrainersAndLocationsPage));
        Routing.RegisterRoute(nameof(CohortGroupsPage), typeof(CohortGroupsPage));
        Routing.RegisterRoute(nameof(PeoplePage), typeof(PeoplePage));
        Routing.RegisterRoute(nameof(LanguagePage), typeof(TkOlympApp.Pages.LanguagePage));
        Routing.RegisterRoute(nameof(AboutAppPage), typeof(AboutAppPage));
        Routing.RegisterRoute(nameof(PrivacyPolicyPage), typeof(PrivacyPolicyPage));

        Dispatcher.Dispatch(async () =>
        {
            try
            {
                await AuthService.InitializeAsync();
                var hasToken = await AuthService.HasTokenAsync();
                if (!hasToken)
                {
                    // Show login without resetting Shell root to avoid route issues
                    await GoToAsync(nameof(LoginPage));
                }
                else
                {
                    // Ensure main page is visible and request a refresh after auth is ready
                    try
                    {
                        try { await GoToAsync("//Kalendář"); } catch { }
                        var current = Shell.Current?.CurrentPage;
                        if (current is MainPage mp)
                        {
                            _ = mp.RefreshEventsAsync();
                        }
                    }
                    catch { }
                }
            }
            catch
            {
                // Ignore navigation errors during startup
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
                            await Shell.Current.GoToAsync(nameof(NoticeboardPage));
                        }
                        catch
                        {
                            // best-effort navigation
                        }
                    });
                };
            }
        }
        catch
        {
            // ignore subscription errors
        }
        
        // Start client-side polling for new announcements
        StartAnnouncementPolling();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        StartAnnouncementPolling();
    }

    protected override void OnDisappearing()
    {
        base.OnDisappearing();
        StopAnnouncementPolling();
    }

    private void StartAnnouncementPolling()
    {
        if (_pollCts != null) return;
        _pollCts = new CancellationTokenSource();
        _ = Task.Run(() => PollLoopAsync(_pollCts.Token));
    }

    private void StopAnnouncementPolling()
    {
        try
        {
            _pollCts?.Cancel();
            _pollCts = null;
        }
        catch { }
    }

    private async Task PollLoopAsync(CancellationToken ct)
    {
        _lastSeenAnnouncementId = Preferences.Get("lastSeenAnnouncementId", 0L);
        _lastSeenStickyId = Preferences.Get("lastSeenStickyId", 0L);

        while (!ct.IsCancellationRequested)
        {
            try
            {
                var announcements = await NoticeboardService.GetMyAnnouncementsAsync(ct);
                var newest = announcements?.OrderByDescending(a => a.CreatedAt).FirstOrDefault();
                if (newest != null && newest.Id > _lastSeenAnnouncementId)
                {
                    NotificationManagerService.EnsureInitialized();
                    NotificationManagerService.Instance?.SendNotification("Nová aktualita", newest.Title ?? "Nová aktualita");
                    _lastSeenAnnouncementId = newest.Id;
                    Preferences.Set("lastSeenAnnouncementId", _lastSeenAnnouncementId);
                }

                var sticky = await NoticeboardService.GetStickyAnnouncementsAsync(ct);
                var newestSticky = sticky?.OrderByDescending(s => s.CreatedAt).FirstOrDefault();
                if (newestSticky != null && newestSticky.Id > _lastSeenStickyId)
                {
                    NotificationManagerService.EnsureInitialized();
                    NotificationManagerService.Instance?.SendNotification("Nová aktualita", newestSticky.Title ?? "Nová aktualita");
                    _lastSeenStickyId = newestSticky.Id;
                    Preferences.Set("lastSeenStickyId", _lastSeenStickyId);
                }
            }
            catch
            {
                // ignore transient errors
            }

            try { await Task.Delay(_pollInterval, ct); } catch (TaskCanceledException) { break; }
        }
    }
}