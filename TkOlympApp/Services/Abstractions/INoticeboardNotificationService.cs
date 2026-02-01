using TkOlympApp.Models.Noticeboard;

namespace TkOlympApp.Services.Abstractions;

public interface INoticeboardNotificationService
{
    Task CheckAndNotifyChangesAsync(List<Announcement> currentAnnouncements, CancellationToken ct = default);
}
