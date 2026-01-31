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
            var query = "query MyQuery($versionId: String!) { getCurrentUser(versionId: $versionId) { uEmail uJmeno uLogin createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }";
            var variables = new Dictionary<string, object> { { "versionId", "" } };

            var data = await GraphQlClient.PostAsync<CurrentUserData>(query, variables, ct);
            return data?.GetCurrentUser;
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
                var query = @"query von {
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
}";

                var data = await GraphQlClient.PostAsync<UsersData>(query, null, ct);

                var result = new List<CoupleInfo>();
                if (data?.Users?.Nodes != null)
                {
                    foreach (var node in data.Users.Nodes)
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
        var query = @"query bon {
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
}";

        var data = await GraphQlClient.PostAsync<UsersData>(query, null, ct);
        var result = new List<CohortInfo>();
        if (data?.Users?.Nodes != null)
        {
            foreach (var node in data.Users.Nodes)
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
