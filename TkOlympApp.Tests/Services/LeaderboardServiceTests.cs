using System.Numerics;
using FluentAssertions;
using Moq;
using TkOlympApp.Models.Leaderboards;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for LeaderboardServiceImplementation.
/// Tests leaderboard queries with date and cohort filtering.
/// Target coverage: 80%+
/// </summary>
public class LeaderboardServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new LeaderboardServiceImplementation(null!));
    }

    [Fact]
    public async Task GetScoreboardsAsync_WithoutFilters_ReturnsScoreboards()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":{""scoreboardEntriesList"":[]}}";
        var expectedResponse = new
        {
            scoreboardEntriesList = new[]
            {
                new
                {
                    personId = "person-1",
                    cohortId = "cohort-1",
                    totalScore = 100.5,
                    lessonTotalScore = 50.0,
                    groupTotalScore = 30.0,
                    eventTotalScore = 15.5,
                    manualTotalScore = 5.0,
                    ranking = 1,
                    person = new
                    {
                        id = "person-1",
                        firstName = "John",
                        lastName = "Doe"
                    },
                    cohort = new
                    {
                        id = "cohort-1",
                        name = "Advanced"
                    }
                }
            },
            getCurrentTenant = new
            {
                id = "1",
                cohortsList = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetScoreboardsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
    }

    [Fact]
    public async Task GetScoreboardsAsync_WithCohortFilter_PassesCohortId()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":{""scoreboardEntriesList"":[]}}";
        var expectedResponse = new
        {
            scoreboardEntriesList = Array.Empty<object>(),
            getCurrentTenant = new { id = "1", cohortsList = Array.Empty<object>() }
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);
        var cohortId = new BigInteger(123);

        // Act
        await service.GetScoreboardsAsync(cohortId: cohortId);

        // Assert
        mockClient.Verify(c => c.PostWithRawAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("cohortId") && v["cohortId"].Equals(cohortId)),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetScoreboardsAsync_WithDateRange_PassesDateFilters()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":{""scoreboardEntriesList"":[]}}";
        var expectedResponse = new
        {
            scoreboardEntriesList = Array.Empty<object>(),
            getCurrentTenant = new { id = "1", cohortsList = Array.Empty<object>() }
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);
        var since = new DateTime(2026, 1, 1);
        var until = new DateTime(2026, 2, 1);

        // Act
        await service.GetScoreboardsAsync(since: since, until: until);

        // Assert
        mockClient.Verify(c => c.PostWithRawAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("since") &&
                v.ContainsKey("until") &&
                v["since"].ToString() == "2026-01-01" &&
                v["until"].ToString() == "2026-02-01"),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetScoreboardsAsync_WithNullResponse_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":null}";
        var expectedResponse = new
        {
            scoreboardEntriesList = (object[]?)null,
            getCurrentTenant = (object?)null
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetScoreboardsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetScoreboardsWithRawAsync_ReturnsDataAndRawResponse()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":{""scoreboardEntriesList"":[{""totalScore"":100}]}}";
        var expectedResponse = new
        {
            scoreboardEntriesList = new[]
            {
                new
                {
                    personId = "person-1",
                    totalScore = 100.0
                }
            },
            getCurrentTenant = new { id = "1", cohortsList = Array.Empty<object>() }
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);

        // Act
        var (scoreboards, raw) = await service.GetScoreboardsWithRawAsync();

        // Assert
        scoreboards.Should().NotBeNull();
        scoreboards.Should().HaveCount(1);
        raw.Should().Be(rawResponse);
    }

    [Fact]
    public async Task GetScoreboardsAsync_WithAllParameters_PassesAllFilters()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":{""scoreboardEntriesList"":[]}}";
        var expectedResponse = new
        {
            scoreboardEntriesList = Array.Empty<object>(),
            getCurrentTenant = new { id = "1", cohortsList = Array.Empty<object>() }
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);
        var cohortId = new BigInteger(456);
        var since = new DateTime(2026, 1, 1);
        var until = new DateTime(2026, 2, 1);

        // Act
        await service.GetScoreboardsAsync(cohortId: cohortId, since: since, until: until);

        // Assert
        mockClient.Verify(c => c.PostWithRawAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("cohortId") &&
                v.ContainsKey("since") &&
                v.ContainsKey("until")),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetScoreboardsAsync_WithDefaultDates_UsesExpectedDefaults()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var rawResponse = @"{""data"":{""scoreboardEntriesList"":[]}}";
        var expectedResponse = new
        {
            scoreboardEntriesList = Array.Empty<object>(),
            getCurrentTenant = new { id = "1", cohortsList = Array.Empty<object>() }
        };

        mockClient.Setup(c => c.PostWithRawAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((expectedResponse, rawResponse));

        var service = new LeaderboardServiceImplementation(mockClient.Object);

        // Act
        await service.GetScoreboardsAsync();

        // Assert
        mockClient.Verify(c => c.PostWithRawAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("since") &&
                v.ContainsKey("until") &&
                v["since"].ToString() == "2025-09-01"),
            It.IsAny<CancellationToken>()), Times.Once);
    }
}
