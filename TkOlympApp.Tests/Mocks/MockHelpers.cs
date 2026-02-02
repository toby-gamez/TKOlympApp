using System.Net;
using System.Text;
using Moq;
using Moq.Protected;

namespace TkOlympApp.Tests.Mocks;

/// <summary>
/// Helper methods for creating mocks in unit tests.
/// </summary>
public static class MockHelpers
{
    /// <summary>
    /// Creates a mock HttpMessageHandler that returns the specified response.
    /// </summary>
    public static Mock<HttpMessageHandler> CreateMockHttpMessageHandler(
        HttpStatusCode statusCode,
        string content,
        string contentType = "application/json")
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = statusCode,
                Content = new StringContent(content, Encoding.UTF8, contentType)
            });
        return mockHandler;
    }

    /// <summary>
    /// Creates a mock HttpMessageHandler that invokes a callback to build the response.
    /// </summary>
    public static Mock<HttpMessageHandler> CreateMockHttpMessageHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> responseFactory)
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Returns(responseFactory);
        return mockHandler;
    }

    /// <summary>
    /// Creates a mock HttpClient using the specified mock handler.
    /// </summary>
    public static HttpClient CreateMockHttpClient(Mock<HttpMessageHandler> mockHandler, string baseAddress = "https://api.example.com/")
    {
        var client = new HttpClient(mockHandler.Object)
        {
            BaseAddress = new Uri(baseAddress)
        };
        return client;
    }

    /// <summary>
    /// Creates a mock IHttpClientFactory that returns the specified HttpClient.
    /// </summary>
    public static Mock<IHttpClientFactory> CreateMockHttpClientFactory(HttpClient client)
    {
        var factory = new Mock<IHttpClientFactory>();
        factory.Setup(f => f.CreateClient(It.IsAny<string>()))
            .Returns(client);
        return factory;
    }
}
