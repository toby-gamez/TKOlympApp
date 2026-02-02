using System.Net;
using System.Text;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Storage;
using Moq;
using Moq.Protected;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.Exceptions;
using Xunit;

namespace TkOlympApp.Tests.Services;

public class AuthServiceImplementationTests
{
    [Fact]
    public async Task LoginAsync_Success_PersistsJwt_SetsAuthorization_AndPersistsPersonId()
    {
        var jwt = CreateJwtWithExp(DateTimeOffset.UtcNow.AddHours(1));

        var handler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        handler.Protected()
            .Setup<Task<HttpResponseMessage>>("SendAsync", ItExpr.IsAny<HttpRequestMessage>(), ItExpr.IsAny<CancellationToken>())
            .Returns<HttpRequestMessage, CancellationToken>(async (req, ct) =>
            {
                var body = req.Content == null ? string.Empty : await req.Content.ReadAsStringAsync(ct);

                if (body.Contains("login(input", StringComparison.OrdinalIgnoreCase))
                {
                    return new HttpResponseMessage(HttpStatusCode.OK)
                    {
                        Content = new StringContent(
                            "{\"data\":{\"login\":{\"result\":{\"jwt\":\"" + jwt + "\"}}}}",
                            Encoding.UTF8,
                            "application/json")
                    };
                }

                if (body.Contains("userProxiesList", StringComparison.OrdinalIgnoreCase))
                {
                    return new HttpResponseMessage(HttpStatusCode.OK)
                    {
                        Content = new StringContent(
                            "{\"data\":{\"userProxiesList\":[{\"person\":{\"id\":\"42\"}}]}}",
                            Encoding.UTF8,
                            "application/json")
                    };
                }

                return new HttpResponseMessage(HttpStatusCode.BadRequest)
                {
                    Content = new StringContent("{\"errors\":[{\"message\":\"unexpected\"}]}", Encoding.UTF8, "application/json")
                };
            });

        var httpClient = new HttpClient(handler.Object) { BaseAddress = new Uri("https://api.example.com/") };

        var bareHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var bareClient = new HttpClient(bareHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };
        var httpClientFactory = new Mock<IHttpClientFactory>(MockBehavior.Strict);
        httpClientFactory.Setup(f => f.CreateClient("AuthService.Bare")).Returns(bareClient);

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Strict);
        secureStorage.Setup(s => s.SetAsync(AppConstants.JwtStorageKey, jwt)).Returns(Task.CompletedTask);

        var userService = new Mock<IUserService>(MockBehavior.Strict);
        userService.Setup(s => s.SetCurrentPersonIdAsync("42", It.IsAny<CancellationToken>())).Returns(Task.CompletedTask);

        var logger = new Mock<ILogger<AuthServiceImplementation>>();

        var svc = new AuthServiceImplementation(httpClient, httpClientFactory.Object, secureStorage.Object, userService.Object, logger.Object);

        var returnedJwt = await svc.LoginAsync("john", "pwd");

        Assert.Equal(jwt, returnedJwt);
        Assert.NotNull(httpClient.DefaultRequestHeaders.Authorization);
        Assert.Equal("Bearer", httpClient.DefaultRequestHeaders.Authorization!.Scheme);
        Assert.Equal(jwt, httpClient.DefaultRequestHeaders.Authorization.Parameter);

        secureStorage.VerifyAll();
        userService.VerifyAll();
        handler.Protected().Verify(
            "SendAsync",
            Times.Exactly(2),
            ItExpr.IsAny<HttpRequestMessage>(),
            ItExpr.IsAny<CancellationToken>());
    }

    [Fact]
    public async Task LoginAsync_WhenNoJwt_ThrowsGraphQLException()
    {
        var handler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        handler.Protected()
            .Setup<Task<HttpResponseMessage>>("SendAsync", ItExpr.IsAny<HttpRequestMessage>(), ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    "{\"errors\":[{\"message\":\"bad creds\"}],\"data\":{\"login\":{\"result\":{\"jwt\":null}}}}",
                    Encoding.UTF8,
                    "application/json")
            });

        var httpClient = new HttpClient(handler.Object) { BaseAddress = new Uri("https://api.example.com/") };

        var bareHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var bareClient = new HttpClient(bareHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };
        var httpClientFactory = new Mock<IHttpClientFactory>(MockBehavior.Strict);
        httpClientFactory.Setup(f => f.CreateClient("AuthService.Bare")).Returns(bareClient);

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Loose);
        var userService = new Mock<IUserService>(MockBehavior.Loose);
        var logger = new Mock<ILogger<AuthServiceImplementation>>();

        var svc = new AuthServiceImplementation(httpClient, httpClientFactory.Object, secureStorage.Object, userService.Object, logger.Object);

        var ex = await Assert.ThrowsAsync<GraphQLException>(() => svc.LoginAsync("john", "wrong"));
        Assert.Contains("bad creds", ex.Message);

        handler.Protected().Verify(
            "SendAsync",
            Times.Once(),
            ItExpr.IsAny<HttpRequestMessage>(),
            ItExpr.IsAny<CancellationToken>());
    }

    [Fact]
    public async Task TryRefreshIfNeededAsync_WhenTokenValid_SetsAuthorizationAndReturnsTrue_WithoutCallingBareClient()
    {
        var jwt = CreateJwtWithExp(DateTimeOffset.UtcNow.AddHours(1));

        var apiHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var httpClient = new HttpClient(apiHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };

        var bareHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var bareClient = new HttpClient(bareHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };

        var httpClientFactory = new Mock<IHttpClientFactory>(MockBehavior.Strict);
        httpClientFactory.Setup(f => f.CreateClient("AuthService.Bare")).Returns(bareClient);

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Strict);
        secureStorage.Setup(s => s.GetAsync(AppConstants.JwtStorageKey)).ReturnsAsync(jwt);

        var userService = new Mock<IUserService>(MockBehavior.Loose);
        var logger = new Mock<ILogger<AuthServiceImplementation>>();

        var svc = new AuthServiceImplementation(httpClient, httpClientFactory.Object, secureStorage.Object, userService.Object, logger.Object);

        var ok = await svc.TryRefreshIfNeededAsync();

        Assert.True(ok);
        Assert.NotNull(httpClient.DefaultRequestHeaders.Authorization);
        Assert.Equal(jwt, httpClient.DefaultRequestHeaders.Authorization!.Parameter);

        // Should not attempt any HTTP calls when token is valid
        apiHandler.Protected().Verify(
            "SendAsync",
            Times.Never(),
            ItExpr.IsAny<HttpRequestMessage>(),
            ItExpr.IsAny<CancellationToken>());
        bareHandler.Protected().Verify(
            "SendAsync",
            Times.Never(),
            ItExpr.IsAny<HttpRequestMessage>(),
            ItExpr.IsAny<CancellationToken>());

        secureStorage.VerifyAll();
    }

    [Fact]
    public async Task RefreshJwtAsync_WhenRefreshReturnsToken_PersistsAndSetsAuthorization()
    {
        var newJwt = CreateJwtWithExp(DateTimeOffset.UtcNow.AddHours(1));

        var apiHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var httpClient = new HttpClient(apiHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };

        var bareHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        bareHandler.Protected()
            .Setup<Task<HttpResponseMessage>>("SendAsync", ItExpr.IsAny<HttpRequestMessage>(), ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    "{\"data\":{\"refreshJwt\":\"" + newJwt + "\"}}",
                    Encoding.UTF8,
                    "application/json")
            });

        var bareClient = new HttpClient(bareHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };

        var httpClientFactory = new Mock<IHttpClientFactory>(MockBehavior.Strict);
        httpClientFactory.Setup(f => f.CreateClient("AuthService.Bare")).Returns(bareClient);

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Strict);
        secureStorage.Setup(s => s.SetAsync(AppConstants.JwtStorageKey, newJwt)).Returns(Task.CompletedTask);

        var userService = new Mock<IUserService>(MockBehavior.Loose);
        var logger = new Mock<ILogger<AuthServiceImplementation>>();

        var svc = new AuthServiceImplementation(httpClient, httpClientFactory.Object, secureStorage.Object, userService.Object, logger.Object);

        var jwt = await svc.RefreshJwtAsync();

        Assert.Equal(newJwt, jwt);
        Assert.NotNull(httpClient.DefaultRequestHeaders.Authorization);
        Assert.Equal(newJwt, httpClient.DefaultRequestHeaders.Authorization!.Parameter);

        secureStorage.VerifyAll();
        bareHandler.Protected().Verify(
            "SendAsync",
            Times.Once(),
            ItExpr.IsAny<HttpRequestMessage>(),
            ItExpr.IsAny<CancellationToken>());
    }

    [Fact]
    public async Task LogoutAsync_ClearsJwtAndAuthorization_AndClearsPersonId()
    {
        var apiHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var httpClient = new HttpClient(apiHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };
        httpClient.DefaultRequestHeaders.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", "x");

        var bareHandler = new Mock<HttpMessageHandler>(MockBehavior.Strict);
        var bareClient = new HttpClient(bareHandler.Object) { BaseAddress = new Uri("https://api.example.com/") };
        var httpClientFactory = new Mock<IHttpClientFactory>(MockBehavior.Strict);
        httpClientFactory.Setup(f => f.CreateClient("AuthService.Bare")).Returns(bareClient);

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Strict);
        secureStorage.Setup(s => s.SetAsync(AppConstants.JwtStorageKey, string.Empty)).Returns(Task.CompletedTask);

        var userService = new Mock<IUserService>(MockBehavior.Strict);
        userService.Setup(s => s.SetCurrentPersonIdAsync(null, It.IsAny<CancellationToken>())).Returns(Task.CompletedTask);

        var logger = new Mock<ILogger<AuthServiceImplementation>>();

        var svc = new AuthServiceImplementation(httpClient, httpClientFactory.Object, secureStorage.Object, userService.Object, logger.Object);

        await svc.LogoutAsync();

        Assert.Null(httpClient.DefaultRequestHeaders.Authorization);
        secureStorage.VerifyAll();
        userService.VerifyAll();
    }

    private static string CreateJwtWithExp(DateTimeOffset exp)
    {
        static string Base64Url(string s)
        {
            var bytes = Encoding.UTF8.GetBytes(s);
            return Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        }

        var header = Base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        var payload = Base64Url("{\"exp\":" + exp.ToUnixTimeSeconds() + "}");
        return header + "." + payload + ".";
    }
}
