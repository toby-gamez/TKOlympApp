using static TkOlympApp.Services.NoticeboardService;

namespace TkOlympApp.Services.Abstractions;

public interface INoticeboardService
{
    Task<List<Announcement>> GetMyAnnouncementsAsync(bool? sticky = null, CancellationToken ct = default);
    Task<AnnouncementDetails?> GetAnnouncementAsync(long id, CancellationToken ct = default);
    Task<List<Announcement>> GetStickyAnnouncementsAsync(CancellationToken ct = default);
}
