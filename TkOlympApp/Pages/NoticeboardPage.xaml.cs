using System;
using System.Linq;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Controls.Shapes;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp.Pages;

public partial class NoticeboardPage : ContentPage
{
    private bool _isAktualityActive = true;

    public NoticeboardPage()
    {
        InitializeComponent();
        // default to Aktuality tab
        SetTabVisuals(true);
        Dispatcher.Dispatch(async () => await LoadAnnouncementsAsync());
    }

    private void SetTabVisuals(bool aktivniAktuality)
    {
        _isAktualityActive = aktivniAktuality;
        var theme = Application.Current?.RequestedTheme ?? AppTheme.Unspecified;
        if (theme == AppTheme.Light)
        {
            // Light mode: active button black background + white text, inactive: transparent bg + black text
            if (aktivniAktuality)
            {
                TabAktualityButton.BackgroundColor = Colors.Black;
                TabAktualityButton.TextColor = Colors.White;
                TabStalaButton.BackgroundColor = Colors.Transparent;
                TabStalaButton.TextColor = Colors.Black;
            }
            else
            {
                TabStalaButton.BackgroundColor = Colors.Black;
                TabStalaButton.TextColor = Colors.White;
                TabAktualityButton.BackgroundColor = Colors.Transparent;
                TabAktualityButton.TextColor = Colors.Black;
            }
        }
        else
        {
            // Dark mode (or unspecified): keep current light-gray background for active, text black; inactive text white
            if (aktivniAktuality)
            {
                TabAktualityButton.BackgroundColor = Colors.LightGray;
                TabAktualityButton.TextColor = Colors.Black;
                TabStalaButton.BackgroundColor = Colors.Transparent;
                TabStalaButton.TextColor = Colors.White;
            }
            else
            {
                TabStalaButton.BackgroundColor = Colors.LightGray;
                TabStalaButton.TextColor = Colors.Black;
                TabAktualityButton.BackgroundColor = Colors.Transparent;
                TabAktualityButton.TextColor = Colors.White;
            }
        }
    }

    private async void OnTabAktualityClicked(object? sender, EventArgs e)
    {
        SetTabVisuals(true);
        LoadingIndicator.IsVisible = true;
        LoadingIndicator.IsRunning = true;
        await LoadAnnouncementsAsync();
        LoadingIndicator.IsRunning = false;
        LoadingIndicator.IsVisible = false;
    }

    private void OnTabStalaClicked(object? sender, EventArgs e)
    {
        SetTabVisuals(false);
        Dispatcher.Dispatch(async () => await LoadStickyAnnouncementsAsync());
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
        LoadingIndicator.IsVisible = true;
        LoadingIndicator.IsRunning = true;
        try
        {
            var list = await NoticeboardService.GetMyAnnouncementsAsync();
            var views = new List<AnnouncementView>();
            if (list != null && list.Count > 0)
            {
                foreach (var a in list.OrderByDescending(x => x.CreatedAt))
                {
                    var plain = HtmlToPlainText(a.Body ?? string.Empty) ?? string.Empty;
                    views.Add(new AnnouncementView
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
                views.Add(new AnnouncementView { Title = "Žádné oznámení.", PlainBody = string.Empty, CreatedAtText = string.Empty });
            }

            AnnouncementsCollection.ItemsSource = views;
            
            // Check for changes and send notifications
            await NoticeboardNotificationService.CheckAndNotifyChangesAsync(list);
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            LoadingIndicator.IsRunning = false;
            LoadingIndicator.IsVisible = false;
            try
            {
                if (AnnouncementsRefresh != null)
                    AnnouncementsRefresh.IsRefreshing = false;
            }
            catch
            {
                // ignore
            }
        }
    }

    private record AnnouncementView
    {
        public long Id { get; init; }
        public string Title { get; init; } = string.Empty;
        public string PlainBody { get; init; } = string.Empty;
        public string CreatedAtText { get; init; } = string.Empty;
        public bool IsSticky { get; init; }
        public bool IsVisible { get; init; } = true;
    }

    private async Task LoadStickyAnnouncementsAsync()
    {
        SetTabVisuals(false);
        LoadingIndicator.IsVisible = true;
        LoadingIndicator.IsRunning = true;
        try
        {
            var list = await NoticeboardService.GetStickyAnnouncementsAsync();
            var views = new List<AnnouncementView>();
            if (list != null && list.Count > 0)
            {
                foreach (var a in list.OrderByDescending(x => x.CreatedAt))
                {
                    var plain = HtmlHelpers.ToFormattedString(a.Body)?.ToString() ?? string.Empty; // keep simple snippet
                    var plainBody = HtmlToPlainText(a.Body) ?? string.Empty;
                    var titleFromBody = plainBody.Length > 80 ? plainBody.Substring(0, 80).Trim() + "…" : plainBody;
                    var title = !string.IsNullOrWhiteSpace(a.Title) ? a.Title : (!string.IsNullOrWhiteSpace(titleFromBody) ? titleFromBody : "Stálé oznámení");
                    views.Add(new AnnouncementView
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
                views.Add(new AnnouncementView { Title = "Žádné stálé oznámení.", PlainBody = string.Empty, CreatedAtText = string.Empty });
            }

            AnnouncementsCollection.ItemsSource = views;
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            LoadingIndicator.IsRunning = false;
            LoadingIndicator.IsVisible = false;
            try
            {
                if (AnnouncementsRefresh != null)
                    AnnouncementsRefresh.IsRefreshing = false;
            }
            catch
            {
                // ignore
            }
        }
    }

    private async void OnAnnouncementTapped(object? sender, EventArgs e)
    {
        try
        {
            if (sender is Border b && b.BindingContext is AnnouncementView av)
            {
                // Navigate to detail page; use PushAsync so route registration isn't required
                await Navigation.PushAsync(new NoticePage(av.Id));
            }
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Navigation_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
    }

    private async void OnAnnouncementsRefresh(object? sender, EventArgs e)
    {
        if (_isAktualityActive)
            await LoadAnnouncementsAsync();
        else
            await LoadStickyAnnouncementsAsync();

        try
        {
            if (AnnouncementsRefresh != null)
                AnnouncementsRefresh.IsRefreshing = false;
        }
        catch
        {
            // ignore
        }
    }
}
