using System.Net;
using System.Text;
using Moq;
using Moq.Protected;

namespace TkOlympApp.Tests.Mocks;

/// <summary>
/// Helper class for creating mock HttpMessageHandlers to test HTTP-based services.
/// </summary>
public static class MockHttpMessageHandler
{
    /// <summary>
    /// Creates a mock HttpMessageHandler that returns the specified response.
    /// </summary>
    public static Mock<HttpMessageHandler> Create(HttpStatusCode statusCode, string content, string contentType = "application/json")
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>()
            )
            .ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = statusCode,
                Content = new StringContent(content, Encoding.UTF8, contentType)
            });
        return mockHandler;
    }

    /// <summary>
    /// Creates a mock HttpMessageHandler that can be configured with multiple responses.
    /// </summary>
    public static Mock<HttpMessageHandler> CreateSequence(params (HttpStatusCode statusCode, string content)[] responses)
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        var setup = mockHandler
            .Protected()
            .SetupSequence<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>()
            );

        foreach (var (statusCode, content) in responses)
        {
            setup = setup.ReturnsAsync(new HttpResponseMessage
            {
                StatusCode = statusCode,
                Content = new StringContent(content, Encoding.UTF8, "application/json")
            });
        }

        return mockHandler;
    }

    /// <summary>
    /// Creates a mock HttpMessageHandler that throws an exception.
    /// </summary>
    public static Mock<HttpMessageHandler> CreateThrows(Exception exception)
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>()
            )
            .ThrowsAsync(exception);
        return mockHandler;
    }

    /// <summary>
    /// Creates an HttpClient from a mocked HttpMessageHandler.
    /// </summary>
    public static HttpClient CreateHttpClient(Mock<HttpMessageHandler> mockHandler, string baseAddress = "https://api.example.com/graphql")
    {
        return new HttpClient(mockHandler.Object)
        {
            BaseAddress = new Uri(baseAddress)
        };
    }
}
