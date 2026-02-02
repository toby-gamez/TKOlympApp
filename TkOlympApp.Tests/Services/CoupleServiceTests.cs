using FluentAssertions;
using Moq;
using TkOlympApp.Models.Couples;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for CoupleServiceImplementation.
/// Tests couple queries and null handling.
/// Target coverage: 80%+
/// </summary>
public class CoupleServiceTests
{
    [Fact]
    public void Constructor_WithNullGraphQlClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new CoupleServiceImplementation(null!));
    }

    [Fact]
    public async Task GetCoupleAsync_WithValidId_ReturnsCoupleRecord()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            couple = new
            {
                id = "couple-123",
                createdAt = "2026-01-01T00:00:00Z",
                man = new
                {
                    id = "man-1",
                    firstName = "John",
                    lastName = "Doe",
                    phone = "+420123456789"
                },
                woman = new
                {
                    id = "woman-1",
                    firstName = "Jane",
                    lastName = "Smith",
                    phone = "+420987654321"
                }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.Is<Dictionary<string, object>>(v => v["id"].Equals("couple-123")),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CoupleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCoupleAsync("couple-123");

        // Assert
        result.Should().NotBeNull();
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.IsAny<string>(),
            It.Is<Dictionary<string, object>>(v => v["id"].Equals("couple-123")),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetCoupleAsync_WithInvalidId_ReturnsNull()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            couple = (object?)null
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CoupleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCoupleAsync("invalid-id");

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public async Task GetCoupleAsync_PassesCorrectVariables()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        var expectedResponse = new
        {
            couple = new
            {
                id = "couple-456",
                createdAt = "2026-01-01T00:00:00Z",
                man = new { id = "man-1", firstName = "Test", lastName = "Man", phone = "" },
                woman = new { id = "woman-1", firstName = "Test", lastName = "Woman", phone = "" }
            }
        };

        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync(expectedResponse);

        var service = new CoupleServiceImplementation(mockClient.Object);

        // Act
        await service.GetCoupleAsync("couple-456");

        // Assert
        mockClient.Verify(c => c.PostAsync<dynamic>(
            It.Is<string>(q => q.Contains("couple(id: $id)")),
            It.Is<Dictionary<string, object>>(v =>
                v.ContainsKey("id") &&
                v["id"].Equals("couple-456")),
            It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task GetCoupleAsync_WithCancellationToken_PropagatesCancellation()
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

        var service = new CoupleServiceImplementation(mockClient.Object);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            service.GetCoupleAsync("couple-123", cts.Token));
    }

    [Fact]
    public async Task GetCoupleAsync_WithNullResponse_ReturnsNull()
    {
        // Arrange
        var mockClient = new Mock<IGraphQlClient>();
        mockClient.Setup(c => c.PostAsync<dynamic>(
                It.IsAny<string>(),
                It.IsAny<Dictionary<string, object>>(),
                It.IsAny<CancellationToken>()))
            .ReturnsAsync((object?)null);

        var service = new CoupleServiceImplementation(mockClient.Object);

        // Act
        var result = await service.GetCoupleAsync("couple-123");

        // Assert
        result.Should().BeNull();
    }
}
