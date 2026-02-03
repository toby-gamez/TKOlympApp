using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Models.Users;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public sealed class UserServiceImplementation : IUserService
{
    private readonly IGraphQlClient _graphQlClient;
    private readonly IRuntimeState _runtimeState;

    [ActivatorUtilitiesConstructor]
    public UserServiceImplementation(IGraphQlClient graphQlClient, IRuntimeState runtimeState)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
        _runtimeState = runtimeState ?? throw new ArgumentNullException(nameof(runtimeState));
    }

    // Backwards-compatible constructor for existing tests and callers that still provide ISecureStorage
    public UserServiceImplementation(IGraphQlClient graphQlClient, Microsoft.Maui.Storage.ISecureStorage secureStorage)
        : this(graphQlClient, new Services.State.RuntimeState(secureStorage))
    {
    }

    public string? CurrentPersonId => _runtimeState.CurrentPersonId;
    public string? CurrentCohortId => _runtimeState.CurrentCohortId;

    public void SetCurrentPersonId(string? personId)
    {
        _ = _runtimeState.SetCurrentPersonIdAsync(personId);
    }

    public Task SetCurrentPersonIdAsync(string? personId, CancellationToken ct = default)
    {
        return _runtimeState.SetCurrentPersonIdAsync(personId);
    }

    public void SetCurrentCohortId(string? cohortId)
    {
        _ = _runtimeState.SetCurrentCohortIdAsync(cohortId);
    }

    public Task SetCurrentCohortIdAsync(string? cohortId, CancellationToken ct = default)
    {
        return _runtimeState.SetCurrentCohortIdAsync(cohortId);
    }

    public Task InitializeAsync(CancellationToken ct = default)
    {
        return _runtimeState.InitializeAsync();
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
