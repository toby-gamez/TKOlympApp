using System.Net.Http;
using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Storage;
using Microsoft.Extensions.Logging;
using TkOlympApp.Helpers;

namespace TkOlympApp.Services;

public static class AuthService
{
    private static readonly ILogger _logger = LoggerService.CreateLogger("AuthService");
    private static readonly HttpClient Client;
    // Bare client without the auth delegating handler - used for internal refresh requests
    private static readonly HttpClient BareClient;

    static AuthService()
    {
        // Handler that will intercept 401/403 and attempt token refresh + retry
        var authHandler = new AuthDelegatingHandler();
        authHandler.InnerHandler = new HttpClientHandler();

        Client = new HttpClient(authHandler)
        {
            BaseAddress = new Uri(AppConstants.BaseApiUrl)
        };
        Client.DefaultRequestHeaders.Add(AppConstants.TenantHeader, AppConstants.TenantId);
        if (Client.DefaultRequestHeaders.Contains("x-tenant"))
        {
            Client.DefaultRequestHeaders.Remove("x-tenant");
        }

        // Bare client without the delegating handler to avoid recursion when refreshing tokens
        BareClient = new HttpClient(new HttpClientHandler())
        {
            BaseAddress = new Uri(AppConstants.BaseApiUrl)
        };
        BareClient.DefaultRequestHeaders.Add(AppConstants.TenantHeader, AppConstants.TenantId);
        
        _logger.LogDebug("AuthService initialized with base URL: {BaseUrl}", AppConstants.BaseApiUrl);
    }

    public static HttpClient Http => Client;

    public static async Task InitializeAsync(CancellationToken ct = default)
    {
        _logger.LogDebug("Initializing AuthService");
        var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey);
        if (!string.IsNullOrWhiteSpace(jwt))
        {
            _logger.LogInformation("Found existing JWT token, attempting refresh");
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            // Try refresh on startup if token is expired or near expiry
            try
            {
                await TryRefreshIfNeededAsync(ct);
            }
            catch (Exception ex)
            {
                // If refresh fails, ensure we clear any invalid token
                _logger.LogWarning(ex, "Token refresh failed during initialization, forcing logout");
                try 
                { 
                    await LogoutAsync(); 
                }
                catch (Exception logoutEx)
                {
                    _logger.LogError(logoutEx, "Logout failed during cleanup");
                }
            }
        }
        else
        {
            _logger.LogDebug("No existing JWT token found");
        }
    }

    public static async Task<bool> TryRefreshIfNeededAsync(CancellationToken ct = default)
    {
        var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey);
        if (string.IsNullOrWhiteSpace(jwt))
        {
            _logger.LogDebug("No JWT token to refresh");
            return false;
        }

        // If not expired (within a small leeway), keep existing token
        if (!IsJwtExpired(jwt))
        {
            _logger.LogDebug("JWT token is still valid, no refresh needed");
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            return true;
        }

        _logger.LogInformation("JWT token expired or near expiry, attempting refresh");
        try
        {
            var newJwt = await RefreshJwtAsync(ct);
            var success = !string.IsNullOrWhiteSpace(newJwt);
            if (success)
            {
                _logger.LogInformation("JWT token refreshed successfully");
            }
            else
            {
                _logger.LogWarning("JWT token refresh returned empty token");
            }
            return success;
        }
        catch (Exception ex)
        {
            // On any failure, clear stored token to force re-login
            _logger.LogError(ex, "JWT token refresh failed, clearing stored credentials");
            try 
            { 
                await LogoutAsync(); 
            }
            catch (Exception logoutEx)
            {
                _logger.LogError(logoutEx, "Logout failed during refresh error cleanup");
            }
            return false;
        }
    }

    public static async Task<string?> RefreshJwtAsync(CancellationToken ct = default)
    {
        var gql = new GraphQlRequest { Query = "query Refresh { refreshJwt }" };
        var json = JsonSerializer.Serialize(gql);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await BareClient.PostAsync("", content, ct);

        // If the server returns 401/403 or other non-success, bubble up an error
        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = true
        };

        var result = JsonSerializer.Deserialize<GraphQlResponse<RefreshJwtData>>(body, options);
        var jwt = result?.Data?.RefreshJwt;

        if (string.IsNullOrWhiteSpace(jwt))
        {
            var errMsg = result?.Errors?.FirstOrDefault()?.Message ?? "Obnovení tokenu selhalo.";
            throw new InvalidOperationException(errMsg);
        }

        await SecureStorage.SetAsync(AppConstants.JwtStorageKey, jwt);
        Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
        return jwt;
    }

    // Delegating handler that intercepts 401/403 and attempts a token refresh + one retry
    private class AuthDelegatingHandler : DelegatingHandler
    {
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            // Send original request
            var response = await base.SendAsync(request, cancellationToken).ConfigureAwait(false);

            if (response.StatusCode != HttpStatusCode.Unauthorized && response.StatusCode != HttpStatusCode.Forbidden)
                return response;

            _logger.LogWarning("Received {StatusCode} response, attempting token refresh", response.StatusCode);

            // Attempt to refresh token
            var refreshed = false;
            try
            {
                refreshed = await AuthService.TryRefreshIfNeededAsync(cancellationToken).ConfigureAwait(false);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Token refresh attempt failed in delegating handler");
                refreshed = false;
            }

            if (!refreshed)
            {
                _logger.LogWarning("Token refresh unsuccessful, logging out");
                try 
                { 
                    await AuthService.LogoutAsync().ConfigureAwait(false); 
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Logout failed in delegating handler");
                }
                return response;
            }

            _logger.LogInformation("Token refreshed, retrying original request");

            // Clone request for retry since HttpRequestMessage can only be sent once
            var retry = await CloneHttpRequestMessageAsync(request).ConfigureAwait(false);
            // Ensure Authorization header is set from the refreshed token
            var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey).ConfigureAwait(false);
            if (!string.IsNullOrWhiteSpace(jwt))
            {
                retry.Headers.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            }

            var retryResponse = await base.SendAsync(retry, cancellationToken).ConfigureAwait(false);
            if (retryResponse.StatusCode == HttpStatusCode.Unauthorized || retryResponse.StatusCode == HttpStatusCode.Forbidden)
            {
                _logger.LogError("Retry after token refresh still returned {StatusCode}, forcing logout", retryResponse.StatusCode);
                try 
                { 
                    await AuthService.LogoutAsync().ConfigureAwait(false); 
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Logout failed after retry failure");
                }
            }

            return retryResponse;
        }

        private static async Task<HttpRequestMessage> CloneHttpRequestMessageAsync(HttpRequestMessage req)
        {
            var clone = new HttpRequestMessage(req.Method, req.RequestUri)
            {
                Version = req.Version
            };

            // Copy headers
            foreach (var header in req.Headers)
                clone.Headers.TryAddWithoutValidation(header.Key, header.Value);

            // Copy content (if any)
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

    private static bool IsJwtExpired(string jwt, int leewaySeconds = 60)
    {
        try
        {
            var parts = jwt.Split('.');
            if (parts.Length < 2) return true;
            var payload = parts[1];
            // base64url -> base64
            var padded = payload.Replace('-', '+').Replace('_', '/');
            switch (padded.Length % 4)
            {
                case 2: padded += "=="; break;
                case 3: padded += "="; break;
                case 1: padded += "==="; break;
            }
            var bytes = Convert.FromBase64String(padded);
            var json = Encoding.UTF8.GetString(bytes);
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.TryGetProperty("exp", out var expEl) && expEl.ValueKind == JsonValueKind.Number)
            {
                var exp = expEl.GetInt64();
                var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                return exp <= now + leewaySeconds;
            }
            // If no exp claim, consider expired
            return true;
        }
        catch
        {
            return true;
        }
    }

    public static async Task<bool> HasTokenAsync(CancellationToken ct = default)
    {
        var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey);
        return !string.IsNullOrWhiteSpace(jwt);
    }

    public static async Task<string?> LoginAsync(string login, string passwd, CancellationToken ct = default)
    {
        _logger.LogInformation("Attempting login for user: {Login}", login);
        
        // GraphQL payload using variables
        var gql = new GraphQlRequest
        {
            Query = "mutation($login: String!, $passwd: String!) { login(input: {login: $login, passwd: $passwd}) { result { jwt } } }",
            Variables = new Dictionary<string, object>
            {
                {"login", login},
                {"passwd", passwd}
            }
        };

        var json = JsonSerializer.Serialize(gql);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await Client.PostAsync("", content, ct);
        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            PropertyNameCaseInsensitive = true
        };

        var result = JsonSerializer.Deserialize<GraphQlResponse<LoginData>>(body, options);
        var jwt = result?.Data?.Login?.Result?.Jwt;

        if (string.IsNullOrWhiteSpace(jwt))
        {
            // Try extract first error message if available
            var errMsg = result?.Errors?.FirstOrDefault()?.Message ?? "Neplatné přihlašovací údaje.";
            _logger.LogWarning("Login failed for user {Login}: {Error}", login, errMsg);
            throw new InvalidOperationException(errMsg);
        }

        _logger.LogInformation("Login successful for user: {Login}", login);

        // Persist and set default auth header for future requests
        await SecureStorage.SetAsync(AppConstants.JwtStorageKey, jwt);
        Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
        
        // After login, try to fetch the user's person id (userProxiesList.person.id) and persist it
        try
        {
            _logger.LogDebug("Fetching user person ID");
            var gqlReq2 = new GraphQlRequest { Query = "query { userProxiesList { person { id } } }" };
            var json2 = JsonSerializer.Serialize(gqlReq2);
            using var content2 = new StringContent(json2, Encoding.UTF8, "application/json");
            using var resp2 = await Client.PostAsync("", content2, ct);
            resp2.EnsureSuccessStatusCode();

            var body2 = await resp2.Content.ReadAsStringAsync(ct);
            var options2 = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
            var parsed = JsonSerializer.Deserialize<GraphQlResponse<UserProxiesData>>(body2, options2);
            var id = parsed?.Data?.UserProxiesList?.FirstOrDefault()?.Person?.Id;
            if (!string.IsNullOrWhiteSpace(id))
            {
                await UserService.SetCurrentPersonIdAsync(id);
                _logger.LogDebug("User person ID set: {PersonId}", id);
            }
            else
            {
                _logger.LogWarning("No person ID found for user");
            }
        }
        catch (Exception ex)
        {
            // non-critical: ignore failures fetching/persisting person id
            _logger.LogWarning(ex, "Failed to fetch or persist user person ID (non-critical)");
        }
        return jwt;
    }

    public static async Task LogoutAsync(CancellationToken ct = default)
    {
        _logger.LogInformation("Logging out user");
        try
        {
            // Persist empty token (best-effort) and clear auth header
            try 
            { 
                await SecureStorage.SetAsync(AppConstants.JwtStorageKey, string.Empty); 
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to clear JWT token from secure storage");
            }
            
            Client.DefaultRequestHeaders.Authorization = null;

            // Clear persisted person id as well
            try 
            { 
                await UserService.SetCurrentPersonIdAsync(null, ct); 
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to clear person ID");
            }
            
            _logger.LogInformation("Logout completed successfully");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error during logout");
        }
    }

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
}
