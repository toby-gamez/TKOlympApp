namespace TkOlympApp.ViewModels;

public sealed record NoticeboardAnnouncementItem
{
    public long Id { get; init; }
    public string Title { get; init; } = string.Empty;
    public string PlainBody { get; init; } = string.Empty;
    public string CreatedAtText { get; init; } = string.Empty;
    public bool IsSticky { get; init; }
    public bool IsVisible { get; init; } = true;
}
