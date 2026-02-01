using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Numerics;
using Microsoft.Maui.Storage;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class UserService
{
    private static IUserService? _instance;

    private static IUserService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<IUserService>();
            return _instance;
        }
    }

    internal static void SetInstance(IUserService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };

    // Person id derived from userProxiesList.person.id — can be set by UI after fetching
    public static string? CurrentPersonId => Instance.CurrentPersonId;

    // Cohort id — can be set by UI after fetching
    public static string? CurrentCohortId => Instance.CurrentCohortId;

    public static void SetCurrentPersonId(string? personId)
    {
        Instance.SetCurrentPersonId(personId);
    }

    public static async Task SetCurrentPersonIdAsync(string? personId, CancellationToken ct = default)
    {
        await Instance.SetCurrentPersonIdAsync(personId, ct);
    }
    
    public static void SetCurrentCohortId(string? cohortId)
    {
        Instance.SetCurrentCohortId(cohortId);
    }

    public static async Task SetCurrentCohortIdAsync(string? cohortId, CancellationToken ct = default)
    {
        await Instance.SetCurrentCohortIdAsync(cohortId, ct);
    }

    public static async Task InitializeAsync(CancellationToken ct = default)
    {
        await Instance.InitializeAsync(ct);
    }

    public static Task<CurrentUser?> GetCurrentUserAsync(CancellationToken ct = default)
        => Instance.GetCurrentUserAsync(ct);

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

    public static Task<List<CoupleInfo>> GetActiveCouplesFromUsersAsync(CancellationToken ct = default)
        => Instance.GetActiveCouplesFromUsersAsync(ct);
    
    public static Task<List<CohortInfo>> GetCohortsFromUsersAsync(CancellationToken ct = default)
        => Instance.GetCohortsFromUsersAsync(ct);

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
