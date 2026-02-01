using System.Net.Http;
using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Storage;
using TkOlympApp.Helpers;

namespace TkOlympApp.Services;

public static class AuthService
{
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
    }

    public static HttpClient Http => Client;

    public static async Task InitializeAsync()
    {
        var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey);
        if (!string.IsNullOrWhiteSpace(jwt))
        {
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            // Try refresh on startup if token is expired or near expiry
            try
            {
                await TryRefreshIfNeededAsync();
            }
            catch
            {
                // If refresh fails, ensure we clear any invalid token
                try { await LogoutAsync(); } catch { }
            }
        }
    }

    public static async Task<bool> TryRefreshIfNeededAsync(CancellationToken ct = default)
    {
        var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey);
        if (string.IsNullOrWhiteSpace(jwt))
            return false;

        // If not expired (within a small leeway), keep existing token
        if (!IsJwtExpired(jwt))
        {
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
            return true;
        }

        try
        {
            var newJwt = await RefreshJwtAsync(ct);
            return !string.IsNullOrWhiteSpace(newJwt);
        }
        catch
        {
            // On any failure, clear stored token to force re-login
            try { await LogoutAsync(); } catch { }
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

            // Attempt to refresh token
            var refreshed = false;
            try
            {
                refreshed = await AuthService.TryRefreshIfNeededAsync(cancellationToken).ConfigureAwait(false);
            }
            catch
            {
                refreshed = false;
            }

            if (!refreshed)
            {
                try { await AuthService.LogoutAsync().ConfigureAwait(false); } catch { }
                return response;
            }

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
                try { await AuthService.LogoutAsync().ConfigureAwait(false); } catch { }
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

    public static async Task<bool> HasTokenAsync()
    {
        var jwt = await SecureStorage.GetAsync(AppConstants.JwtStorageKey);
        return !string.IsNullOrWhiteSpace(jwt);
    }

    public static async Task<string?> LoginAsync(string login, string passwd, CancellationToken ct = default)
    {
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
            throw new InvalidOperationException(errMsg);
        }

        // Persist and set default auth header for future requests
        await SecureStorage.SetAsync(AppConstants.JwtStorageKey, jwt);
        Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
        // After login, try to fetch the user's person id (userProxiesList.person.id) and persist it
        try
        {
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
            }
        }
        catch
        {
            // non-critical: ignore failures fetching/persisting person id
        }
        return jwt;
    }

    public static async Task LogoutAsync()
    {
        try
        {
            // Persist empty token (best-effort) and clear auth header
            try { await SecureStorage.SetAsync(AppConstants.JwtStorageKey, string.Empty); } catch { }
            Client.DefaultRequestHeaders.Authorization = null;

            // Clear persisted person id as well
            try { await UserService.SetCurrentPersonIdAsync(null); } catch { }
        }
        catch
        {
            // ignore
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
