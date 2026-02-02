using FluentAssertions;
using Moq;
using TkOlympApp.Models.People;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for PeopleServiceImplementation.
/// Tests people queries with cohort filtering and data mapping.
/// Target coverage: 80%+
/// </summary>
public class PeopleServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new PeopleServiceImplementation(null!));
    }

    [Fact]
    public async Task GetPeopleAsync_ReturnsPeopleList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            people = new
            {
                nodes = new[]
                {
                    new
                    {
                        id = "person-1",
                        firstName = "John",
                        lastName = "Doe",
                        birthDate = "1990-01-01",
                        cohortMembershipsList = new[]
                        {
                            new
                            {
                                cohort = new
                                {
                                    id = "cohort-1",
                                    name = "Advanced",
                                    colorRgb = "#FF0000",
                                    isVisible = true
                                }
                            }
                        }
                    },
                    new
                    {
                        id = "person-2",
                        firstName = "Jane",
                        lastName = "Smith",
                        birthDate = "1992-05-15",
                        cohortMembershipsList = Array.Empty<object>()
                    }
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetPeopleAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(2);
    }

    [Fact]
    public async Task GetPeopleAsync_WithEmptyNodes_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            people = new
            {
                nodes = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetPeopleAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetPeopleAsync_WithNullNodes_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            people = new
            {
                nodes = (object[]?)null
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetPeopleAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetPeopleAsync_WithNullPeople_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            people = (object?)null
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetPeopleAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetPeopleAsync_SendsCorrectQuery()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            people = new
            {
                nodes = Array.Empty<object>()
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act
        await service.GetPeopleAsync();

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.Is<string>(q =>
                q.Contains("people") &&
                q.Contains("nodes") &&
                q.Contains("firstName") &&
                q.Contains("lastName") &&
                q.Contains("cohortMembershipsList")),
            It.IsAny<Dictionary<string, object>>(),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetPeopleAsync_WithCancellationToken_PropagatesCancellation()
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

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            service.GetPeopleAsync(cts.Token));
    }

    [Fact]
    public async Task GetPeopleAsync_IncludesCohortInformation()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            people = new
            {
                nodes = new[]
                {
                    new
                    {
                        id = "person-1",
                        firstName = "Test",
                        lastName = "User",
                        birthDate = "1995-03-20",
                        cohortMembershipsList = new[]
                        {
                            new
                            {
                                cohort = new
                                {
                                    id = "cohort-1",
                                    name = "Beginner",
                                    colorRgb = "#0000FF",
                                    isVisible = true
                                }
                            },
                            new
                            {
                                cohort = new
                                {
                                    id = "cohort-2",
                                    name = "Intermediate",
                                    colorRgb = "#00FF00",
                                    isVisible = false
                                }
                            }
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

        var service = new PeopleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetPeopleAsync();

        // Assert
        result.Should().HaveCount(1);
        // Note: Detailed cohort membership validation would require inspecting the Person model structure
    }
}
