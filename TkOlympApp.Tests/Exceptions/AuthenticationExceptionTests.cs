using TkOlympApp.Exceptions;

namespace TkOlympApp.Tests.Exceptions;

public class AuthenticationExceptionTests
{
    [Fact]
    public void AuthenticationException_WithInvalidCredentials_IsNotTransient()
    {
        // Arrange & Act
        var exception = new AuthenticationException(
            "Invalid username or password",
            AuthenticationException.AuthErrorType.InvalidCredentials);

        // Assert
        Assert.Equal(AuthenticationException.AuthErrorType.InvalidCredentials, exception.ErrorType);
        Assert.False(exception.IsTransient);
    }

    [Fact]
    public void AuthenticationException_WithNetworkError_IsTransient()
    {
        // Arrange & Act
        var exception = new AuthenticationException(
            "Network connection lost",
            AuthenticationException.AuthErrorType.NetworkError);

        // Assert
        Assert.Equal(AuthenticationException.AuthErrorType.NetworkError, exception.ErrorType);
        Assert.True(exception.IsTransient);
    }

    [Fact]
    public void AuthenticationException_WithTokenExpired_StoresHttpStatusCode()
    {
        // Arrange & Act
        var exception = new AuthenticationException(
            "JWT token expired",
            AuthenticationException.AuthErrorType.TokenExpired,
            httpStatusCode: 401);

        // Assert
        Assert.Equal(AuthenticationException.AuthErrorType.TokenExpired, exception.ErrorType);
        Assert.Equal(401, exception.HttpStatusCode);
        Assert.False(exception.IsTransient);
    }

    [Fact]
    public void AuthenticationException_WithUnauthorized_HasCorrectErrorType()
    {
        // Arrange & Act
        var exception = new AuthenticationException(
            "Insufficient permissions",
            AuthenticationException.AuthErrorType.Unauthorized,
            httpStatusCode: 403);

        // Assert
        Assert.Equal(AuthenticationException.AuthErrorType.Unauthorized, exception.ErrorType);
        Assert.Equal(403, exception.HttpStatusCode);
    }

    [Theory]
    [InlineData(AuthenticationException.AuthErrorType.InvalidCredentials, false)]
    [InlineData(AuthenticationException.AuthErrorType.TokenExpired, false)]
    [InlineData(AuthenticationException.AuthErrorType.TokenInvalid, false)]
    [InlineData(AuthenticationException.AuthErrorType.Unauthorized, false)]
    [InlineData(AuthenticationException.AuthErrorType.NetworkError, true)]
    public void AuthenticationException_TransientProperty_ReflectsErrorType(
        AuthenticationException.AuthErrorType errorType,
        bool expectedTransient)
    {
        // Arrange & Act
        var exception = new AuthenticationException("Test", errorType);

        // Assert
        Assert.Equal(expectedTransient, exception.IsTransient);
    }

    [Fact]
    public void AuthenticationException_WithInnerException_WrapsOriginal()
    {
        // Arrange
        var innerException = new HttpRequestException("Connection refused");

        // Act
        var exception = new AuthenticationException(
            "Login failed due to network error",
            AuthenticationException.AuthErrorType.NetworkError,
            innerException);

        // Assert
        Assert.NotNull(exception.InnerException);
        Assert.Equal("Connection refused", exception.InnerException.Message);
        Assert.IsType<HttpRequestException>(exception.InnerException);
    }

    [Fact]
    public void AuthenticationException_InheritsFromServiceException()
    {
        // Arrange & Act
        var exception = new AuthenticationException(
            "Test",
            AuthenticationException.AuthErrorType.InvalidCredentials);

        // Assert
        Assert.IsAssignableFrom<ServiceException>(exception);
    }
}
