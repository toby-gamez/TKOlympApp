using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Numerics;

namespace TkOlympApp.Services;

public static class UserService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

        public static async Task<CurrentUser?> GetCurrentUserAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = "query MyQuery($versionId: String!) { getCurrentUser(versionId: $versionId) { uEmail uJmeno uLogin uCreatedAt createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }",
            Variables = new Dictionary<string, object> { { "versionId", "" } }
        };

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        resp.EnsureSuccessStatusCode();

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<CurrentUserData>>(body, Options);
        return data?.Data?.GetCurrentUser;
    }

    private sealed class GraphQlRequest
    {
        [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
        [JsonPropertyName("variables")] public Dictionary<string, object>? Variables { get; set; }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
    }

    private sealed class CurrentUserData
    {
        [JsonPropertyName("getCurrentUser")] public CurrentUser? GetCurrentUser { get; set; }
    }

    public sealed record CurrentUser(
        string UEmail,
        string? UJmeno,
        string ULogin,
        DateTime UCreatedAt,
        DateTime? CreatedAt,
        long Id,
        DateTime? LastActiveAt,
        DateTime? LastLogin,
        long TenantId,
        string? UPrijmeni,
        DateTime UpdatedAt
    );
        public sealed record CoupleInfo(string ManName, string WomanName, string? Id);

        public static async Task<List<CoupleInfo>> GetActiveCouplesFromUsersAsync(CancellationToken ct = default)
    {
                var query = new GraphQlRequest
                {
                        Query = @"query {
    users {
        nodes {
            userProxiesList {
                person {
                    activeCouplesList {
                        id
                        man { firstName lastName }
                        woman { firstName lastName }
                    }
                }
            }
        }
    }
}"
                };

                var json = JsonSerializer.Serialize(query, Options);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await AuthService.Http.PostAsync("", content, ct);
                resp.EnsureSuccessStatusCode();

                var body = await resp.Content.ReadAsStringAsync(ct);
                var data = JsonSerializer.Deserialize<GraphQlResponse<UsersData>>(body, Options);

                var result = new List<CoupleInfo>();
                if (data?.Data?.Users?.Nodes != null)
                {
                        foreach (var node in data.Data.Users.Nodes)
                        {
                                if (node?.UserProxiesList == null) continue;
                                foreach (var proxy in node.UserProxiesList)
                                {
                                        var person = proxy?.Person;
                                        if (person?.ActiveCouplesList == null) continue;
                                        foreach (var c in person.ActiveCouplesList)
                                        {
                                                if (c == null) continue;
                                            var manFirst = c.Man?.FirstName?.Trim();
                                            var manLast = c.Man?.LastName?.Trim();
                                            var womanFirst = c.Woman?.FirstName?.Trim();
                                            var womanLast = c.Woman?.LastName?.Trim();
                                            var coupleId = c.Id;
                                                var manName = string.Join(" ", new[] { manFirst, manLast }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
                                                var womanName = string.Join(" ", new[] { womanFirst, womanLast }.Where(s => !string.IsNullOrWhiteSpace(s))).Trim();
                                            if (string.IsNullOrEmpty(manName) && string.IsNullOrEmpty(womanName)) continue;
                                            result.Add(new CoupleInfo(manName, womanName, coupleId));
                                        }
                                }
                        }
                }

                return result;
    }

    private sealed class UsersData
    {
        [JsonPropertyName("users")] public UsersWrapper? Users { get; set; }
    }

    private sealed class UsersWrapper
    {
        [JsonPropertyName("nodes")] public UserNode[]? Nodes { get; set; }
    }

    private sealed class UserNode
    {
        [JsonPropertyName("userProxiesList")] public UserProxy[]? UserProxiesList { get; set; }
    }

    private sealed class UserProxy
    {
        [JsonPropertyName("person")] public Person? Person { get; set; }
    }

    private sealed class Person
    {
        [JsonPropertyName("activeCouplesList")] public ActiveCouple[]? ActiveCouplesList { get; set; }
    }

    private sealed class ActiveCouple
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("man")] public PersonReference? Man { get; set; }
        [JsonPropertyName("woman")] public PersonReference? Woman { get; set; }
    }

    private sealed class PersonReference
    {
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
    }
}
