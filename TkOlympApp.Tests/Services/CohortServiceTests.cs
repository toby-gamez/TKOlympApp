using FluentAssertions;
using Moq;
using TkOlympApp.Models.Cohorts;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for CohortServiceImplementation.
/// Tests cohort group queries and data transformation.
/// Target coverage: 80%+
/// </summary>
public class CohortServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new CohortServiceImplementation(null!));
    }

    [Fact]
    public async Task GetCohortGroupsAsync_ReturnsCohortGroups()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            getCurrentTenant = new
            {
                id = "tenant-1",
                cohortsList = new[]
                {
                    new
                    {
                        name = "Beginner",
                        colorRgb = "#FF0000",
                        description = "Beginner level",
                        location = "Hall A"
                    },
                    new
                    {
                        name = "Advanced",
                        colorRgb = "#00FF00",
                        description = "Advanced level",
                        location = "Hall B"
                    }
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CohortServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCohortGroupsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        result[0].Cohorts.Should().HaveCount(2);
    }

    [Fact]
    public async Task GetCohortGroupsAsync_WithEmptyCohortsList_ReturnsEmptyGroup()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            getCurrentTenant = new
            {
                id = "tenant-1",
                cohortsList = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CohortServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCohortGroupsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        result[0].Cohorts.Should().BeEmpty();
    }

    [Fact]
    public async Task GetCohortGroupsAsync_WithNullResponse_ReturnsEmptyGroup()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            getCurrentTenant = (object?)null
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CohortServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCohortGroupsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        result[0].Cohorts.Should().BeEmpty();
    }

    [Fact]
    public async Task GetCohortGroupsAsync_SendsCorrectQuery()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            getCurrentTenant = new
            {
                id = "tenant-1",
                cohortsList = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CohortServiceImplementation(mockClient.Object);

        // Act
        await service.GetCohortGroupsAsync();

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.Is<string>(q =>
                q.Contains("getCurrentTenant") &&
                q.Contains("cohortsList") &&
                q.Contains("isVisible: true") &&
                q.Contains("NAME_ASC")),
            It.IsAny<Dictionary<string, object>>(),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetCohortGroupsAsync_WithCancellationToken_PropagatesCancellation()
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

        var service = new CohortServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            service.GetCohortGroupsAsync(cts.Token));
    }

    [Fact]
    public async Task GetCohortGroupsAsync_WithNullCohortsList_ReturnsEmptyGroup()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            getCurrentTenant = new
            {
                id = "tenant-1",
                cohortsList = (object[]?)null
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CohortServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCohortGroupsAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        result[0].Cohorts.Should().BeEmpty();
    }
}
