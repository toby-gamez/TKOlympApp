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
            Query = "query MyQuery($id: BigInt!) { event(id: $id) { capacity createdAt description eventRegistrations { totalCount nodes { couple { active man { name } woman { name } status } } } isPublic isRegistrationOpen isVisible location { name } __typename name summary } }",
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
        [property: JsonPropertyName("description")] string? Description,
        [property: JsonPropertyName("eventRegistrations")] EventRegistrations? EventRegistrations,
        [property: JsonPropertyName("isPublic")] bool IsPublic,
        [property: JsonPropertyName("isRegistrationOpen")] bool IsRegistrationOpen,
        [property: JsonPropertyName("isVisible")] bool IsVisible,
        [property: JsonPropertyName("location")] EventLocation? Location,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("summary")] string? Summary
    );

    public sealed record EventRegistrations(
        [property: JsonPropertyName("totalCount")] int TotalCount,
        [property: JsonPropertyName("nodes")] List<EventRegistrationNode> Nodes
    );

    public sealed record EventRegistrationNode(
        [property: JsonPropertyName("couple")] RegistrationCouple Couple
    );

    public sealed record RegistrationCouple(
        [property: JsonPropertyName("active")] bool Active,
        [property: JsonPropertyName("man")] RegistrationPerson? Man,
        [property: JsonPropertyName("woman")] RegistrationPerson? Woman,
        [property: JsonPropertyName("status")] string? Status
    );

    public sealed record RegistrationPerson(
        [property: JsonPropertyName("name")] string? Name
    );

    public sealed record EventLocation(
        [property: JsonPropertyName("name")] string? Name
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
            Query = "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $onlyMine: Boolean, $first: Int, $offset: Int, $onlyType: EventType) { myEventInstancesForRangeList(startRange: $startRange, endRange: $endRange, onlyMine: $onlyMine, first: $first, offset: $offset, onlyType: $onlyType) { id isCancelled locationId until updatedAt tenant { couplesList { active man { firstName name lastName } woman { name lastName firstName } } } event { id description name locationText isRegistrationOpen isPublic guestPrice { amount currency } } } }",
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

    public sealed record EventInstance(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("isCancelled")] bool IsCancelled,
        [property: JsonPropertyName("locationId")] long? LocationId,
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
        [property: JsonPropertyName("isRegistrationOpen")] bool IsRegistrationOpen,
        [property: JsonPropertyName("isPublic")] bool IsPublic,
        [property: JsonPropertyName("guestPrice")] Money? GuestPrice
    );

    public sealed record Money(
        [property: JsonPropertyName("amount")] decimal Amount,
        [property: JsonPropertyName("currency")] string Currency
    );
}
