using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class EventService
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };
    public static async Task<EventDetails?> GetEventAsync(long id, CancellationToken ct = default)
    {
            var query = new GraphQlRequest
        {
            // Request person names and couple names (including first name) and instance trainers for registrations
            Query = "query MyQuery($id: BigInt!) { event(id: $id) { capacity createdAt description eventRegistrations { totalCount nodes { couple { active status man { name firstName lastName eventInstanceTrainersList { name lessonPrice { amount currency } } } woman { name firstName lastName eventInstanceTrainersList { name lessonPrice { amount currency } } } } eventLessonDemandsByRegistrationIdList { lessonCount trainer { name } } person { firstName lastName } } } isPublic isRegistrationOpen isVisible __typename name summary locationText eventTrainersList { lessonPrice { amount currency } name } updatedAt since until } }",
            Variables = new Dictionary<string, object> { { "id", id } }
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
        var data = JsonSerializer.Deserialize<GraphQlResponse<EventData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? "Neznámá chyba GraphQL.";
            throw new InvalidOperationException(msg);
        }
        return data?.Data?.Event;
    }

    private sealed class EventData
    {
        [JsonPropertyName("event")] public EventDetails? Event { get; set; }
    }

    public sealed record EventDetails(
        [property: JsonPropertyName("capacity")] int? Capacity,
        [property: JsonPropertyName("createdAt")] DateTime CreatedAt,
        [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("description")] string? Description,
        [property: JsonPropertyName("eventRegistrations")] EventRegistrations? EventRegistrations,
        [property: JsonPropertyName("isPublic")] bool IsPublic,
        [property: JsonPropertyName("isRegistrationOpen")] bool IsRegistrationOpen,
        [property: JsonPropertyName("isVisible")] bool IsVisible,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("summary")] string? Summary,
        [property: JsonPropertyName("locationText")] string? LocationText,
        [property: JsonPropertyName("eventTrainersList")] List<EventTrainer>? EventTrainersList
    );

    public sealed record EventRegistrations(
        [property: JsonPropertyName("totalCount")] int TotalCount,
        [property: JsonPropertyName("nodes")] List<EventRegistrationNode> Nodes
    );

    public sealed record EventRegistrationNode(
        [property: JsonPropertyName("couple")] RegistrationCouple? Couple,
        [property: JsonPropertyName("eventLessonDemandsByRegistrationIdList")] List<EventLessonDemand>? EventLessonDemandsByRegistrationIdList,
        [property: JsonPropertyName("person")] Person? Person
    );

    public sealed record RegistrationCouple(
        [property: JsonPropertyName("active")] bool Active,
        [property: JsonPropertyName("man")] RegistrationPerson? Man,
        [property: JsonPropertyName("woman")] RegistrationPerson? Woman,
        [property: JsonPropertyName("status")] string? Status
    );

    public sealed record RegistrationPerson(
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("eventInstanceTrainersList")] List<EventInstanceTrainer>? EventInstanceTrainersList
    );

    public sealed record EventInstanceTrainer(
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("lessonPrice")] Money? LessonPrice
    );

    public sealed record EventLessonDemand(
        [property: JsonPropertyName("lessonCount")] int LessonCount,
        [property: JsonPropertyName("trainer")] Trainer? Trainer
    );

    public sealed record Trainer(
        [property: JsonPropertyName("name")] string? Name
    );

    // Trainers at the event level
    public sealed record EventTrainer(
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("lessonPrice")] Money? LessonPrice,
        [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt
    );
    

    public static async Task<List<EventInstance>> GetMyEventInstancesForRangeAsync(
        DateTime startRange,
        DateTime endRange,
        bool onlyMine = true,
        int? first = null,
        int? offset = null,
        string? onlyType = null,
        CancellationToken ct = default)
    {
        var variables = new Dictionary<string, object>
        {
            {"startRange", startRange.ToString("o")},
            {"endRange", endRange.ToString("o")},
            {"onlyMine", onlyMine}
        };

        if (first.HasValue) variables["first"] = first.Value;
        if (offset.HasValue) variables["offset"] = offset.Value;
        if (!string.IsNullOrEmpty(onlyType)) variables["onlyType"] = onlyType;

            var query = new GraphQlRequest
        {
            Query = "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $onlyMine: Boolean, $first: Int, $offset: Int, $onlyType: EventType) { myEventInstancesForRangeList(startRange: $startRange, endRange: $endRange, onlyMine: $onlyMine, first: $first, offset: $offset, onlyType: $onlyType) { id isCancelled locationId since until updatedAt event { id description name locationText isRegistrationOpen isPublic guestPrice { amount currency } eventTrainersList { name } eventRegistrationsList { person { name } couple { man { lastName } woman { lastName } } } } tenant { couplesList { active man { firstName name lastName } woman { name lastName firstName } } } } }",
            Variables = variables
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
        var data = JsonSerializer.Deserialize<GraphQlResponse<MyEventInstancesData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? "Neznámá chyba GraphQL.";
            throw new InvalidOperationException(msg);
        }
        return data?.Data?.MyEventInstancesForRangeList ?? new List<EventInstance>();
    }

    public static async Task<List<EventInstance>> GetEventInstancesForRangeListAsync(
        DateTime startRange,
        DateTime endRange,
        int? first = null,
        int? offset = null,
        string? onlyType = null,
        CancellationToken ct = default)
    {
        var variables = new Dictionary<string, object>
        {
            {"startRange", startRange.ToString("o")},
            {"endRange", endRange.ToString("o")}
        };

        if (first.HasValue) variables["first"] = first.Value;
        if (offset.HasValue) variables["offset"] = offset.Value;
        if (!string.IsNullOrEmpty(onlyType)) variables["onlyType"] = onlyType;

        var query = new GraphQlRequest
        {
            Query = "query MyQuery($startRange: Datetime!, $endRange: Datetime!) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange) { id event { id name locationText eventTrainersList { name } } since until isCancelled } }",
            Variables = variables
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
        var data = JsonSerializer.Deserialize<GraphQlResponse<EventInstancesForRangeData>>(body, Options);
        if (data?.Errors != null && data.Errors.Count > 0)
        {
            var msg = data.Errors[0].Message ?? "Neznámá chyba GraphQL.";
            throw new InvalidOperationException(msg);
        }

        var list = data?.Data?.EventInstancesForRangeList ?? new List<MinimalEventInstance>();
        // Map minimal instances to the full EventInstance shape expected by the app
        var result = new List<EventInstance>(list.Count);
        foreach (var mi in list)
        {
            var updatedAt = mi.UpdatedAt ?? DateTime.MinValue;
            result.Add(new EventInstance(
                mi.Id,
                mi.IsCancelled,
                mi.LocationId,
                mi.Since,
                mi.Until,
                updatedAt,
                mi.Tenant,
                mi.Event
            ));
        }

        return result;
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

    private sealed class MyEventInstancesData
    {
        [JsonPropertyName("myEventInstancesForRangeList")] public List<EventInstance>? MyEventInstancesForRangeList { get; set; }
    }

    private sealed class EventInstancesForRangeData
    {
        [JsonPropertyName("eventInstancesForRangeList")] public List<MinimalEventInstance>? EventInstancesForRangeList { get; set; }
    }

    private sealed class MinimalEventInstance
    {
        [JsonPropertyName("id")] public long Id { get; set; }
        [JsonPropertyName("isCancelled")] public bool IsCancelled { get; set; }
        [JsonPropertyName("locationId")] public long? LocationId { get; set; }
        [JsonPropertyName("since")] public DateTime? Since { get; set; }
        [JsonPropertyName("until")] public DateTime? Until { get; set; }
        [JsonPropertyName("updatedAt")] public DateTime? UpdatedAt { get; set; }
        [JsonPropertyName("tenant")] public Tenant? Tenant { get; set; }
        [JsonPropertyName("event")] public EventInfo? Event { get; set; }
    }

    public sealed record EventInstance(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("isCancelled")] bool IsCancelled,
        [property: JsonPropertyName("locationId")] long? LocationId,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("updatedAt")] DateTime UpdatedAt,
        [property: JsonPropertyName("tenant")] Tenant? Tenant,
        [property: JsonPropertyName("event")] EventInfo? Event
    );

    public sealed record Tenant(
        [property: JsonPropertyName("couplesList")] List<Couple> CouplesList
    );

    public sealed record Couple(
        [property: JsonPropertyName("active")] bool Active,
        [property: JsonPropertyName("man")] Person? Man,
        [property: JsonPropertyName("woman")] Person? Woman
    );

    public sealed record Person(
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("lastName")] string? LastName
    );

    public sealed record EventInfo(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("description")] string? Description,
        [property: JsonPropertyName("name")] string Name,
        [property: JsonPropertyName("locationText")] string? LocationText,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("isRegistrationOpen")] bool IsRegistrationOpen,
        [property: JsonPropertyName("isPublic")] bool IsPublic,
        [property: JsonPropertyName("guestPrice")] Money? GuestPrice,
        [property: JsonPropertyName("eventTrainersList")] List<EventTrainer>? EventTrainersList,
        [property: JsonPropertyName("eventRegistrationsList")] List<EventRegistrationShort>? EventRegistrationsList
    );

    public sealed record EventRegistrationShort(
        [property: JsonPropertyName("person")] EventPersonShort? Person,
        [property: JsonPropertyName("couple")] EventCoupleShort? Couple
    );

    public sealed record EventPersonShort(
        [property: JsonPropertyName("name")] string? Name
    );

    public sealed record EventCoupleShort(
        [property: JsonPropertyName("man")] EventCoupleMemberShort? Man,
        [property: JsonPropertyName("woman")] EventCoupleMemberShort? Woman
    );

    public sealed record EventCoupleMemberShort(
        [property: JsonPropertyName("lastName")] string? LastName
    );

    public sealed record Money(
        [property: JsonPropertyName("amount")] decimal Amount,
        [property: JsonPropertyName("currency")] string Currency
    );
}
