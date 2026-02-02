using FluentAssertions;
using Moq;
using TkOlympApp.Exceptions;
using TkOlympApp.Models.Events;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for EventServiceImplementation.
/// Tests event queries, date range filtering, and DTO mapping.
/// Target coverage: 70%+
/// </summary>
public class EventServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new EventServiceImplementation(null!));
    }

    [Fact]
    public async Task GetEventInstanceAsync_WithValidId_ReturnsEventInstanceDetails()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            eventInstance = new
            {
                id = 123L,
                isCancelled = false,
                since = "2026-02-10T10:00:00Z",
                until = "2026-02-10T12:00:00Z",
                @event = new
                {
                    id = 1L,
                    name = "Test Event",
                    type = "TRAINING",
                    description = "Test description",
                    locationText = "Test Location",
                    capacity = 20,
                    isPublic = true,
                    isRegistrationOpen = true,
                    isVisible = true,
                    summary = "Test summary"
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.Is<Dictionary<string, object>>(v => v["id"].Equals(123L)),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetEventInstanceAsync(123L);

        // Assert
        result.Should().NotBeNull();
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v => v["id"].Equals(123L)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetEventInstanceAsync_WithInvalidId_ReturnsNull()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(new { eventInstance = (object?)null });

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetEventInstanceAsync(999L);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public async Task GetEventAsync_WithValidId_ReturnsEventDetails()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            @event = new
            {
                id = 1L,
                name = "Test Event",
                type = "TRAINING",
                description = "Test description",
                summary = "Test summary",
                capacity = 20,
                locationText = "Test Location"
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.Is<Dictionary<string, object>>(v => v["id"].Equals(1L)),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetEventAsync(1L);

        // Assert
        result.Should().NotBeNull();
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v => v["id"].Equals(1L)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithDateRange_ReturnsEventList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);
        var expectedResponse = new
        {
            eventInstancesForRangeList = new[]
            {
                new
                {
                    id = 1L,
                    isCancelled = false,
                    since = "2026-02-10T10:00:00Z",
                    until = "2026-02-10T12:00:00Z",
                    @event = new
                    {
                        id = 1L,
                        name = "Event 1",
                        type = "TRAINING"
                    }
                },
                new
                {
                    id = 2L,
                    isCancelled = false,
                    since = "2026-02-15T14:00:00Z",
                    until = "2026-02-15T16:00:00Z",
                    @event = new
                    {
                        id = 2L,
                        name = "Event 2",
                        type = "COMPETITION"
                    }
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetMyEventInstancesForRangeAsync(startDate, endDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(2);
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("startRange") &&
                v.ContainsKey("endRange")),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithPagination_PassesCorrectParameters()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);
        var expectedResponse = new { eventInstancesForRangeList = Array.Empty<object>() };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        await service.GetMyEventInstancesForRangeAsync(startDate, endDate, first: 10, offset: 5);

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("first") && v["first"].Equals(10) &&
                v.ContainsKey("offset") && v["offset"].Equals(5)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithEventTypeFilter_PassesCorrectType()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);
        var expectedResponse = new { eventInstancesForRangeList = Array.Empty<object>() };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        await service.GetMyEventInstancesForRangeAsync(startDate, endDate, onlyType: "TRAINING");

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("onlyType") && v["onlyType"].Equals("TRAINING")),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithGraphQLError_ThrowsGraphQLException()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ThrowsAsync(new GraphQLException("GraphQL error", new List<string> { "Field not found" }));

        var service = new EventServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<GraphQLException>(() =>
            service.GetMyEventInstancesForRangeAsync(startDate, endDate));
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithServiceException_ThrowsServiceException()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ThrowsAsync(new ServiceException("Network error", isTransient: true));

        var service = new EventServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<ServiceException>(() =>
            service.GetMyEventInstancesForRangeAsync(startDate, endDate));
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithCancellation_PropagatesCancellation()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);
        var cts = new CancellationTokenSource();
        cts.Cancel();

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ThrowsAsync(new OperationCanceledException());

        var service = new EventServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            service.GetMyEventInstancesForRangeAsync(startDate, endDate, ct: cts.Token));
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_WithUnexpectedException_WrapsInServiceException()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ThrowsAsync(new InvalidOperationException("Unexpected error"));

        var service = new EventServiceImplementation(mockClient.Object);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<ServiceException>(() =>
            service.GetMyEventInstancesForRangeAsync(startDate, endDate));
        exception.InnerException.Should().BeOfType<InvalidOperationException>();
    }

    [Fact]
    public async Task GetEventInstancesForRangeListAsync_ReturnsCorrectDateTimeConversion()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);
        var rawResponse = @"{""data"":{""eventInstancesForRangeList"":[{""id"":1,""isCancelled"":false,""since"":""2026-02-10T10:00:00Z"",""until"":""2026-02-10T12:00:00Z"",""updatedAt"":""2026-02-01T08:00:00Z""}]}}";
        
        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((new
            {
                eventInstancesForRangeList = new[]
                {
                    new
                    {
                        id = 1L,
                        isCancelled = false,
                        since = DateTimeOffset.Parse("2026-02-10T10:00:00Z"),
                        until = DateTimeOffset.Parse("2026-02-10T12:00:00Z"),
                        updatedAt = DateTimeOffset.Parse("2026-02-01T08:00:00Z")
                    }
                }
            }, rawResponse));

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetEventInstancesForRangeListAsync(startDate, endDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        service.LastEventInstancesForRangeRawJson.Should().Be(rawResponse);
    }

    [Fact]
    public async Task GetAllEventInstancesPagedAsync_WithMultipleBatches_RetrievesAllEvents()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);

        var batch1 = Enumerable.Range(1, 15).Select(i => new
        {
            id = (long)i,
            isCancelled = false,
            since = DateTimeOffset.Parse("2026-02-10T10:00:00Z"),
            until = DateTimeOffset.Parse("2026-02-10T12:00:00Z"),
            updatedAt = DateTimeOffset.Parse("2026-02-01T08:00:00Z")
        }).ToArray();

        var batch2 = Enumerable.Range(16, 10).Select(i => new
        {
            id = (long)i,
            isCancelled = false,
            since = DateTimeOffset.Parse("2026-02-15T10:00:00Z"),
            until = DateTimeOffset.Parse("2026-02-15T12:00:00Z"),
            updatedAt = DateTimeOffset.Parse("2026-02-01T08:00:00Z")
        }).ToArray();

        var callCount = 0;
        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(() =>
            {
                callCount++;
                return callCount == 1
                    ? (new { eventInstancesForRangeList = batch1 }, "{}")
                    : (new { eventInstancesForRangeList = batch2 }, "{}");
            });

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetAllEventInstancesPagedAsync(startDate, endDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(25);
        mockClient.Verify(c => c.PostWithRawAsync<dynamic>(
            It.IsAny<string>(),
            It.IsAny<Dictionary<string, object>>(),
            It.IsAny<CancellationToken>()), Times.Exactly(2));
    }

    [Fact]
    public async Task GetAllEventInstancesPagedAsync_WithEmptyResult_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((new { eventInstancesForRangeList = Array.Empty<object>() }, "{}"));

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetAllEventInstancesPagedAsync(startDate, endDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetAllEventInstancesPagedAsync_WithCancellation_StopsPagination()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var startDate = new DateTime(2026, 2, 1);
        var endDate = new DateTime(2026, 2, 28);
        var cts = new CancellationTokenSource();

        var batch1 = Enumerable.Range(1, 15).Select(i => new
        {
            id = (long)i,
            isCancelled = false,
            since = DateTimeOffset.Parse("2026-02-10T10:00:00Z"),
            until = DateTimeOffset.Parse("2026-02-10T12:00:00Z"),
            updatedAt = DateTimeOffset.Parse("2026-02-01T08:00:00Z")
        }).ToArray();

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(() =>
            {
                cts.Cancel();
                return (new { eventInstancesForRangeList = batch1 }, "{}");
            });

        var service = new EventServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetAllEventInstancesPagedAsync(startDate, endDate, ct: cts.Token);

        // Assert
        result.Should().NotBeNull();
        // Should have partial results before cancellation
        mockClient.Verify(c => c.PostWithRawAsync<dynamic>(
            It.IsAny<string>(),
            It.IsAny<Dictionary<string, object>>(),
            It.IsAny<CancellationToken>()), Times.Once);
    }
}
