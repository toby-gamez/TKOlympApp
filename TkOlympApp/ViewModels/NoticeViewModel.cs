using System;
using System.Collections.ObjectModel;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class NoticeViewModel : ViewModelBase
{
    private readonly INoticeboardService _noticeboardService;
    private readonly IUserNotifier _notifier;

    private bool _appeared;
    private string? _lastBodyHtml;

    public ObservableCollection<View> BodyViews { get; } = new();

    [ObservableProperty]
    private long _announcementId;

    [ObservableProperty]
    private string _titleText = string.Empty;

    [ObservableProperty]
    private string _authorText = string.Empty;

    [ObservableProperty]
    private bool _authorVisible;

    [ObservableProperty]
    private string _createdAtText = string.Empty;

    [ObservableProperty]
    private string _updatedAtText = string.Empty;

    [ObservableProperty]
    private bool _updatedAtVisible;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _bodyVisible;

    public NoticeViewModel(INoticeboardService noticeboardService, IUserNotifier notifier)
    {
        _noticeboardService = noticeboardService ?? throw new ArgumentNullException(nameof(noticeboardService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    partial void OnAnnouncementIdChanged(long value)
    {
        if (_appeared && value != 0)
        {
            _ = LoadAsync();
        }
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        _appeared = true;
        if (AnnouncementId != 0)
        {
            await LoadAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadAsync();
    }

    [RelayCommand]
    private async Task CopyBodyAsync()
    {
        try
        {
            var text = HtmlToPlainText(_lastBodyHtml) ?? string.Empty;
            if (string.IsNullOrWhiteSpace(text))
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("PlainText_Empty_Title") ?? "Prázdný text",
                    LocalizationService.Get("PlainText_Empty_Body") ?? "Žádný text ke zobrazení",
                    LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            try
            {
                await Pages.PlainTextPage.ShowAsync(text);
            }
            catch (Exception ex)
            {
                await _notifier.ShowAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
        }
        catch (Exception ex)
        {
            await _notifier.ShowAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
    }

    private async Task LoadAsync()
    {
        if (AnnouncementId == 0) return;
        IsRefreshing = true;

        try
        {
            var a = await _noticeboardService.GetAnnouncementAsync(AnnouncementId);
            if (a == null)
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("NotFound_Title") ?? "Not found",
                    LocalizationService.Get("NotFound_Notice") ?? "Not found",
                    LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            TitleText = a.Title ?? string.Empty;
            CreatedAtText = (LocalizationService.Get("Notice_Created_Prefix") ?? "Vytvořeno: ") + a.CreatedAt.ToString("dd.MM.yyyy HH:mm");

            if (a.UpdatedAt.HasValue)
            {
                UpdatedAtText = (LocalizationService.Get("Notice_Updated_Prefix") ?? "Aktualizováno: ") + a.UpdatedAt.Value.ToString("dd.MM.yyyy HH:mm");
                UpdatedAtVisible = true;
            }
            else
            {
                UpdatedAtVisible = false;
                UpdatedAtText = string.Empty;
            }

            if (a.Author != null)
            {
                AuthorText = (LocalizationService.Get("Notice_Author_Prefix") ?? "Autor: ") + (a.Author.FirstName ?? string.Empty) + " " + (a.Author.LastName ?? string.Empty);
                AuthorVisible = true;
            }
            else
            {
                AuthorVisible = false;
                AuthorText = string.Empty;
            }

            _lastBodyHtml = a.Body;
            var views = HtmlHelpers.ToViews(a.Body);
            BodyViews.Clear();
            foreach (var v in views)
            {
                BodyViews.Add(v);
            }

            BodyVisible = BodyViews.Count > 0;
        }
        catch (Exception ex)
        {
            await _notifier.ShowAsync(LocalizationService.Get("Error_Loading_Title") ?? "Error", ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
        finally
        {
            IsRefreshing = false;
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
}
