using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
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
    }

    public static HttpClient Http => Client;

    public static async Task InitializeAsync()
    {
        var jwt = await SecureStorage.GetAsync("jwt");
        if (!string.IsNullOrWhiteSpace(jwt))
        {
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
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
        return jwt;
    }

    public static Task LogoutAsync()
    {
        SecureStorage.Remove("jwt");
        Client.DefaultRequestHeaders.Authorization = null;
        return Task.CompletedTask;
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
}
