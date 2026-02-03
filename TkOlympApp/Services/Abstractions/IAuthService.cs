using System.Net.Http;

namespace TkOlympApp.Services.Abstractions;

/// <summary>
/// Abstraction for authentication and authorization operations.
/// Manages JWT token lifecycle, user authentication, and automatic token refresh.
/// </summary>
public interface IAuthService
{
    /// <summary>
    /// Gets the configured HttpClient with authentication headers.
    /// </summary>
    HttpClient Http { get; }

    /// <summary>
    /// Initializes the authentication service by loading and validating stored JWT token.
    /// If token exists but is expired, attempts automatic refresh.
    /// Should be called during application startup.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    /// <exception cref="Exception">Thrown if token refresh fails during initialization.</exception>
    Task InitializeAsync(CancellationToken ct = default);

    /// <summary>
    /// Checks if JWT token needs refresh and performs refresh if necessary.
    /// Token is considered expired if within a small leeway window (default 60 seconds).
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>True if token is valid or successfully refreshed; false if no token exists.</returns>
    /// <exception cref="InvalidOperationException">Thrown if refresh operation fails.</exception>
    Task<bool> TryRefreshIfNeededAsync(CancellationToken ct = default);

    /// <summary>
    /// Performs JWT token refresh using the current token.
    /// Updates secure storage and HTTP client authorization header with new token.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>The new JWT token, or null if refresh failed.</returns>
    /// <exception cref="InvalidOperationException">Thrown if GraphQL request fails or returns errors.</exception>
    Task<string?> RefreshJwtAsync(CancellationToken ct = default);

    /// <summary>
    /// Checks if a valid JWT token exists in secure storage.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>True if token exists and is not empty; false otherwise.</returns>
    Task<bool> HasTokenAsync(CancellationToken ct = default);

    /// <summary>
    /// Authenticates user with login credentials and stores the returned JWT token.
    /// Updates HTTP client authorization header upon successful login.
    /// </summary>
    /// <param name="login">User login/username.</param>
    /// <param name="passwd">User password.</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>The JWT token on success, or null if login failed.</returns>
    /// <exception cref="InvalidOperationException">Thrown if GraphQL request fails.</exception>
    Task<string?> LoginAsync(string login, string passwd, CancellationToken ct = default);

    /// <summary>
    /// Logs out the current user by clearing stored JWT token and removing authorization header.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    Task LogoutAsync(CancellationToken ct = default);

    /// <summary>
    /// Changes the current user's password.
    /// </summary>
    Task ChangePasswordAsync(string newPassword, CancellationToken ct = default);
}
