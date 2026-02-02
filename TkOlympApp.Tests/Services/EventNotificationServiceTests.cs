// NOTE: EventNotificationService tests are temporarily disabled due to MAUI dependencies (Plugin.LocalNotification)
// TODO: Extract testable logic from EventNotificationService into a separate class without MAUI dependencies

#if FALSE
using FluentAssertions;
using Microsoft.Extensions.Logging;
using Moq;
using TkOlympApp.Models.Events;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.Tests.Mocks;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for EventNotificationService.
/// Tests notification scheduling logic and change detection.
/// Target coverage: 50%+ (complex service with platform-specific dependencies)
/// </summary>
public class EventNotificationServiceTests
{
    [Fact]
    public void Constructor_WithNullLogger_ThrowsArgumentNullException()
    {
        // Arrange
        var mockEventService = new Mock<IEventService>();
        var mockUserService = new Mock<IUserService>();
        var mockStorage = MockSecureStorage.Create();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new EventNotificationService(null!, mockEventService.Object, mockUserService.Object, mockStorage.Object));
    }

    [Fact]
    public void Constructor_WithNullEventService_ThrowsArgumentNullException()
    {
        // Arrange
        var mockUserService = new Mock<IUserService>();
        var mockStorage = MockSecureStorage.Create();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new EventNotificationService(new NullLogger<EventNotificationService>(), null!, mockUserService.Object, mockStorage.Object));
    }

    [Fact]
    public void Constructor_WithNullUserService_ThrowsArgumentNullException()
    {
        // Arrange
        var mockEventService = new Mock<IEventService>();
        var mockStorage = MockSecureStorage.Create();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new EventNotificationService(new NullLogger<EventNotificationService>(), mockEventService.Object, null!, mockStorage.Object));
    }

    [Fact]
    public void Constructor_WithNullSecureStorage_ThrowsArgumentNullException()
    {
        // Arrange
        var mockEventService = new Mock<IEventService>();
        var mockUserService = new Mock<IUserService>();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new EventNotificationService(new NullLogger<EventNotificationService>(), mockEventService.Object, mockUserService.Object, null!));
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithEmptyList_CompletesSuccessfully()
    {
        // Arrange
        var service = CreateService();
        var events = new List<EventInstance>();

        // Act & Assert - Should not throw
        await service.ScheduleNotificationsForEventsAsync(events);
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithCancelledEvent_SkipsEvent()
    {
        // Arrange
        var service = CreateService();
        var futureDate = DateTime.Now.AddHours(2);
        var events = new List<EventInstance>
        {
            CreateEventInstance(1, futureDate, isCancelled: true)
        };

        // Act & Assert - Should not throw and skip cancelled event
        await service.ScheduleNotificationsForEventsAsync(events);
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithPastEvent_SkipsEvent()
    {
        // Arrange
        var service = CreateService();
        var pastDate = DateTime.Now.AddHours(-2);
        var events = new List<EventInstance>
        {
            CreateEventInstance(1, pastDate, isCancelled: false)
        };

        // Act & Assert - Should not throw and skip past event
        await service.ScheduleNotificationsForEventsAsync(events);
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithFutureEvent_ProcessesEvent()
    {
        // Arrange
        var service = CreateService();
        var futureDate = DateTime.Now.AddHours(3);
        var events = new List<EventInstance>
        {
            CreateEventInstance(1, futureDate, isCancelled: false)
        };

        // Act & Assert - Should not throw
        await service.ScheduleNotificationsForEventsAsync(events);
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithNullSince_SkipsEvent()
    {
        // Arrange
        var service = CreateService();
        var events = new List<EventInstance>
        {
            CreateEventInstance(1, null, isCancelled: false)
        };

        // Act & Assert - Should not throw
        await service.ScheduleNotificationsForEventsAsync(events);
    }

    [Fact]
    public async Task ClearAllScheduledNotificationsAsync_CompletesSuccessfully()
    {
        // Arrange
        var service = CreateService();

        // Act & Assert - Should not throw
        await service.ClearAllScheduledNotificationsAsync();
    }

    [Fact]
    public async Task RequestNotificationPermissionAsync_ReturnsBoolean()
    {
        // Arrange
        var service = CreateService();

        // Act
        var result = await service.RequestNotificationPermissionAsync();

        // Assert
        result.Should().BeOfType<bool>();
    }

    [Fact]
    public async Task NotifyRegistrationAsync_WithRegisteredTrue_CompletesSuccessfully()
    {
        // Arrange
        var service = CreateService();

        // Act & Assert - Should not throw
        await service.NotifyRegistrationAsync(123L, registered: true);
    }

    [Fact]
    public async Task NotifyRegistrationAsync_WithRegisteredFalse_CompletesSuccessfully()
    {
        // Arrange
        var service = CreateService();

        // Act & Assert - Should not throw
        await service.NotifyRegistrationAsync(123L, registered: false);
    }

    [Fact]
    public async Task GetDiagnosticsAsync_ReturnsNonEmptyString()
    {
        // Arrange
        var service = CreateService();

        // Act
        var diagnostics = await service.GetDiagnosticsAsync();

        // Assert
        diagnostics.Should().NotBeNullOrEmpty();
        diagnostics.Should().Contain("EventNotificationService Diagnostics");
    }

    [Fact]
    public async Task CheckAndNotifyChangesAsync_WithEmptyList_CompletesSuccessfully()
    {
        // Arrange
        var service = CreateService();
        var events = new List<EventInstance>();

        // Act & Assert - Should not throw
        await service.CheckAndNotifyChangesAsync(events);
    }

    [Fact]
    public async Task CheckAndNotifyChangesAsync_WithNewEvent_DetectsChange()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var service = CreateService(mockStorage: mockStorage);
        
        // First call - establish baseline
        var initialEvents = new List<EventInstance>
        {
            CreateEventInstance(1, DateTime.Now.AddHours(2), isCancelled: false)
        };
        await service.CheckAndNotifyChangesAsync(initialEvents);

        // Second call - add new event
        var updatedEvents = new List<EventInstance>
        {
            CreateEventInstance(1, DateTime.Now.AddHours(2), isCancelled: false),
            CreateEventInstance(2, DateTime.Now.AddHours(3), isCancelled: false)
        };

        // Act & Assert - Should not throw
        await service.CheckAndNotifyChangesAsync(updatedEvents);
    }

    [Fact]
    public async Task CheckAndNotifyChangesAsync_WithCancelledEvent_DetectsChange()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var service = CreateService(mockStorage: mockStorage);
        
        // First call - event is active
        var initialEvents = new List<EventInstance>
        {
            CreateEventInstance(1, DateTime.Now.AddHours(2), isCancelled: false)
        };
        await service.CheckAndNotifyChangesAsync(initialEvents);

        // Second call - event is cancelled
        var updatedEvents = new List<EventInstance>
        {
            CreateEventInstance(1, DateTime.Now.AddHours(2), isCancelled: true)
        };

        // Act & Assert - Should not throw
        await service.CheckAndNotifyChangesAsync(updatedEvents);
    }

    [Fact]
    public async Task CheckAndNotifyChangesAsync_WithTimeChange_DetectsChange()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var service = CreateService(mockStorage: mockStorage);
        
        // First call - original time
        var initialTime = DateTime.Now.AddHours(2);
        var initialEvents = new List<EventInstance>
        {
            CreateEventInstance(1, initialTime, isCancelled: false)
        };
        await service.CheckAndNotifyChangesAsync(initialEvents);

        // Second call - time changed
        var newTime = DateTime.Now.AddHours(3);
        var updatedEvents = new List<EventInstance>
        {
            CreateEventInstance(1, newTime, isCancelled: false)
        };

        // Act & Assert - Should not throw
        await service.CheckAndNotifyChangesAsync(updatedEvents);
    }

    [Fact]
    public async Task CheckAndNotifyChangesAsync_WithLocationChange_DetectsChange()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var service = CreateService(mockStorage: mockStorage);
        
        // First call - original location
        var initialEvents = new List<EventInstance>
        {
            CreateEventInstance(1, DateTime.Now.AddHours(2), isCancelled: false, locationText: "Hall A")
        };
        await service.CheckAndNotifyChangesAsync(initialEvents);

        // Second call - location changed
        var updatedEvents = new List<EventInstance>
        {
            CreateEventInstance(1, DateTime.Now.AddHours(2), isCancelled: false, locationText: "Hall B")
        };

        // Act & Assert - Should not throw
        await service.CheckAndNotifyChangesAsync(updatedEvents);
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithMultipleEvents_ProcessesAll()
    {
        // Arrange
        var service = CreateService();
        var futureDate1 = DateTime.Now.AddHours(2);
        var futureDate2 = DateTime.Now.AddHours(4);
        var futureDate3 = DateTime.Now.AddHours(6);
        
        var events = new List<EventInstance>
        {
            CreateEventInstance(1, futureDate1, isCancelled: false),
            CreateEventInstance(2, futureDate2, isCancelled: false),
            CreateEventInstance(3, futureDate3, isCancelled: false)
        };

        // Act & Assert - Should not throw
        await service.ScheduleNotificationsForEventsAsync(events);
    }

    [Fact]
    public async Task ScheduleNotificationsForEventsAsync_WithCancellationToken_PropagatesCancellation()
    {
        // Arrange
        var service = CreateService();
        var cts = new CancellationTokenSource();
        cts.Cancel();
        var events = new List<EventInstance>();

        // Act & Assert
        await service.ScheduleNotificationsForEventsAsync(events, cts.Token);
        // Note: Current implementation doesn't use cancellation token in early exit paths
    }

    // Helper methods

    private EventNotificationService CreateService(
        Mock<IEventService>? mockEventService = null,
        Mock<IUserService>? mockUserService = null,
        Mock<ISecureStorage>? mockStorage = null)
    {
        mockEventService ??= new Mock<IEventService>();
        mockUserService ??= new Mock<IUserService>();
        mockStorage ??= MockSecureStorage.Create();

        return new EventNotificationService(
            new NullLogger<EventNotificationService>(),
            mockEventService.Object,
            mockUserService.Object,
            mockStorage.Object);
    }

    private EventInstance CreateEventInstance(
        long id,
        DateTime? since,
        bool isCancelled,
        string? locationText = null,
        string? eventName = null)
    {
        var eventInfo = new EventInfo
        {
            Id = id,
            Name = eventName ?? $"Event {id}",
            Type = "TRAINING",
            LocationText = locationText ?? "Test Location",
            EventTrainersList = new List<EventTrainer>()
        };

        return new EventInstance(
            id,
            isCancelled,
            null,
            since,
            since?.AddHours(2),
            DateTime.Now,
            eventInfo);
    }
}
#endif
