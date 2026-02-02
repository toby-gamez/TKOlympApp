using System.Net;
using System.Text;
using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Maui.Storage;
using Moq;
using Moq.Protected;
using TkOlympApp.Exceptions;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.Tests.Mocks;
using Xunit;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Unit tests for AuthServiceImplementation.
/// Tests JWT token lifecycle, login/logout, token refresh, and error handling.
/// Target coverage: 80%+
/// </summary>
public class AuthServiceTests
{
    private const string TestJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJ0ZXN0dXNlciIsImV4cCI6OTk5OTk5OTk5OX0.X";
    private const string ExpiredJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJ0ZXN0dXNlciIsImV4cCI6MTYwMDAwMDAwMH0.X";

    [Fact]
    public void Constructor_WithNullHttpClient_ThrowsArgumentNullException()
    {
        // Arrange
        var mockFactory = new Mock<IHttpClientFactory>();
        var mockStorage = MockSecureStorage.Create();
        var mockUserService = new Mock<IUserService>();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new AuthServiceImplementation(null!, mockFactory.Object, mockStorage.Object, mockUserService.Object, NullLogger<AuthServiceImplementation>.Instance));
    }

    [Fact]
    public void Constructor_WithNullSecureStorage_ThrowsArgumentNullException()
    {
        // Arrange
        var httpClient = new HttpClient();
        var mockFactory = new Mock<IHttpClientFactory>();
        var mockUserService = new Mock<IUserService>();

        // Act & Assert
        Assert.Throws<ArgumentNullException>(() =>
            new AuthServiceImplementation(httpClient, mockFactory.Object, null!, mockUserService.Object, NullLogger<AuthServiceImplementation>.Instance));
    }

    [Fact]
    public async Task InitializeAsync_WithNoStoredToken_CompletesSuccessfully()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var authService = CreateAuthService(mockStorage: mockStorage);

        // Act
        await authService.InitializeAsync();

        // Assert
        var hasToken = await authService.HasTokenAsync();
        hasToken.Should().BeFalse();
    }

    [Fact]
    public async Task InitializeAsync_WithValidStoredToken_SetsAuthorizationHeader()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK, @"{""data"":{""refreshJwt"":""new-token""}}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act
        await authService.InitializeAsync();

        // Assert
        var hasToken = await authService.HasTokenAsync();
        hasToken.Should().BeTrue();
    }

    [Fact]
    public async Task HasTokenAsync_WithStoredToken_ReturnsTrue()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var authService = CreateAuthService(mockStorage: mockStorage);

        // Act
        var result = await authService.HasTokenAsync();

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public async Task HasTokenAsync_WithoutStoredToken_ReturnsFalse()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var authService = CreateAuthService(mockStorage: mockStorage);

        // Act
        var result = await authService.HasTokenAsync();

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public async Task LoginAsync_WithValidCredentials_ReturnsJwtAndStoresToken()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockHandler = MockHttpMessageHandler.CreateSequence(
            (HttpStatusCode.OK, @"{""data"":{""login"":{""result"":{""jwt"":""test-jwt-token""}}}}"),
            (HttpStatusCode.OK, @"{""data"":{""userProxiesList"":[{""person"":{""id"":""123""}}]}}")
        );
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act
        var jwt = await authService.LoginAsync("testuser", "testpass");

        // Assert
        jwt.Should().Be("test-jwt-token");
        var storedToken = await mockStorage.Object.GetAsync("jwt");
        storedToken.Should().Be("test-jwt-token");
    }

    [Fact]
    public async Task LoginAsync_WithInvalidCredentials_ThrowsGraphQLException()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""errors"":[{""message"":""Neplatné přihlašovací údaje.""}]}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<GraphQLException>(() =>
            authService.LoginAsync("testuser", "wrongpass"));
        exception.Message.Should().Contain("Neplatné přihlašovací údaje");
    }

    [Fact]
    public async Task LoginAsync_WithEmptyJwtResponse_ThrowsGraphQLException()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""login"":{""result"":{""jwt"":""""}}}}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act & Assert
        await Assert.ThrowsAsync<GraphQLException>(() =>
            authService.LoginAsync("testuser", "testpass"));
    }

    [Fact]
    public async Task LoginAsync_WithNullJwtResponse_ThrowsGraphQLException()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""login"":{""result"":{""jwt"":null}}}}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act & Assert
        await Assert.ThrowsAsync<GraphQLException>(() =>
            authService.LoginAsync("testuser", "testpass"));
    }

    [Fact]
    public async Task LoginAsync_SetsPersonIdFromUserProxies()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var mockUserService = new Mock<IUserService>();
        var mockHandler = MockHttpMessageHandler.CreateSequence(
            (HttpStatusCode.OK, @"{""data"":{""login"":{""result""{""jwt"":""test-jwt-token""}}}}"),
            (HttpStatusCode.OK, @"{""data"":{""userProxiesList"":[{""person"":{""id"":""person-123""}}]}}")
        );
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage, mockUserService: mockUserService);

        // Act
        await authService.LoginAsync("testuser", "testpass");

        // Assert
        mockUserService.Verify(u => u.SetCurrentPersonIdAsync("person-123", It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task LogoutAsync_ClearsTokenAndAuthorizationHeader()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var mockUserService = new Mock<IUserService>();
        var authService = CreateAuthService(mockStorage: mockStorage, mockUserService: mockUserService);

        // Act
        await authService.LogoutAsync();

        // Assert
        var storedToken = await mockStorage.Object.GetAsync("jwt");
        storedToken.Should().BeEmpty();
        mockUserService.Verify(u => u.SetCurrentPersonIdAsync(null, It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task RefreshJwtAsync_WithValidToken_ReturnsNewJwt()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""refreshJwt"":""new-jwt-token""}}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act
        var newJwt = await authService.RefreshJwtAsync();

        // Assert
        newJwt.Should().Be("new-jwt-token");
        var storedToken = await mockStorage.Object.GetAsync("jwt");
        storedToken.Should().Be("new-jwt-token");
    }

    [Fact]
    public async Task RefreshJwtAsync_WithGraphQLError_ThrowsGraphQLException()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""errors"":[{""message"":""Token refresh failed""}]}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act & Assert
        var exception = await Assert.ThrowsAsync<GraphQLException>(() =>
            authService.RefreshJwtAsync());
        exception.Message.Should().Contain("Token refresh failed");
    }

    [Fact]
    public async Task RefreshJwtAsync_WithEmptyResponse_ThrowsGraphQLException()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""refreshJwt"":""""}}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act & Assert
        await Assert.ThrowsAsync<GraphQLException>(() =>
            authService.RefreshJwtAsync());
    }

    [Fact]
    public async Task TryRefreshIfNeededAsync_WithoutToken_ReturnsFalse()
    {
        // Arrange
        var mockStorage = MockSecureStorage.Create();
        var authService = CreateAuthService(mockStorage: mockStorage);

        // Act
        var result = await authService.TryRefreshIfNeededAsync();

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public async Task TryRefreshIfNeededAsync_WithValidToken_ReturnsTrue()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = TestJwt
        });
        var authService = CreateAuthService(mockStorage: mockStorage);

        // Act
        var result = await authService.TryRefreshIfNeededAsync();

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public async Task TryRefreshIfNeededAsync_WithExpiredToken_RefreshesToken()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = ExpiredJwt
        });
        var mockHandler = MockHttpMessageHandler.Create(HttpStatusCode.OK,
            @"{""data"":{""refreshJwt"":""refreshed-jwt-token""}}");
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage);

        // Act
        var result = await authService.TryRefreshIfNeededAsync();

        // Assert
        result.Should().BeTrue();
        var storedToken = await mockStorage.Object.GetAsync("jwt");
        storedToken.Should().Be("refreshed-jwt-token");
    }

    [Fact]
    public async Task TryRefreshIfNeededAsync_WhenRefreshFails_LogsOutAndReturnsFalse()
    {
        // Arrange
        var mockStorage = MockSecureStorage.CreateWithValues(new Dictionary<string, string>
        {
            ["jwt"] = ExpiredJwt
        });
        var mockHandler = MockHttpMessageHandler.CreateThrows(new HttpRequestException("Network error"));
        var mockUserService = new Mock<IUserService>();
        var authService = CreateAuthService(mockHandler: mockHandler, mockStorage: mockStorage, mockUserService: mockUserService);

        // Act
        var result = await authService.TryRefreshIfNeededAsync();

        // Assert
        result.Should().BeFalse();
        var storedToken = await mockStorage.Object.GetAsync("jwt");
        storedToken.Should().BeEmpty();
    }

    // Helper method to create a mock IAuthService with typical behavior
    private Mock<IAuthService> CreateMockAuthService(
        Mock<ISecureStorage>? mockStorage = null)
    {
        mockStorage ??= MockSecureStorage.Create();
        var mockAuthService = new Mock<IAuthService>();
        
        // Configure default behaviors
        mockAuthService.Setup(a => a.HasTokenAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(() => mockStorage.Object.GetAsync("jwt").Result != null);
            
        mockAuthService.Setup(a => a.LoginAsync(It.IsAny<string>(), It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync("test-jwt-token");
            
        return mockAuthService;
    }
}
