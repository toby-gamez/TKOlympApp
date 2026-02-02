using FluentAssertions;
using Moq;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.Tests.Mocks;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for UserServiceImplementation.
/// Tests user state management, person ID persistence, and couple/cohort queries.
/// Target coverage: 80%+
/// </summary>
public class UserServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new UserServiceImplementation(null!, mockStorage.Object));
    }

    [Fact]
    public void Constructor_WithNullSecureStorage_ThrowsArgumentNullException()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new UserServiceImplementation(mockClient.Object, null!));
    }

    [Fact]
    public async Task InitializeAsync_WithStoredPersonId_LoadsPersonId()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["currentPersonId"] = "person-123"
        });
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.InitializeAsync();

        // Assert
        service.CurrentPersonId.Should().Be("person-123");
    }

    [Fact]
    public async Task InitializeAsync_WithStoredCohortId_LoadsCohortId()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["currentCohortId"] = "cohort-456"
        });
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.InitializeAsync();

        // Assert
        service.CurrentCohortId.Should().Be("cohort-456");
    }

    [Fact]
    public async Task InitializeAsync_WithNoStoredValues_LeavesIdsNull()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.InitializeAsync();

        // Assert
        service.CurrentPersonId.Should().BeNull();
        service.CurrentCohortId.Should().BeNull();
    }

    [Fact]
    public async Task SetCurrentPersonIdAsync_PersistsToStorage()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.SetCurrentPersonIdAsync("person-789");

        // Assert
        service.CurrentPersonId.Should().Be("person-789");
        var storedValue = await mockStorage.Object.GetAsync("currentPersonId");
        storedValue.Should().Be("person-789");
    }

    [Fact]
    public async Task SetCurrentPersonIdAsync_WithNull_ClearsStorage()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["currentPersonId"] = "person-123"
        });
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.SetCurrentPersonIdAsync(null);

        // Assert
        service.CurrentPersonId.Should().BeNull();
        var storedValue = await mockStorage.Object.GetAsync("currentPersonId");
        storedValue.Should().BeEmpty();
    }

    [Fact]
    public void SetCurrentPersonId_SetsPropertyAndPersistsAsync()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        service.SetCurrentPersonId("person-abc");

        // Assert
        service.CurrentPersonId.Should().Be("person-abc");
        // Note: Async persistence is fire-and-forget, so we can't directly test storage here
    }

    [Fact]
    public async Task SetCurrentCohortIdAsync_PersistsToStorage()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.SetCurrentCohortIdAsync("cohort-999");

        // Assert
        service.CurrentCohortId.Should().Be("cohort-999");
        var storedValue = await mockStorage.Object.GetAsync("currentCohortId");
        storedValue.Should().Be("cohort-999");
    }

    [Fact]
    public async Task SetCurrentCohortIdAsync_WithNull_ClearsStorage()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["currentCohortId"] = "cohort-456"
        });
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        await service.SetCurrentCohortIdAsync(null);

        // Assert
        service.CurrentCohortId.Should().BeNull();
        var storedValue = await mockStorage.Object.GetAsync("currentCohortId");
        storedValue.Should().BeEmpty();
    }

    [Fact]
    public void SetCurrentCohortId_SetsPropertyAndPersistsAsync()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        service.SetCurrentCohortId("cohort-xyz");

        // Assert
        service.CurrentCohortId.Should().Be("cohort-xyz");
    }

    [Fact]
    public async Task GetCurrentUserAsync_ReturnsCurrentUser()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            getCurrentUser = new
            {
                id = "user-123",
                uLogin = "testuser",
                uEmail = "test@example.com",
                uJmeno = "Test",
                uPrijmeni = "User",
                tenantId = 1L,
                createdAt = "2026-01-01T00:00:00Z",
                lastLogin = "2026-02-01T00:00:00Z",
                lastActiveAt = "2026-02-02T00:00:00Z",
                updatedAt = "2026-02-02T00:00:00Z"
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var mockStorage = MockSecureStorage.Create();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        var result = await service.GetCurrentUserAsync();

        // Assert
        result.Should().NotBeNull();
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.Is<string>(q => q.Contains("getCurrentUser")),
            It.IsAny<Dictionary<string, object>>(),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetActiveCouplesFromUsersAsync_ReturnsCouplelist()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            users = new
            {
                nodes = new[]
                {
                    new
                    {
                        userProxiesList = new[]
                        {
                            new
                            {
                                person = new
                                {
                                    activeCouplesList = new[]
                                    {
                                        new
                                        {
                                            id = "couple-1",
                                            man = new { firstName = "John", lastName = "Doe" },
                                            woman = new { firstName = "Jane", lastName = "Smith" }
                                        }
                                    },
                                    cohortMembershipsList = Array.Empty<object>()
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

        var mockStorage = MockSecureStorage.Create();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        var result = await service.GetActiveCouplesFromUsersAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        result[0].CoupleId.Should().Be("couple-1");
        result[0].ManName.Should().Be("John Doe");
        result[0].WomanName.Should().Be("Jane Smith");
    }

    [Fact]
    public async Task GetActiveCouplesFromUsersAsync_WithEmptyNames_SkipsCouple()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            users = new
            {
                nodes = new[]
                {
                    new
                    {
                        userProxiesList = new[]
                        {
                            new
                            {
                                person = new
                                {
                                    activeCouplesList = new[]
                                    {
                                        new
                                        {
                                            id = "couple-1",
                                            man = new { firstName = "", lastName = "" },
                                            woman = new { firstName = "", lastName = "" }
                                        }
                                    },
                                    cohortMembershipsList = Array.Empty<object>()
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

        var mockStorage = MockSecureStorage.Create();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        var result = await service.GetActiveCouplesFromUsersAsync();

        // Assert
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetActiveCouplesFromUsersAsync_WithNullNodes_ReturnsEmptyList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            users = new
            {
                nodes = (object[]?)null
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var mockStorage = MockSecureStorage.Create();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        var result = await service.GetActiveCouplesFromUsersAsync();

        // Assert
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetCohortsFromUsersAsync_ReturnsCohortList()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            users = new
            {
                nodes = new[]
                {
                    new
                    {
                        userProxiesList = new[]
                        {
                            new
                            {
                                person = new
                                {
                                    cohortMembershipsList = new[]
                                    {
                                        new
                                        {
                                            cohort = new
                                            {
                                                id = "cohort-1",
                                                name = "Advanced",
                                                colorRgb = "#FF0000"
                                            }
                                        }
                                    }
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

        var mockStorage = MockSecureStorage.Create();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        var result = await service.GetCohortsFromUsersAsync();

        // Assert
        result.Should().NotBeNull();
        result.Should().HaveCount(1);
        result[0].CohortId.Should().Be("cohort-1");
        result[0].CohortName.Should().Be("Advanced");
        result[0].CohortColorRgb.Should().Be("#FF0000");
    }

    [Fact]
    public async Task GetCohortsFromUsersAsync_WithMultipleCohorts_ReturnsAllCohorts()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            users = new
            {
                nodes = new[]
                {
                    new
                    {
                        userProxiesList = new[]
                        {
                            new
                            {
                                person = new
                                {
                                    cohortMembershipsList = new[]
                                    {
                                        new
                                        {
                                            cohort = new
                                            {
                                                id = "cohort-1",
                                                name = "Beginner",
                                                colorRgb = "#00FF00"
                                            }
                                        },
                                        new
                                        {
                                            cohort = new
                                            {
                                                id = "cohort-2",
                                                name = "Advanced",
                                                colorRgb = "#0000FF"
                                            }
                                        }
                                    }
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

        var mockStorage = MockSecureStorage.Create();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act
        var result = await service.GetCohortsFromUsersAsync();

        // Assert
        result.Should().HaveCount(2);
        result[0].CohortName.Should().Be("Beginner");
        result[1].CohortName.Should().Be("Advanced");
    }

    [Fact]
    public async Task InitializeAsync_WithStorageException_IgnoresError()
    {
        // Arrange
        var mockStorage = new Mock<ISecureStorage>();
        mockStorage.Setup(s => s.GetAsync(It.IsAny<string>()))
            .ThrowsAsync(new InvalidOperationException("Storage error"));
        
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act & Assert - Should not throw
        await service.InitializeAsync();
        service.CurrentPersonId.Should().BeNull();
        service.CurrentCohortId.Should().BeNull();
    }

    [Fact]
    public async Task SetCurrentPersonIdAsync_WithStorageException_IgnoresError()
    {
        // Arrange
        var mockStorage = new Mock<ISecureStorage>();
        mockStorage.Setup(s => s.SetAsync(It.IsAny<string>(), It.IsAny<string>()))
            .ThrowsAsync(new InvalidOperationException("Storage error"));
        
        var mockClient = new Mock<IGraphQlClient>();
        var service = new UserServiceImplementation(mockClient.Object, mockStorage.Object);

        // Act & Assert - Should not throw
        await service.SetCurrentPersonIdAsync("person-123");
        service.CurrentPersonId.Should().Be("person-123");
    }
}
