using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Polly.CircuitBreaker;
using TkOlympApp.Helpers;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class NoticeboardViewModel : ViewModelBase
{
    private readonly INoticeboardService _noticeboardService;
    private readonly INoticeboardNotificationService _noticeboardNotificationService;
    private readonly IUserNotifier _notifier;
    private readonly INavigationService _navigationService;

    public ObservableCollection<AnnouncementItem> Announcements { get; } = new();

    [ObservableProperty]
    private bool _isAktualityActive = true;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private Color _tabAktualityBackgroundColor = Colors.Black;

    [ObservableProperty]
    private Color _tabAktualityTextColor = Colors.White;

    [ObservableProperty]
    private Color _tabStalaBackgroundColor = Colors.Transparent;

    [ObservableProperty]
    private Color _tabStalaTextColor = Colors.Black;

    [ObservableProperty]
    private AnnouncementItem? _selectedAnnouncement;

    public NoticeboardViewModel(
        INoticeboardService noticeboardService,
        INoticeboardNotificationService noticeboardNotificationService,
        IUserNotifier notifier,
        INavigationService navigationService)
    {
        _noticeboardService = noticeboardService ?? throw new ArgumentNullException(nameof(noticeboardService));
        _noticeboardNotificationService = noticeboardNotificationService ?? throw new ArgumentNullException(nameof(noticeboardNotificationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));

        UpdateTabVisuals(true);
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        UpdateTabVisuals(IsAktualityActive);
        await LoadCurrentTabAsync();
    }

    partial void OnSelectedAnnouncementChanged(AnnouncementItem? value)
    {
        if (value == null) return;
        _ = OpenAnnouncementAsync(value);
    }

    [RelayCommand]
    private async Task ShowAktualityAsync()
    {
        IsAktualityActive = true;
        UpdateTabVisuals(true);
        await LoadAnnouncementsAsync();
    }

    [RelayCommand]
    private async Task ShowStalaAsync()
    {
        IsAktualityActive = false;
        UpdateTabVisuals(false);
        await LoadStickyAnnouncementsAsync();
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadCurrentTabAsync();
    }

    private Task LoadCurrentTabAsync()
    {
        return IsAktualityActive ? LoadAnnouncementsAsync() : LoadStickyAnnouncementsAsync();
    }

    private void UpdateTabVisuals(bool aktuality)
    {
        IsAktualityActive = aktuality;
        var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
        if (theme == AppTheme.Light)
        {
            if (aktuality)
            {
                TabAktualityBackgroundColor = Colors.Black;
                TabAktualityTextColor = Colors.White;
                TabStalaBackgroundColor = Colors.Transparent;
                TabStalaTextColor = Colors.Black;
            }
            else
            {
                TabStalaBackgroundColor = Colors.Black;
                TabStalaTextColor = Colors.White;
                TabAktualityBackgroundColor = Colors.Transparent;
                TabAktualityTextColor = Colors.Black;
            }
        }
        else
        {
            if (aktuality)
            {
                TabAktualityBackgroundColor = Colors.LightGray;
                TabAktualityTextColor = Colors.Black;
                TabStalaBackgroundColor = Colors.Transparent;
                TabStalaTextColor = Colors.White;
            }
            else
            {
                TabStalaBackgroundColor = Colors.LightGray;
                TabStalaTextColor = Colors.Black;
                TabAktualityBackgroundColor = Colors.Transparent;
                TabAktualityTextColor = Colors.White;
            }
        }
    }

    private static string? HtmlToPlainText(string? html)
    {
        if (string.IsNullOrWhiteSpace(html)) return html;
        var text = html;
        text = Regex.Replace(text, "<br\\s*/?>", "<br>", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "(?:<br>\\s*){2,}", "<br>", RegexOptions.IgnoreCase);
        text = text.Replace("<br>", "\n");
        text = Regex.Replace(text, "</p>", "\n\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<p[^>]*>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<li[^>]*>", "\n• ", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</li>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</h[1-6]>", "\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<h[1-6][^>]*>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<[^>]+>", string.Empty);
        text = WebUtility.HtmlDecode(text);
        return text.Trim();
    }

    private async Task LoadAnnouncementsAsync()
    {
        try
        {
            IsRefreshing = true;
            var list = await _noticeboardService.GetMyAnnouncementsAsync();
            var views = new List<AnnouncementItem>();
            if (list != null && list.Count > 0)
            {
                foreach (var a in list.OrderByDescending(x => x.CreatedAt))
                {
                    var plain = HtmlToPlainText(a.Body ?? string.Empty) ?? string.Empty;
                    views.Add(new AnnouncementItem
                    {
                        Id = a.Id,
                        Title = a.Title ?? string.Empty,
                        PlainBody = plain,
                        CreatedAtText = a.CreatedAt.ToLocalTime().ToString("dd.MM.yyyy HH:mm"),
                        IsSticky = a.IsSticky,
                        IsVisible = a.IsVisible
                    });
                }
            }
            else
            {
                views.Add(new AnnouncementItem { Title = "Žádné oznámení.", PlainBody = string.Empty, CreatedAtText = string.Empty });
            }

            Announcements.Clear();
            foreach (var v in views)
            {
                Announcements.Add(v);
            }

            await _noticeboardNotificationService.CheckAndNotifyChangesAsync(list);
        }
        catch (BrokenCircuitException bce)
        {
            LoggerService.SafeLogWarning<NoticeboardViewModel>("Circuit open when loading announcements: {0}", new object[] { bce.Message });
            await SafeNotifyAsync(
                LocalizationService.Get("Service_Unavailable_Title") ?? "Service unavailable",
                LocalizationService.Get("Service_Unavailable_Message") ?? "The service is temporarily unavailable. Please try again later.");
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogError<NoticeboardViewModel>(ex, "Failed to load announcements: {0}", new object[] { ex.Message });
            await SafeNotifyAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message);
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private async Task LoadStickyAnnouncementsAsync()
    {
        try
        {
            IsRefreshing = true;
            var list = await _noticeboardService.GetStickyAnnouncementsAsync();
            var views = new List<AnnouncementItem>();
            if (list != null && list.Count > 0)
            {
                foreach (var a in list.OrderByDescending(x => x.CreatedAt))
                {
                    var plainBody = HtmlToPlainText(a.Body) ?? string.Empty;
                    var titleFromBody = plainBody.Length > 80 ? plainBody[..80].Trim() + "…" : plainBody;
                    var title = !string.IsNullOrWhiteSpace(a.Title)
                        ? a.Title
                        : (!string.IsNullOrWhiteSpace(titleFromBody) ? titleFromBody : "Stálé oznámení");

                    views.Add(new AnnouncementItem
                    {
                        Id = a.Id,
                        Title = title,
                        PlainBody = plainBody,
                        CreatedAtText = a.CreatedAt.ToLocalTime().ToString("dd.MM.yyyy HH:mm"),
                        IsSticky = a.IsSticky,
                        IsVisible = true
                    });
                }
            }
            else
            {
                views.Add(new AnnouncementItem { Title = "Žádné stálé oznámení.", PlainBody = string.Empty, CreatedAtText = string.Empty });
            }

            Announcements.Clear();
            foreach (var v in views)
            {
                Announcements.Add(v);
            }
        }
        catch (BrokenCircuitException bce)
        {
            LoggerService.SafeLogWarning<NoticeboardViewModel>("Circuit open when loading sticky announcements: {0}", new object[] { bce.Message });
            await SafeNotifyAsync(
                LocalizationService.Get("Service_Unavailable_Title") ?? "Service unavailable",
                LocalizationService.Get("Service_Unavailable_Message") ?? "The service is temporarily unavailable. Please try again later.");
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogError<NoticeboardViewModel>(ex, "Failed to load sticky announcements: {0}", new object[] { ex.Message });
            await SafeNotifyAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message);
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private async Task OpenAnnouncementAsync(AnnouncementItem item)
    {
        try
        {
            await _navigationService.NavigateToAsync(nameof(Pages.NoticePage), new Dictionary<string, object>
            {
                ["id"] = item.Id
            });
        }
        catch (Exception ex)
        {
            await SafeNotifyAsync(LocalizationService.Get("Error_Navigation_Title") ?? "Error", ex.Message);
        }
        finally
        {
            SelectedAnnouncement = null;
        }
    }

    private async Task SafeNotifyAsync(string title, string message)
    {
        try
        {
            await _notifier.ShowAsync(title, message, LocalizationService.Get("Button_OK") ?? "OK");
        }
        catch (Exception notifyEx)
        {
            LoggerService.SafeLogWarning<NoticeboardViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
        }
    }

    public sealed record AnnouncementItem
    {
        public long Id { get; init; }
        public string Title { get; init; } = string.Empty;
        public string PlainBody { get; init; } = string.Empty;
        public string CreatedAtText { get; init; } = string.Empty;
        public bool IsSticky { get; init; }
        public bool IsVisible { get; init; } = true;
    }
}
