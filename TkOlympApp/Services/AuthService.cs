using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Storage;

namespace TkOlympApp.Services;

public static class AuthService
{
    private static readonly HttpClient Client;

    static AuthService()
    {
        Client = new HttpClient
        {
            BaseAddress = new Uri("https://api.rozpisovnik.cz/graphql")
        };
        Client.DefaultRequestHeaders.Add("x-tenant-id", "1");
        // Ensure no legacy x-tenant header is present
        if (Client.DefaultRequestHeaders.Contains("x-tenant"))
        {
            Client.DefaultRequestHeaders.Remove("x-tenant");
        }
    }

    public static HttpClient Http => Client;

    public static async Task InitializeAsync()
    {
        var jwt = await SecureStorage.GetAsync("jwt");
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
        var jwt = await SecureStorage.GetAsync("jwt");
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
        using var resp = await Client.PostAsync("", content, ct);

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

        await SecureStorage.SetAsync("jwt", jwt);
        Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
        return jwt;
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
        var jwt = await SecureStorage.GetAsync("jwt");
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
        await SecureStorage.SetAsync("jwt", jwt);
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
            try { await SecureStorage.SetAsync("jwt", string.Empty); } catch { }
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
