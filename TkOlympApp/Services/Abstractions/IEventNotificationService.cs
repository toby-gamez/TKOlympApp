using TkOlympApp.Models.Events;

namespace TkOlympApp.Services.Abstractions;

public interface IEventNotificationService
{
    Task<bool> RequestNotificationPermissionAsync(CancellationToken ct = default);
    void InitializeBackgroundChangeDetection();

    Task ScheduleNotificationsForEventsAsync(List<EventInstance> events, CancellationToken ct = default);
    Task CheckAndNotifyChangesAsync(List<EventInstance> currentEvents, CancellationToken ct = default);
    Task NotifyRegistrationAsync(long eventId, bool registered, CancellationToken ct = default);

    Task<string> GetDiagnosticsAsync(CancellationToken ct = default);
}
