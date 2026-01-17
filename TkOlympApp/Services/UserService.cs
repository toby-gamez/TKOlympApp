using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Numerics;
using Microsoft.Maui.Storage;

namespace TkOlympApp.Services;

public static class UserService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    // Person id derived from userProxiesList.person.id — can be set by UI after fetching
    public static string? CurrentPersonId { get; private set; }
    
    // Cohort id — can be set by UI after fetching
    public static string? CurrentCohortId { get; private set; }

    public static void SetCurrentPersonId(string? personId)
    {
        CurrentPersonId = personId;
        _ = SetCurrentPersonIdAsync(personId);
    }

    public static async Task SetCurrentPersonIdAsync(string? personId)
    {
        CurrentPersonId = personId;
        try
        {
            if (string.IsNullOrEmpty(personId))
            {
                await SecureStorage.SetAsync("currentPersonId", string.Empty);
            }
            else
            {
                await SecureStorage.SetAsync("currentPersonId", personId);
            }
        }
        catch
        {
            // Ignore secure storage errors to avoid crashing UI; persistence is best-effort.
        }
    }
    
    public static void SetCurrentCohortId(string? cohortId)
    {
        CurrentCohortId = cohortId;
        _ = SetCurrentCohortIdAsync(cohortId);
    }

    public static async Task SetCurrentCohortIdAsync(string? cohortId)
    {
        CurrentCohortId = cohortId;
        try
        {
            if (string.IsNullOrEmpty(cohortId))
            {
                await SecureStorage.SetAsync("currentCohortId", string.Empty);
            }
            else
            {
                await SecureStorage.SetAsync("currentCohortId", cohortId);
            }
        }
        catch
        {
            // Ignore secure storage errors to avoid crashing UI; persistence is best-effort.
        }
    }

    public static async Task InitializeAsync(CancellationToken ct = default)
    {
        try
        {
            var storedPerson = await SecureStorage.GetAsync("currentPersonId");
            if (!string.IsNullOrWhiteSpace(storedPerson)) CurrentPersonId = storedPerson;
            
            var storedCohort = await SecureStorage.GetAsync("currentCohortId");
            if (!string.IsNullOrWhiteSpace(storedCohort)) CurrentCohortId = storedCohort;
        }
        catch
        {
            // ignore
        }
    }

        public static async Task<CurrentUser?> GetCurrentUserAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = "query MyQuery($versionId: String!) { getCurrentUser(versionId: $versionId) { uEmail uJmeno uLogin createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }",
            Variables = new Dictionary<string, object> { { "versionId", "" } }
        };

        var json = JsonSerializer.Serialize(query, Options);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var errorBody = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
        }

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<CurrentUserData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }
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
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }
    
    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class CurrentUserData
    {
        [JsonPropertyName("getCurrentUser")] public CurrentUser? GetCurrentUser { get; set; }
    }

    public sealed record CurrentUser(
        string UEmail,
        string? UJmeno,
        string ULogin,
        DateTime CreatedAt,
        long Id,
        DateTime? LastActiveAt,
        DateTime? LastLogin,
        long TenantId,
        string? UPrijmeni,
        DateTime UpdatedAt
    );
    
    public sealed record CoupleInfo(string ManName, string WomanName, string? Id);
    
    public sealed record CohortInfo(string? Id, string? ColorRgb, string Name);

        public static async Task<List<CoupleInfo>> GetActiveCouplesFromUsersAsync(CancellationToken ct = default)
    {
                var query = new GraphQlRequest
                {
                        Query = @"query von {
    users {
        nodes {
            userProxiesList {
                person {
                    activeCouplesList {
                        id
                        man { firstName lastName }
                        woman { firstName lastName }
                    }
                    cohortMembershipsList {
                        cohort {
                            id
                            colorRgb
                            name
                        }
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
                if (!resp.IsSuccessStatusCode)
                {
                    var errorBody = await resp.Content.ReadAsStringAsync(ct);
                    throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
                }

                var body = await resp.Content.ReadAsStringAsync(ct);
                var data = JsonSerializer.Deserialize<GraphQlResponse<UsersData>>(body, Options);
                if (data?.Errors != null && data.Errors.Count > 0)
                {
                    var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
                    throw new InvalidOperationException(msg);
                }

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
    
    public static async Task<List<CohortInfo>> GetCohortsFromUsersAsync(CancellationToken ct = default)
    {
        var query = new GraphQlRequest
        {
            Query = @"query bon {
    users {
        nodes {
            userProxiesList {
                person {
                    cohortMembershipsList {
                        cohort {
                            id
                            colorRgb
                            name
                        }
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
        if (!resp.IsSuccessStatusCode)
        {
            var errorBody = await resp.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
        }

        var body = await resp.Content.ReadAsStringAsync(ct);
        var data = JsonSerializer.Deserialize<GraphQlResponse<UsersData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
            throw new InvalidOperationException(msg);
        }

        var result = new List<CohortInfo>();
        if (data?.Data?.Users?.Nodes != null)
        {
            foreach (var node in data.Data.Users.Nodes)
            {
                if (node?.UserProxiesList == null) continue;
                foreach (var proxy in node.UserProxiesList)
                {
                    var person = proxy?.Person;
                    if (person?.CohortMembershipsList == null) continue;
                    foreach (var membership in person.CohortMembershipsList)
                    {
                        var cohort = membership?.Cohort;
                        if (cohort == null || string.IsNullOrWhiteSpace(cohort.Name)) continue;
                        
                        // Avoid duplicates
                        if (!result.Any(c => c.Id == cohort.Id))
                        {
                            result.Add(new CohortInfo(cohort.Id, cohort.ColorRgb, cohort.Name));
                        }
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
        [JsonPropertyName("cohortMembershipsList")] public CohortMembership[]? CohortMembershipsList { get; set; }
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
    
    private sealed class CohortMembership
    {
        [JsonPropertyName("cohort")] public Cohort? Cohort { get; set; }
    }
    
    private sealed class Cohort
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("colorRgb")] public string? ColorRgb { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
    }
}
