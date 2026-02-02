using System.Net;
using System.Text;
using FluentAssertions;
using Moq;
using Moq.Protected;
using TkOlympApp.Exceptions;
using TkOlympApp.Services;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for GraphQlClientImplementation.
/// Tests GraphQL query execution, error handling, and JSON deserialization.
/// </summary>
public class GraphQlClientTests
{
    private sealed record TestData(string Name, int Value);

    [Fact]
    public async Task PostAsync_SuccessfulResponse_ReturnsData()
    {
        // Arrange
        var responseJson = """
            {
                "data": {
                    "name": "Test",
                    "value": 42
                }
            }
            """;
        
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.OK,
                Content = new StringContent(responseJson, Encoding.UTF8, "application/json")
            });
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);

        // Act
        var result = await graphQlClient.PostAsync<TestData>("query { test }");

        // Assert
        result.Should().NotBeNull();
        result.Name.Should().Be("Test");
        result.Value.Should().Be(42);
    }

    [Fact]
    public async Task PostAsync_GraphQLError_ThrowsGraphQLException()
    {
        // Arrange
        var responseJson = """
            {
                "data": null,
                "errors": [
                    {"message": "Field not found"},
                    {"message": "Invalid query"}
                ]
            }
            """;
        
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.OK,
                Content = new StringContent(responseJson, Encoding.UTF8, "application/json")
            });
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);

        // Act
        Func<Task> act = async () => await graphQlClient.PostAsync<TestData>("query { test }");

        // Assert
        await act.Should().ThrowAsync<GraphQLException>()
            .WithMessage("Field not found");
    }

    [Fact]
    public async Task PostAsync_HttpError500_ThrowsTransientServiceException()
    {
        // Arrange
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.InternalServerError,
                Content = new StringContent("Internal Server Error")
            });
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);

        // Act
        Func<Task> act = async () => await graphQlClient.PostAsync<TestData>("query { test }");

        // Assert
        var exception = await act.Should().ThrowAsync<ServiceException>();
        exception.Which.IsTransient.Should().BeTrue();
        exception.Which.HttpStatusCode.Should().Be(500);
    }

    [Fact]
    public async Task PostAsync_HttpError404_ThrowsNonTransientServiceException()
    {
        // Arrange
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.NotFound,
                Content = new StringContent("Not Found")
            });
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);

        // Act
        Func<Task> act = async () => await graphQlClient.PostAsync<TestData>("query { test }");

        // Assert
        var exception = await act.Should().ThrowAsync<ServiceException>();
        exception.Which.IsTransient.Should().BeFalse();
        exception.Which.HttpStatusCode.Should().Be(404);
    }

    [Fact]
    public async Task PostAsync_NetworkError_ThrowsTransientServiceException()
    {
        // Arrange
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ThrowsAsync(new HttpRequestException("Network unreachable"));
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);

        // Act
        Func<Task> act = async () => await graphQlClient.PostAsync<TestData>("query { test }");

        // Assert
        var exception = await act.Should().ThrowAsync<ServiceException>();
        exception.Which.IsTransient.Should().BeTrue();
        exception.Which.Message.Should().Be("Network error");
    }

    [Fact]
    public async Task PostWithRawAsync_ReturnsDataAndRawResponse()
    {
        // Arrange
        var responseJson = """{"data": {"name": "Test", "value": 99}}""";
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.OK,
                Content = new StringContent(responseJson, Encoding.UTF8, "application/json")
            });
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);

        // Act
        var (data, raw) = await graphQlClient.PostWithRawAsync<TestData>("query { test }");

        // Assert
        data.Should().NotBeNull();
        data.Name.Should().Be("Test");
        data.Value.Should().Be(99);
        raw.Should().Be(responseJson);
    }

    [Fact]
    public async Task PostAsync_WithVariables_SerializesCorrectly()
    {
        // Arrange
        string? capturedContent = null;
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>(async (req, ct) =>
            {
                // Read content before it gets disposed
                capturedContent = await req.Content!.ReadAsStringAsync(ct);
            })
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = HttpStatusCode.OK,
                Content = new StringContent("""{"data": {"name": "Test", "value": 1}}""")
            });
        
        var client = new HttpClient(mockHandler.Object) { BaseAddress = new Uri("https://test.api/") };
        var graphQlClient = new GraphQlClientImplementation(client);
        var variables = new Dictionary<string, object> { ["id"] = 123, ["name"] = "test" };

        // Act
        await graphQlClient.PostAsync<TestData>("query($id: Int!) { test(id: $id) }", variables);

        // Assert
        capturedContent.Should().NotBeNullOrEmpty();
        capturedContent!.Should().Contain("\"query\"");
        capturedContent.Should().Contain("\"variables\"");
        capturedContent.Should().Contain("123");
        capturedContent.Should().Contain("test");
    }
}
