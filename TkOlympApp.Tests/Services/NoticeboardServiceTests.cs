using FluentAssertions;
using Moq;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for NoticeboardServiceImplementation.
/// Tests announcement queries and DTO mapping.
/// Target coverage: 80%+
/// </summary>
public class NoticeboardServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new NoticeboardServiceImplementation(null!));
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_ReturnsAnnouncementsList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            myAnnouncements = new
            {
                nodes = new[]
                {
                    new
                    {
                        id = 1L,
                        title = "Test Announcement",
                        body = "This is a test announcement",
                        isSticky = false,
                        isVisible = true,
                        createdAt = "2026-02-01T00:00:00Z",
                        updatedAt = "2026-02-01T00:00:00Z",
                        author = new
                        {
                            id = "user-1",
                            uJmeno = "John",
                            uPrijmeni = "Doe"
                        }
                    }
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetMyAnnouncementsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_WithStickyFilter_PassesCorrectVariable()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            myAnnouncements = new
            {
                nodes = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        await service.GetMyAnnouncementsAsync(sticky: true);

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("sticky") &&
                v["sticky"].Equals(true)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_WithoutStickyFilter_PassesNullVariables()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            myAnnouncements = new
            {
                nodes = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        await service.GetMyAnnouncementsAsync();

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            null,
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_WithNullNodes_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            myAnnouncements = new
            {
                nodes = (object[]?)null
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetMyAnnouncementsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_WithNullResponse_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            myAnnouncements = (object?)null
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetMyAnnouncementsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetAnnouncementAsync_WithValidId_ReturnsAnnouncementDetails()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            announcement = new
            {
                id = 123L,
                title = "Important Update",
                body = "This is an important update",
                createdAt = "2026-02-01T00:00:00Z",
                updatedAt = "2026-02-01T12:00:00Z",
                isVisible = true,
                author = new
                {
                    uJmeno = "Admin",
                    uPrijmeni = "User"
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.Is<Dictionary<string, object>>(v => v["id"].Equals(123L)),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetAnnouncementAsync(123L);

        // Assert
        result.Should().NotBeNull();
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.Is<string>(q => q.Contains("announcement(id: $id)")),
            It.Is<Dictionary<string, object>>(v => v["id"].Equals(123L)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetAnnouncementAsync_WithInvalidId_ReturnsNull()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            announcement = (object?)null
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetAnnouncementAsync(999L);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public async Task GetStickyAnnouncementsAsync_CallsGetMyAnnouncementsWithStickyTrue()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            myAnnouncements = new
            {
                nodes = new[]
                {
                    new
                    {
                        id = 1L,
                        title = "Sticky Announcement",
                        body = "Important sticky message",
                        isSticky = true,
                        isVisible = true,
                        createdAt = "2026-02-01T00:00:00Z",
                        updatedAt = "2026-02-01T00:00:00Z",
                        author = new { id = "user-1", uJmeno = "Admin", uPrijmeni = "User" }
                    }
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetStickyAnnouncementsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("sticky") &&
                v["sticky"].Equals(true)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_WithCancellationToken_PropagatesCancellation()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var cts = new CancellationTokenSource();
        cts.Cancel();

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ThrowsAsync(new OperationCanceledException());

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            service.GetMyAnnouncementsAsync(ct: cts.Token));
    }

    [Fact]
    public async Task GetAnnouncementAsync_WithCancellationToken_PropagatesCancellation()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var cts = new CancellationTokenSource();
        cts.Cancel();

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ThrowsAsync(new OperationCanceledException());

        var service = new NoticeboardServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            service.GetAnnouncementAsync(123L, cts.Token));
    }
}
