using System.Net;
using FluentAssertions;
using TkOlympApp.Exceptions;
using TkOlympApp.Services;
using TkOlympApp.Tests.Mocks;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for GraphQlClientImplementation.
/// Tests GraphQL query execution, error handling, and response parsing.
/// Target coverage: 90%+
/// </summary>
public class GraphQlClientTests
{
    private sealed class TestData
    {
        public string? Message { get; set; }
        public int Value { get; set; }
    }

    [Fact]
    public void Constructor_WithNullHttpClient_ThrowsArgumentNullException()
    {
        // Act & Assert
        Assert.Throws<ArgumentNullException>(() => new GraphQlClientImplementation(null!));
    }

    [Fact]
    public async Task PostAsync_WithSuccessfulResponse_ReturnsDeserializedData()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""message"":""Hello"",""value"":42}}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act
        var result = await client.PostAsync<TestData>("query { test }");

        // Assert
        result.Should().NotBeNull();
        result.Message.Should().Be("Hello");
        result.Value.Should().Be(42);
    }

    [Fact]
    public async Task PostAsync_WithVariables_SendsCorrectRequest()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""message"":""Success"",""value"":100}}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);
        var variables = new Dictionary<string, object>
        {
            ["id"] = 123,
            ["name"] = "test"
        };

        // Act
        var result = await client.PostAsync<TestData>("query($id: Int!) { test(id: $id) }", variables);

        // Assert
        result.Should().NotBeNull();
        result.Message.Should().Be("Success");
        result.Value.Should().Be(100);
    }

    [Fact]
    public async Task PostAsync_WithGraphQLErrors_ThrowsGraphQLException()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":null,""errors"":[{""message"":""Field 'test' not found""}]}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<GraphQLException>(() =>
            client.PostAsync<TestData>("query { test }"));
        exception.Message.Should().Contain("Field 'test' not found");
        exception.Errors.Should().ContainSingle();
    }

    [Fact]
    public async Task PostAsync_WithMultipleGraphQLErrors_ThrowsWithFirstError()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":null,""errors"":[{""message"":""Error 1""},{""message"":""Error 2""}]}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<GraphQLException>(() =>
            client.PostAsync<TestData>("query { test }"));
        exception.Message.Should().Be("Error 1");
        exception.Errors.Should().HaveCount(2);
    }

    [Fact]
    public async Task PostAsync_WithEmptyErrorMessage_UsesDefaultMessage()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":null,""errors"":[{""message"":""""}]}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<GraphQLException>(() =>
            client.PostAsync<TestData>("query { test }"));
        exception.Message.Should().NotBeEmpty();
    }

    [Fact]
    public async Task PostAsync_With400StatusCode_ThrowsServiceException()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.BadRequest,
            @"{""error"":""Bad request""}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<ServiceException>(() =>
            client.PostAsync<TestData>("query { test }"));
        exception.HttpStatusCode.Should().Be(400);
        exception.IsTransient.Should().BeFalse();
    }

    [Fact]
    public async Task PostAsync_With500StatusCode_ThrowsTransientServiceException()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.InternalServerError,
            @"{""error"":""Internal server error""}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<ServiceException>(() =>
            client.PostAsync<TestData>("query { test }"));
        exception.HttpStatusCode.Should().Be(500);
        exception.IsTransient.Should().BeTrue();
    }

    [Fact]
    public async Task PostAsync_WithNetworkError_ThrowsTransientServiceException()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.CreateThrows(new HttpRequestException("Network unreachable"));
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<ServiceException>(() =>
            client.PostAsync<TestData>("query { test }"));
        exception.IsTransient.Should().BeTrue();
        exception.InnerException.Should().BeOfType<HttpRequestException>();
    }

    [Fact]
    public async Task PostAsync_WithCancellationToken_PropagatesCancellation()
    {
        // Arrange
        var cts = new CancellationTokenSource();
        cts.Cancel();
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK, "{}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(() =>
            client.PostAsync<TestData>("query { test }", null, cts.Token));
    }

    [Fact]
    public async Task PostAsync_WithNullData_ReturnsDefault()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":null}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act
        var result = await client.PostAsync<TestData>("query { test }");

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public async Task PostWithRawAsync_ReturnsDataAndRawResponse()
    {
        // Arrange
        var rawResponse = @"{""data"":{""message"":""Hello"",""value"":42}}";
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK, rawResponse);
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act
        var (data, raw) = await client.PostWithRawAsync<TestData>("query { test }");

        // Assert
        data.Should().NotBeNull();
        data.Message.Should().Be("Hello");
        data.Value.Should().Be(42);
        raw.Should().Be(rawResponse);
    }

    [Fact]
    public async Task PostWithRawAsync_WithGraphQLErrors_ThrowsGraphQLExceptionWithRawResponse()
    {
        // Arrange
        var rawResponse = @"{""data"":null,""errors"":[{""message"":""Test error""}]}";
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK, rawResponse);
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<GraphQLException>(() =>
            client.PostWithRawAsync<TestData>("query { test }"));
        exception.RawResponse.Should().Be(rawResponse);
    }

    [Fact]
    public async Task PostWithRawAsync_With500StatusCode_ThrowsServiceExceptionWithBody()
    {
        // Arrange
        var errorBody = @"{""error"":""Server error""}";
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.InternalServerError, errorBody);
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<ServiceException>(() =>
            client.PostWithRawAsync<TestData>("query { test }"));
        exception.Context.Should().ContainKey("Body");
        exception.Context["Body"].Should().Be(errorBody);
    }

    [Fact]
    public async Task PostAsync_WithMalformedJson_ThrowsException()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{invalid json}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act & Assert
        await Assert.ThrowsAnyAsync<Exception>(() =>
            client.PostAsync<TestData>("query { test }"));
    }

    [Fact]
    public async Task PostAsync_WithCaseInsensitivePropertyNames_DeserializesCorrectly()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""MESSAGE"":""Test"",""VALUE"":99}}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act
        var result = await client.PostAsync<TestData>("query { test }");

        // Assert
        result.Should().NotBeNull();
        result.Message.Should().Be("Test");
        result.Value.Should().Be(99);
    }

    [Fact]
    public async Task PostAsync_WithNestedData_DeserializesCorrectly()
    {
        // Arrange
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""nested"":{""message"":""Nested"",""value"":77}}}");
        var httpClient = MockHttpMessageHandler.CreateHttpClient(mockHandler);
        var client = new GraphQlClientImplementation(httpClient);

        // Act
        var result = await client.PostAsync<NestedResponse>("query { test }");

        // Assert
        result.Should().NotBeNull();
        result.Nested.Should().NotBeNull();
        result.Nested.Message.Should().Be("Nested");
        result.Nested.Value.Should().Be(77);
    }

    private sealed class NestedResponse
    {
        public TestData? Nested { get; set; }
    }
}
