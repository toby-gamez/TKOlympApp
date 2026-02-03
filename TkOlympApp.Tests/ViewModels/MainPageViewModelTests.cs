using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using FluentAssertions;
using Moq;
using TkOlympApp.Models.Events;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.ViewModels;
using Xunit;

namespace TkOlympApp.Tests.ViewModels;

public sealed class MainPageViewModelTests
{
    [Fact]
    public async Task OnDisappearingAsync_cancelsInitializationToken()
    {
        var eventService = new Mock<IEventService>();
        var noticeboardService = new Mock<INoticeboardService>();
        var eventNotificationService = new Mock<IEventNotificationService>();
        var notifier = new Mock<IUserNotifier>();

        CancellationToken capturedToken = default;

        eventService
            .Setup(s => s.GetMyEventInstancesForRangeAsync(
                It.IsAny<DateTime>(),
                It.IsAny<DateTime>(),
                It.IsAny<int?>(),
                It.IsAny<int?>(),
                It.IsAny<string?>(),
                It.IsAny<CancellationToken>()))
            .Returns(async (DateTime start, DateTime end, int? first, int? offset, string? onlyType, CancellationToken ct) =>
            {
                capturedToken = ct;
                await Task.Delay(Timeout.Infinite, ct);
                return new List<EventInstance>();
            });

        noticeboardService
            .Setup(s => s.GetMyAnnouncementsAsync(It.IsAny<bool?>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(new List<Announcement>());

        eventNotificationService
            .Setup(s => s.CheckAndNotifyChangesAsync(It.IsAny<List<EventInstance>>(), It.IsAny<CancellationToken>()))
            .Returns(Task.CompletedTask);
        eventNotificationService
            .Setup(s => s.ScheduleNotificationsForEventsAsync(It.IsAny<List<EventInstance>>(), It.IsAny<CancellationToken>()))
            .Returns(Task.CompletedTask);

        var vm = new MainPageViewModel(
            eventService.Object,
            noticeboardService.Object,
            eventNotificationService.Object,
            notifier.Object);

        var appearingTask = vm.OnAppearingAsync();
        await Task.Delay(50);
        await vm.OnDisappearingAsync();

        capturedToken.IsCancellationRequested.Should().BeTrue();
        await appearingTask;
    }
}
