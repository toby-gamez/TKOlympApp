using Microsoft.Maui.Controls;
using System;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(AnnouncementId), "id")]
public partial class NoticePage : ContentPage
{
    private readonly INoticeboardService _noticeboardService;
    private long _announcementId;
    private bool _appeared;
    private string? _lastBodyHtml;

    public long AnnouncementId
    {
        get => _announcementId;
        set
        {
            _announcementId = value;
            if (_appeared) _ = LoadAsync();
        }
    }

    public NoticePage(INoticeboardService noticeboardService)
    {
        _noticeboardService = noticeboardService;
        InitializeComponent();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _appeared = true;
        if (AnnouncementId != 0)
            _ = LoadAsync();
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

    private async Task LoadAsync()
    {
        if (AnnouncementId == 0) return;
        try
        {
            var a = await _noticeboardService.GetAnnouncementAsync(AnnouncementId);
            if (a == null)
            {
                await DisplayAlertAsync(LocalizationService.Get("NotFound_Title"), LocalizationService.Get("NotFound_Notice"), LocalizationService.Get("Button_OK"));
                return;
            }

            TitleLabel.Text = a.Title ?? string.Empty;
            CreatedAtLabel.Text = (LocalizationService.Get("Notice_Created_Prefix") ?? "Vytvořeno: ") + a.CreatedAt.ToString("dd.MM.yyyy HH:mm");
            if (a.UpdatedAt.HasValue)
            {
                UpdatedAtLabel.Text = (LocalizationService.Get("Notice_Updated_Prefix") ?? "Aktualizováno: ") + a.UpdatedAt.Value.ToString("dd.MM.yyyy HH:mm");
                UpdatedAtLabel.IsVisible = true;
            }
            else
            {
                UpdatedAtLabel.IsVisible = false;
            }

            if (a.Author != null)
            {
                AuthorLabel.Text = (LocalizationService.Get("Notice_Author_Prefix") ?? "Autor: ") + (a.Author.FirstName ?? string.Empty) + " " + (a.Author.LastName ?? string.Empty);
                AuthorLabel.IsVisible = true;
            }
            else
            {
                AuthorLabel.IsVisible = false;
            }

            _lastBodyHtml = a.Body;
            // Render body as sequence of Views (labels, images)
            var views = HtmlHelpers.ToViews(a.Body);
            BodyContent.Children.Clear();
            foreach (var v in views)
            {
                BodyContent.Children.Add(v);
            }
            BodyFrame.IsVisible = BodyContent.Children.Count > 0;
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
    }

    private async void OnCopyBodyClicked(object? sender, EventArgs e)
    {
        try
        {
            var text = HtmlToPlainText(_lastBodyHtml) ?? string.Empty;
            if (string.IsNullOrWhiteSpace(text))
            {
                try { await DisplayAlertAsync(LocalizationService.Get("PlainText_Empty_Title") ?? "Prázdný text", LocalizationService.Get("PlainText_Empty_Body") ?? "Žádný text ke zobrazení", LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
                return;
            }

            try
            {
                // Use PlainTextPage.ShowAsync to avoid any URI/XAML aggregation issues
                await PlainTextPage.ShowAsync(text);
            }
            catch (Exception ex)
            {
                try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
            }
        }
        catch (Exception ex)
        {
            try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
    }

    private async void OnNoticeRefresh(object? sender, EventArgs e)
    {
        try
        {
            await LoadAsync();
        }
        finally
        {
            try { NoticeRefresh.IsRefreshing = false; } catch { }
        }
    }
}
