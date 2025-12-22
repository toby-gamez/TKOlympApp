using Microsoft.Maui.Controls;
using System;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Helpers;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(AnnouncementId), "id")]
public partial class NoticePage : ContentPage
{
    private long _announcementId;
    private bool _appeared;

    public long AnnouncementId
    {
        get => _announcementId;
        set
        {
            _announcementId = value;
            if (_appeared) _ = LoadAsync();
        }
    }

    public NoticePage()
    {
        InitializeComponent();
    }

    public NoticePage(long id) : this()
    {
        AnnouncementId = id;
        Dispatcher.Dispatch(async () => await LoadAsync());
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
            var a = await NoticeboardService.GetAnnouncementAsync(AnnouncementId);
            if (a == null)
            {
                await DisplayAlertAsync("Nenalezeno", "Oznámení nebylo nalezeno.", "OK");
                return;
            }

            TitleLabel.Text = a.Title ?? string.Empty;
            CreatedAtLabel.Text = $"Vytvořeno: {a.CreatedAt:dd.MM.yyyy HH:mm}";
            if (a.UpdatedAt.HasValue)
            {
                UpdatedAtLabel.Text = $"Aktualizováno: {a.UpdatedAt:dd.MM.yyyy HH:mm}";
                UpdatedAtLabel.IsVisible = true;
            }
            else
            {
                UpdatedAtLabel.IsVisible = false;
            }

            if (a.Author != null)
            {
                AuthorLabel.Text = $"Autor: {a.Author.FirstName ?? string.Empty} {a.Author.LastName ?? string.Empty}";
                AuthorLabel.IsVisible = true;
            }
            else
            {
                AuthorLabel.IsVisible = false;
            }

            var formatted = HtmlHelpers.ToFormattedString(a.Body);
            BodyLabel.FormattedText = formatted;
            BodyFrame.IsVisible = formatted?.Spans?.Count > 0;
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba načtení", ex.Message, "OK");
        }
    }
}
