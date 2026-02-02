using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Storage;
using TkOlympApp.Helpers;
using TkOlympApp.Services.Abstractions;
using TkOlympApp.Exceptions;

namespace TkOlympApp.Services;

/// <summary>
/// Instance-based authentication service with dependency injection support.
/// </summary>
public class AuthServiceImplementation : IAuthService
{
    private readonly HttpClient _httpClient;
    private readonly HttpClient _bareClient;
    private readonly ISecureStorage _secureStorage;
    private readonly ILogger<AuthServiceImplementation> _logger;
    private readonly IUserService _userService;

    public HttpClient Http => _httpClient;

    public AuthServiceImplementation(
        HttpClient httpClient,
        IHttpClientFactory httpClientFactory,
        ISecureStorage secureStorage,
        IUserService userService,
        ILogger<AuthServiceImplementation> logger)
    {
        _httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        _secureStorage = secureStorage ?? throw new ArgumentNullException(nameof(secureStorage));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
        
        // Create bare client without auth handler for token refresh (avoid recursion)
        _bareClient = httpClientFactory.CreateClient("AuthService.Bare");
        
        _logger.LogDebug("AuthService initialized");
    }

    public async Task InitializeAsync(CancellationToken ct = default)
    {
        _logger.LogDebug("Initializing AuthService");
        var jwt = await _secureStorage.GetAsync(AppConstants.JwtStorageKey);
        if (!string.IsNullOrWhiteSpace(jwt))
        {
            _logger.LogInformation("Found existing JWT token, attempting refresh");
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            
            try
            {
                await TryRefreshIfNeededAsync(ct);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Token refresh failed during initialization, forcing logout");
                try { await LogoutAsync(ct); }
                catch (Exception logoutEx) { _logger.LogError(logoutEx, "Logout failed during cleanup"); }
            }
        }
        else
        {
            _logger.LogDebug("No existing JWT token found");
        }
    }

    public async Task<bool> TryRefreshIfNeededAsync(CancellationToken ct = default)
    {
        var jwt = await _secureStorage.GetAsync(AppConstants.JwtStorageKey);
        if (string.IsNullOrWhiteSpace(jwt))
        {
            _logger.LogDebug("No JWT token to refresh");
            return false;
        }

        if (!IsJwtExpired(jwt))
        {
            _logger.LogDebug("JWT token is still valid, no refresh needed");
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            return true;
        }

        _logger.LogInformation("JWT token expired or near expiry, attempting refresh");
        try
        {
            var newJwt = await RefreshJwtAsync(ct);
            var success = !string.IsNullOrWhiteSpace(newJwt);
            if (success) _logger.LogInformation("JWT token refreshed successfully");
            else _logger.LogWarning("JWT token refresh returned empty token");
            return success;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "JWT token refresh failed, clearing stored credentials");
            try { await LogoutAsync(ct); }
            catch (Exception logoutEx) { _logger.LogError(logoutEx, "Logout failed during refresh error cleanup"); }
            return false;
        }
    }

    public async Task<string?> RefreshJwtAsync(CancellationToken ct = default)
    {
        var gql = new GraphQlRequest { Query = "query Refresh { refreshJwt }" };
        var json = JsonSerializer.Serialize(gql);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await _bareClient.PostAsync("", content, ct);

        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
        var result = JsonSerializer.Deserialize<GraphQlResponse<RefreshJwtData>>(body, options);
        var jwt = result?.Data?.RefreshJwt;

        if (string.IsNullOrWhiteSpace(jwt))
        {
            var errors = result?.Errors?.Select(e => e.Message ?? "").ToList() ?? new List<string>();
            var errMsg = errors.FirstOrDefault() ?? LocalizationService.Get("Auth_RefreshFailed") ?? "Obnovení tokenu selhalo.";
            throw new GraphQLException(errMsg, errors, body);
        }

        await _secureStorage.SetAsync(AppConstants.JwtStorageKey, jwt);
        _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
        return jwt;
    }

    public async Task<bool> HasTokenAsync(CancellationToken ct = default)
    {
        var jwt = await _secureStorage.GetAsync(AppConstants.JwtStorageKey);
        return !string.IsNullOrWhiteSpace(jwt);
    }

    public async Task<string?> LoginAsync(string login, string passwd, CancellationToken ct = default)
    {
        _logger.LogInformation("Attempting login for user: {Login}", login);
        
        var gql = new GraphQlRequest
        {
            Query = "mutation($login: String!, $passwd: String!) { login(input: {login: $login, passwd: $passwd}) { result { jwt } } }",
            Variables = new Dictionary<string, object> { {"login", login}, {"passwd", passwd} }
        };

        var json = JsonSerializer.Serialize(gql);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await _httpClient.PostAsync("", content, ct);
        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
        var result = JsonSerializer.Deserialize<GraphQlResponse<LoginData>>(body, options);
        var jwt = result?.Data?.Login?.Result?.Jwt;

        if (string.IsNullOrWhiteSpace(jwt))
        {
            var errors = result?.Errors?.Select(e => e.Message ?? "").ToList() ?? new List<string>();
            var errMsg = errors.FirstOrDefault() ?? LocalizationService.Get("Auth_InvalidCredentials") ?? "Neplatné přihlašovací údaje.";
            _logger.LogWarning("Login failed for user {Login}: {Error}", login, errMsg);
            throw new GraphQLException(errMsg, errors, body);
        }

        _logger.LogInformation("Login successful for user: {Login}", login);

        await _secureStorage.SetAsync(AppConstants.JwtStorageKey, jwt);
        _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
        
        // Fetch user person ID
        try
        {
            _logger.LogDebug("Fetching user person ID");
            var gqlReq2 = new GraphQlRequest { Query = "query { userProxiesList { person { id } } }" };
            var json2 = JsonSerializer.Serialize(gqlReq2);
            using var content2 = new StringContent(json2, Encoding.UTF8, "application/json");
            using var resp2 = await _httpClient.PostAsync("", content2, ct);
            resp2.EnsureSuccessStatusCode();

            var body2 = await resp2.Content.ReadAsStringAsync(ct);
            var parsed = JsonSerializer.Deserialize<GraphQlResponse<UserProxiesData>>(body2, options);
            var id = parsed?.Data?.UserProxiesList?.FirstOrDefault()?.Person?.Id;
            if (!string.IsNullOrWhiteSpace(id))
            {
                await _userService.SetCurrentPersonIdAsync(id, ct);
                _logger.LogDebug("User person ID set: {PersonId}", id);
            }
            else _logger.LogWarning("No person ID found for user");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to fetch or persist user person ID (non-critical)");
        }
        return jwt;
    }

    public async Task LogoutAsync(CancellationToken ct = default)
    {
        _logger.LogInformation("Logging out user");
        try
        {
            try { await _secureStorage.SetAsync(AppConstants.JwtStorageKey, string.Empty); }
            catch (Exception ex) { _logger.LogWarning(ex, "Failed to clear JWT token from secure storage"); }
            
            _httpClient.DefaultRequestHeaders.Authorization = null;

            try { await _userService.SetCurrentPersonIdAsync(null, ct); }
            catch (Exception ex) { _logger.LogWarning(ex, "Failed to clear person ID"); }
            
            _logger.LogInformation("Logout completed successfully");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error during logout");
        }
    }

    private static bool IsJwtExpired(string jwt, int leewaySeconds = 60)
    {
        try
        {
            var parts = jwt.Split('.');
            if (parts.Length < 2) return true;
            var payload = parts[1].Replace('-', '+').Replace('_', '/');
            switch (payload.Length % 4)
            {
                case 2: payload += "=="; break;
                case 3: payload += "="; break;
                case 1: payload += "==="; break;
            }
            var bytes = Convert.FromBase64String(payload);
            var json = Encoding.UTF8.GetString(bytes);
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.TryGetProperty("exp", out var expEl) && expEl.ValueKind == JsonValueKind.Number)
            {
                var exp = expEl.GetInt64();
                var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                return exp <= now + leewaySeconds;
            }
            return true;
        }
        catch { return true; }
    }

    #region DTOs
    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class LoginData
    {
        [JsonPropertyName("login")] public LoginPayload? Login { get; set; }
    }

    private sealed class LoginPayload
    {
        [JsonPropertyName("result")] public LoginResult? Result { get; set; }
    }

    private sealed class LoginResult
    {
        [JsonPropertyName("jwt")] public string? Jwt { get; set; }
    }

    private sealed class RefreshJwtData
    {
        [JsonPropertyName("refreshJwt")] public string? RefreshJwt { get; set; }
    }

    private sealed class UserProxiesData
    {
        [JsonPropertyName("userProxiesList")] public UserProxy[]? UserProxiesList { get; set; }
    }

    private sealed class UserProxy
    {
        [JsonPropertyName("person")] public Person? Person { get; set; }
    }

    private sealed class Person
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
    }
    #endregion
}

/// <summary>
/// Delegating handler that intercepts 401/403 and attempts automatic token refresh.
/// </summary>
public class AuthDelegatingHandler : DelegatingHandler
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ISecureStorage _secureStorage;
    private readonly ILogger<AuthDelegatingHandler> _logger;

    public AuthDelegatingHandler(
        IHttpClientFactory httpClientFactory,
        ISecureStorage secureStorage,
        ILogger<AuthDelegatingHandler> logger)
    {
        _httpClientFactory = httpClientFactory ?? throw new ArgumentNullException(nameof(httpClientFactory));
        _secureStorage = secureStorage ?? throw new ArgumentNullException(nameof(secureStorage));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        var requestId = Guid.NewGuid().ToString("N")[..8];
        using var scope = _logger.BeginScope(new Dictionary<string, object?>
        {
            ["RequestId"] = requestId,
            ["TenantId"] = AppConstants.TenantId,
            ["RequestMethod"] = request.Method.ToString(),
            ["RequestUri"] = request.RequestUri?.PathAndQuery
        });

        // Ensure we send the JWT on the first attempt.
        // Without this, legacy callers that use AuthService.Http may hit 401 first and pay an extra round-trip (and sometimes time out).
        if (request.Headers.Authorization == null)
        {
            var storedJwt = await _secureStorage.GetAsync(AppConstants.JwtStorageKey).ConfigureAwait(false);
            if (!string.IsNullOrWhiteSpace(storedJwt))
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", storedJwt);
        }

        var response = await base.SendAsync(request, cancellationToken).ConfigureAwait(false);

        if (response.StatusCode != HttpStatusCode.Unauthorized && response.StatusCode != HttpStatusCode.Forbidden)
            return response;

        _logger.LogWarning("Received {StatusCode} response, attempting token refresh", response.StatusCode);

        var refreshed = false;
        try
        {
            refreshed = await TryRefreshTokenAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Token refresh attempt failed in delegating handler");
        }

        if (!refreshed)
        {
            _logger.LogWarning("Token refresh unsuccessful, clearing token");
            try { await _secureStorage.SetAsync(AppConstants.JwtStorageKey, string.Empty).ConfigureAwait(false); }
            catch (Exception ex) { _logger.LogError(ex, "Clear token failed in delegating handler"); }
            return response;
        }

        _logger.LogInformation("Token refreshed, retrying original request");

        var retry = await CloneHttpRequestMessageAsync(request).ConfigureAwait(false);
        var jwt = await _secureStorage.GetAsync(AppConstants.JwtStorageKey).ConfigureAwait(false);
        if (!string.IsNullOrWhiteSpace(jwt))
            retry.Headers.Authorization = new AuthenticationHeaderValue("Bearer", jwt);

        var retryResponse = await base.SendAsync(retry, cancellationToken).ConfigureAwait(false);
        if (retryResponse.StatusCode == HttpStatusCode.Unauthorized || retryResponse.StatusCode == HttpStatusCode.Forbidden)
        {
            _logger.LogError("Retry after token refresh still returned {StatusCode}, clearing token", retryResponse.StatusCode);
            try { await _secureStorage.SetAsync(AppConstants.JwtStorageKey, string.Empty).ConfigureAwait(false); }
            catch (Exception ex) { _logger.LogError(ex, "Clear token failed after retry failure"); }
        }

        return retryResponse;
    }

    /// <summary>
    /// Tries to refresh the JWT token using the bare HttpClient (no auth handler to avoid recursion).
    /// </summary>
    private async Task<bool> TryRefreshTokenAsync(CancellationToken cancellationToken)
    {
        try
        {
            var currentJwt = await _secureStorage.GetAsync(AppConstants.JwtStorageKey).ConfigureAwait(false);
            if (string.IsNullOrWhiteSpace(currentJwt))
            {
                _logger.LogWarning("No JWT found in secure storage, cannot refresh");
                return false;
            }

            var bareClient = _httpClientFactory.CreateClient("AuthService.Bare");
            
            var payload = new
            {
                query = "query Refresh { refreshJwt }"
            };

            var request = new HttpRequestMessage(HttpMethod.Post, bareClient.BaseAddress)
            {
                Content = JsonContent.Create(payload)
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", currentJwt);

            var response = await bareClient.SendAsync(request, cancellationToken).ConfigureAwait(false);
            
            if (!response.IsSuccessStatusCode)
            {
                _logger.LogWarning("Refresh token request failed with status {StatusCode}", response.StatusCode);
                return false;
            }

            var responseJson = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            var doc = JsonDocument.Parse(responseJson);

            if (doc.RootElement.TryGetProperty("errors", out var errors) && errors.GetArrayLength() > 0)
            {
                var errorMsg = errors[0].GetProperty("message").GetString();
                _logger.LogWarning("Refresh token GraphQL error: {Error}", errorMsg);
                return false;
            }

            if (!doc.RootElement.TryGetProperty("data", out var data) ||
                !data.TryGetProperty("refreshJwt", out var newJwtElement))
            {
                _logger.LogWarning("Unexpected refresh response structure");
                return false;
            }

            var newJwt = newJwtElement.GetString();
            if (string.IsNullOrWhiteSpace(newJwt))
            {
                _logger.LogWarning("Received empty JWT from refresh");
                return false;
            }

            await _secureStorage.SetAsync(AppConstants.JwtStorageKey, newJwt).ConfigureAwait(false);
            _logger.LogInformation("JWT successfully refreshed");
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Exception during token refresh");
            return false;
        }
    }

    private static async Task<HttpRequestMessage> CloneHttpRequestMessageAsync(HttpRequestMessage req)
    {
        var clone = new HttpRequestMessage(req.Method, req.RequestUri) { Version = req.Version };
        foreach (var header in req.Headers)
            clone.Headers.TryAddWithoutValidation(header.Key, header.Value);

        if (req.Content != null)
        {
            var bytes = await req.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
            var contentClone = new ByteArrayContent(bytes);
            foreach (var h in req.Content.Headers)
                contentClone.Headers.TryAddWithoutValidation(h.Key, h.Value);
            clone.Content = contentClone;
        }

        return clone;
    }
}
