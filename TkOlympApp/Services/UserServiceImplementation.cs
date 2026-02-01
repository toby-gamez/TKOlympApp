using Microsoft.Maui.Storage;
using System.Text.Json.Serialization;
using TkOlympApp.Services.Abstractions;
using static TkOlympApp.Services.UserService;

namespace TkOlympApp.Services;

public sealed class UserServiceImplementation : IUserService
{
    private readonly IGraphQlClient _graphQlClient;
    private readonly ISecureStorage _secureStorage;

    public UserServiceImplementation(IGraphQlClient graphQlClient, ISecureStorage secureStorage)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
        _secureStorage = secureStorage ?? throw new ArgumentNullException(nameof(secureStorage));
    }

    public string? CurrentPersonId { get; private set; }
    public string? CurrentCohortId { get; private set; }

    public void SetCurrentPersonId(string? personId)
    {
        CurrentPersonId = personId;
        _ = SetCurrentPersonIdAsync(personId);
    }

    public async Task SetCurrentPersonIdAsync(string? personId, CancellationToken ct = default)
    {
        CurrentPersonId = personId;
        try
        {
            await _secureStorage.SetAsync("currentPersonId", string.IsNullOrEmpty(personId) ? string.Empty : personId);
        }
        catch
        {
            // best-effort
        }
    }

    public void SetCurrentCohortId(string? cohortId)
    {
        CurrentCohortId = cohortId;
        _ = SetCurrentCohortIdAsync(cohortId);
    }

    public async Task SetCurrentCohortIdAsync(string? cohortId, CancellationToken ct = default)
    {
        CurrentCohortId = cohortId;
        try
        {
            await _secureStorage.SetAsync("currentCohortId", string.IsNullOrEmpty(cohortId) ? string.Empty : cohortId);
        }
        catch
        {
            // best-effort
        }
    }

    public async Task InitializeAsync(CancellationToken ct = default)
    {
        try
        {
            var storedPerson = await _secureStorage.GetAsync("currentPersonId");
            if (!string.IsNullOrWhiteSpace(storedPerson)) CurrentPersonId = storedPerson;

            var storedCohort = await _secureStorage.GetAsync("currentCohortId");
            if (!string.IsNullOrWhiteSpace(storedCohort)) CurrentCohortId = storedCohort;
        }
        catch
        {
            // ignore
        }
    }

    public async Task<CurrentUser?> GetCurrentUserAsync(CancellationToken ct = default)
    {
        var query =
            "query MyQuery($versionId: String!) { getCurrentUser(versionId: $versionId) { uEmail uJmeno uLogin createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }";
        var variables = new Dictionary<string, object> { { "versionId", "" } };

        var data = await _graphQlClient.PostAsync<CurrentUserData>(query, variables, ct);
        return data?.GetCurrentUser;
    }

    public async Task<List<CoupleInfo>> GetActiveCouplesFromUsersAsync(CancellationToken ct = default)
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

        var data = await _graphQlClient.PostAsync<UsersData>(query, null, ct);

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

    public async Task<List<CohortInfo>> GetCohortsFromUsersAsync(CancellationToken ct = default)
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

        var data = await _graphQlClient.PostAsync<UsersData>(query, null, ct);
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

    private sealed class CurrentUserData
    {
        [JsonPropertyName("getCurrentUser")] public CurrentUser? GetCurrentUser { get; set; }
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
        [JsonPropertyName("person")] public PersonProxy? Person { get; set; }
    }

    private sealed class PersonProxy
    {
        [JsonPropertyName("activeCouplesList")] public CoupleProxy[]? ActiveCouplesList { get; set; }
        [JsonPropertyName("cohortMembershipsList")] public CohortMembershipProxy[]? CohortMembershipsList { get; set; }
    }

    private sealed class CoupleProxy
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("man")] public NameProxy? Man { get; set; }
        [JsonPropertyName("woman")] public NameProxy? Woman { get; set; }
    }

    private sealed class NameProxy
    {
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
    }

    private sealed class CohortMembershipProxy
    {
        [JsonPropertyName("cohort")] public CohortProxy? Cohort { get; set; }
    }

    private sealed class CohortProxy
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("colorRgb")] public string? ColorRgb { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
    }
}
