using System.Diagnostics;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using TkOlympApp.Exceptions;

namespace TkOlympApp.Services;

public static class EventService
{
    private static readonly ILogger Logger = LoggerService.CreateLogger(nameof(EventService));
    
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };
    
    // Last raw JSON response for eventInstancesForRangeList (for UI/debugging)
    public static string? LastEventInstancesForRangeRawJson { get; private set; }
    
                public static async Task<EventInstanceDetails?> GetEventInstanceAsync(long instanceId, CancellationToken ct = default)
                {
                        var query = @"query MQuery($id: BigInt!) {
    eventInstance(id: $id) {
        id
        isCancelled
        since
        until
        event {
            id
            capacity
            createdAt
            description
            isPublic
            isRegistrationOpen
            isVisible
            name
            type
            summary
            locationText
            eventTrainersList {
                id
                person {
                    prefixTitle
                    firstName
                    lastName
                    suffixTitle
                }
                updatedAt
            }
            updatedAt
            eventTargetCohortsList {
                cohortId
                cohort {
                    id
                    name
                    colorRgb
                }
            }
            eventRegistrationsList {
                couple {
                        id
                        status
                        man {
                            firstName
                            lastName
                            prefixTitle
                            suffixTitle
                        }
                        woman {
                            firstName
                            lastName
                            prefixTitle
                            suffixTitle
                        }
                    }
                    person {
                        id
                        prefixTitle
                        suffixTitle
                        lastName
                        firstName
                    }
                    eventLessonDemandsByRegistrationIdList {
                        id
                        lessonCount
                        trainer {
                            id
                            name
                        }
                    }
                }
            }
        }
    }
}";
                        var variables = new Dictionary<string, object> { { "id", instanceId } };

                        var data = await GraphQlClient.PostAsync<EventInstanceData>(query, variables, ct);
                        return data?.EventInstance;
                }

    private sealed class EventInstanceData
    {
        [JsonPropertyName("eventInstance")] public EventInstanceDetails? EventInstance { get; set; }
    }

    public sealed record EventInstanceDetails(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("isCancelled")] bool IsCancelled,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("event")] EventDetailsFromInstance? Event
    );

    public sealed record EventDetailsFromInstance(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("capacity")] int? Capacity,
        [property: JsonPropertyName("createdAt")] DateTime CreatedAt,
        [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
        [property: JsonPropertyName("description")] string? Description,
        [property: JsonPropertyName("eventRegistrations")] EventRegistrations? EventRegistrations,
        [property: JsonPropertyName("isPublic")] bool IsPublic,
        [property: JsonPropertyName("isRegistrationOpen")] bool? IsRegistrationOpen,
        [property: JsonPropertyName("isVisible")] bool IsVisible,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("type")] string? Type,
        [property: JsonPropertyName("summary")] string? Summary,
        [property: JsonPropertyName("locationText")] string? LocationText,
        [property: JsonPropertyName("eventTrainersList")] List<EventTrainer>? EventTrainersList,
        [property: JsonPropertyName("eventTargetCohortsList")] List<EventTargetCohortLink>? EventTargetCohortsList
    );
    
    public static async Task<EventDetails?> GetEventAsync(long id, CancellationToken ct = default)
    {
                var query = @"query Event($id: BigInt!) {
    event(id: $id) {
        ...EventFull
        __typename
    }
}

fragment EventInstance on EventInstance {
    id
    since
    until
    isCancelled
    __typename
}

fragment Couple on Couple {
    id
    status
    since
    until
    woman {
        id
        name
        firstName
        lastName
        __typename
    }
    man {
        id
        name
        firstName
        lastName
        __typename
    }
    __typename
}

fragment EventRegistration on EventRegistration {
    id
    note
    eventId
    personId
    person {
        id
        name
        firstName
        lastName
        __typename
    }
    coupleId
    couple {
        ...Couple
        __typename
    }
    eventLessonDemandsByRegistrationIdList {
        id
        lessonCount
        trainerId
        __typename
    }
    createdAt
    __typename
}

fragment Event on Event {
    id
    type
    summary
    description
    name
    capacity
    remainingPersonSpots
    remainingLessons
    location {
        id
        name
        __typename
    }
    locationText
    isLocked
    isVisible
    isPublic
    enableNotes
    eventTrainersList {
        id
        name
        personId
        lessonsOffered
        lessonsRemaining
        __typename
    }
    eventInstancesList(orderBy: SINCE_ASC) {
        ...EventInstance
        __typename
    }
    eventTargetCohortsList {
        id
        cohort {
            id
            name
            colorRgb
            __typename
        }
        __typename
    }
    myRegistrationsList {
        ...EventRegistration
        __typename
    }
    eventRegistrations(first: 3) {
        totalCount
        nodes {
            ...EventRegistration
            __typename
        }
        __typename
    }
    __typename
}

fragment EventExternalRegistration on EventExternalRegistration {
    id
    birthDate
    nationality
    note
    phone
    prefixTitle
    suffixTitle
    taxIdentificationNumber
    updatedAt
    createdAt
    email
    eventId
    firstName
    lastName
    __typename
}

fragment EventRegistrations on Event {
    id
    eventRegistrationsList {
        ...EventRegistration
        __typename
    }
    eventExternalRegistrationsList {
        ...EventExternalRegistration
        __typename
    }
    __typename
}

fragment EventAttendanceSummary on Event {
    id
    eventInstancesList(orderBy: SINCE_ASC) {
        ...EventInstance
        attendanceSummaryList {
            count
            status
            __typename
        }
        __typename
    }
    __typename
}

fragment EventInstanceWithTrainer on EventInstance {
    ...EventInstance
    trainersList {
        id
        personId
        person {
            id
            name
            __typename
        }
        __typename
    }
    __typename
}

fragment EventFull on Event {
    ...Event
    ...EventRegistrations
    ...EventAttendanceSummary
    eventInstancesList(orderBy: SINCE_ASC) {
        ...EventInstanceWithTrainer
        eventInstanceTrainersByInstanceIdList {
            id
            personId
            __typename
        }
        __typename
    }
    __typename
}";
        var variables = new Dictionary<string, object> { { "id", id } };

        var data = await GraphQlClient.PostAsync<EventData>(query, variables, ct);
        return data?.Event;
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
        [property: JsonPropertyName("isRegistrationOpen")] bool? IsRegistrationOpen,
        [property: JsonPropertyName("isVisible")] bool IsVisible,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("type")] string? Type,
        [property: JsonPropertyName("summary")] string? Summary,
        [property: JsonPropertyName("locationText")] string? LocationText,
        [property: JsonPropertyName("eventTrainersList")] List<EventTrainer>? EventTrainersList,
        [property: JsonPropertyName("eventTargetCohortsList")] List<EventTargetCohortLink>? EventTargetCohortsList,
        [property: JsonPropertyName("eventInstancesList")] List<EventInstanceShort>? EventInstancesList,
        [property: JsonPropertyName("remainingPersonSpots")] int? RemainingPersonSpots,
        [property: JsonPropertyName("remainingLessons")] int? RemainingLessons,
        [property: JsonPropertyName("location")] Location? Location,
        [property: JsonPropertyName("isLocked")] bool? IsLocked,
        [property: JsonPropertyName("enableNotes")] bool? EnableNotes,
        [property: JsonPropertyName("myRegistrationsList")] List<EventRegistrationNode>? MyRegistrationsList,
        [property: JsonPropertyName("eventRegistrationsList")] List<EventRegistrationNode>? EventRegistrationsList
    );

    public sealed record EventRegistrations(
        [property: JsonPropertyName("totalCount")] int TotalCount,
        [property: JsonPropertyName("nodes")] List<EventRegistrationNode> Nodes
    );

    public sealed record EventRegistrationNode(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("note")] string? Note,
        [property: JsonPropertyName("eventId")] long EventId,
        [property: JsonPropertyName("personId")] long? PersonId,
        [property: JsonPropertyName("person")] Person? Person,
        [property: JsonPropertyName("coupleId")] long? CoupleId,
        [property: JsonPropertyName("couple")] RegistrationCouple? Couple,
        [property: JsonPropertyName("eventLessonDemandsByRegistrationIdList")] List<EventLessonDemand>? EventLessonDemandsByRegistrationIdList,
        [property: JsonPropertyName("createdAt")] DateTime? CreatedAt
    );

    public sealed record RegistrationCouple(
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("status")] string? Status,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("man")] RegistrationPerson? Man,
        [property: JsonPropertyName("woman")] RegistrationPerson? Woman
    );

    public sealed record RegistrationPerson(
        [property: JsonPropertyName("id")] long? Id,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
        [property: JsonPropertyName("suffixTitle")] string? SuffixTitle
    );

    public sealed record EventLessonDemand(
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("lessonCount")] int LessonCount,
        [property: JsonPropertyName("trainerId")] long? TrainerId
    );

    public sealed record EventInstanceShort(
        [property: JsonPropertyName("id")] long? Id,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("isCancelled")] bool IsCancelled
    );

    // Trainers at the event level
    public sealed record EventTrainer(
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
        // New shape: some API responses return a nested `person` object or separate name parts
        [property: JsonPropertyName("person")] Person? Person,
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
        [property: JsonPropertyName("suffixTitle")] string? SuffixTitle,
        [property: JsonPropertyName("personId")] long? PersonId,
        [property: JsonPropertyName("lessonsOffered")] int? LessonsOffered,
        [property: JsonPropertyName("lessonsRemaining")] int? LessonsRemaining
    );

    public static string GetTrainerDisplayName(EventTrainer? t)
    {
        if (t == null) return string.Empty;
        // Prefer structured person fields when available
        try
        {
            if (t.Person != null)
            {
                var fn = t.Person.FirstName?.Trim();
                var ln = t.Person.LastName?.Trim();
                var combined = string.Join(' ', new[] { fn, ln }.Where(s => !string.IsNullOrWhiteSpace(s)));
                if (!string.IsNullOrWhiteSpace(combined)) return combined;
            }

            if (!string.IsNullOrWhiteSpace(t.FirstName) || !string.IsNullOrWhiteSpace(t.LastName))
            {
                var combined = string.Join(' ', new[] { t.FirstName?.Trim(), t.LastName?.Trim() }.Where(s => !string.IsNullOrWhiteSpace(s)));
                if (!string.IsNullOrWhiteSpace(combined)) return combined;
            }

            if (!string.IsNullOrWhiteSpace(t.Name)) return t.Name.Trim();
        }
        catch { }
        return string.Empty;
    }

    public static string GetTrainerDisplayWithPrefix(EventTrainer? t)
    {
        if (t == null) return string.Empty;
        try
        {
            var title = t.Person?.PrefixTitle ?? t.PrefixTitle;
            var name = GetTrainerDisplayName(t);
            if (string.IsNullOrWhiteSpace(name)) return string.Empty;
            if (!string.IsNullOrWhiteSpace(title)) return (title.Trim() + " " + name).Trim();
            return name;
        }
        catch { return GetTrainerDisplayName(t); }
    }
    

    /// <summary>
    /// Získá moje události v zadaném časovém rozsahu.
    /// Použití: LoadEventsAsync s proper error handling a structured logging.
    /// </summary>
    /// <param name="startRange">Začátek časového rozsahu</param>
    /// <param name="endRange">Konec časového rozsahu</param>
    /// <param name="first">Maximální počet výsledků (optional)</param>
    /// <param name="offset">Offset pro stránkování (optional)</param>
    /// <param name="onlyType">Filtr typu události (optional)</param>
    /// <param name="ct">Cancellation token pro zrušení operace</param>
    /// <returns>Seznam EventInstance nebo prázdný list při chybě</returns>
    /// <exception cref="ServiceException">Při selhání komunikace s API</exception>
    /// <exception cref="GraphQLException">Při GraphQL chybě</exception>
    /// <exception cref="OperationCanceledException">Když je operace zrušena</exception>
    public static async Task<List<EventInstance>> GetMyEventInstancesForRangeAsync(
        DateTime startRange,
        DateTime endRange,
        int? first = null,
        int? offset = null,
        string? onlyType = null,
        CancellationToken ct = default)
    {
        var sw = Stopwatch.StartNew();
        
        try
        {
            // Structured logging začátku operace
            using (Logger.BeginOperation(
                "GetMyEventInstancesForRange",
                ("StartRange", startRange.ToString("o")),
                ("EndRange", endRange.ToString("o")),
                ("First", first),
                ("Offset", offset),
                ("OnlyType", onlyType)))
            {
                var variables = new Dictionary<string, object>
                {
                    {"startRange", startRange.ToString("o")},
                    {"endRange", endRange.ToString("o")},
                };

                if (first.HasValue) variables["first"] = first.Value;
                if (offset.HasValue) variables["offset"] = offset.Value;
                if (!string.IsNullOrEmpty(onlyType)) variables["onlyType"] = onlyType;

                var query = "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $first: Int, $offset: Int, $onlyType: EventType) { eventInstancesForRangeList(onlyMine: true, startRange: $startRange, endRange: $endRange, first: $first, offset: $offset, onlyType: $onlyType) { id isCancelled since until updatedAt event { id description name type locationText isRegistrationOpen isPublic eventTrainersList { name } eventTargetCohortsList { cohortId cohort { id name colorRgb } } eventRegistrationsList { person { name } couple { man { lastName } woman { lastName } } } location { id name } } tenant { couplesList { man { firstName name lastName } woman { name lastName firstName } } } } }";

                Logger.LogGraphQLRequest("GetMyEventInstancesForRange", variables);

                var data = await GraphQlClient.PostAsync<MyEventInstancesData>(query, variables, ct);
                var result = data?.EventInstancesForRangeList ?? new List<EventInstance>();

                sw.Stop();
                Logger.LogGraphQLResponse("GetMyEventInstancesForRange", result, sw.Elapsed);
                Logger.LogOperationSuccess(
                    "GetMyEventInstancesForRange",
                    result,
                    sw.Elapsed,
                    ("EventCount", result.Count));

                return result;
            }
        }
        catch (OperationCanceledException)
        {
            sw.Stop();
            Logger.LogOperationCancelled("GetMyEventInstancesForRange", sw.Elapsed, "User or timeout");
            throw; // Re-throw pro caller
        }
        catch (GraphQLException ex)
        {
            sw.Stop();
            Logger.LogOperationFailure(
                "GetMyEventInstancesForRange",
                ex,
                sw.Elapsed,
                ("StartRange", startRange.ToString("o")),
                ("EndRange", endRange.ToString("o")));
            throw; // Re-throw pro caller aby mohl rozhodnout o retry
        }
        catch (ServiceException ex)
        {
            sw.Stop();
            Logger.LogOperationFailure(
                "GetMyEventInstancesForRange",
                ex,
                sw.Elapsed,
                ("StartRange", startRange.ToString("o")),
                ("EndRange", endRange.ToString("o")),
                ("IsTransient", ex.IsTransient));
            throw; // Re-throw pro caller
        }
        catch (Exception ex)
        {
            sw.Stop();
            // Unexpected exception - wrap do ServiceException
            var serviceEx = new ServiceException(
                "Neočekávaná chyba při načítání událostí",
                ex,
                isTransient: false)
                .WithContext("StartRange", startRange.ToString("o"))
                .WithContext("EndRange", endRange.ToString("o"));
            
            Logger.LogOperationFailure(
                "GetMyEventInstancesForRange",
                serviceEx,
                sw.Elapsed);
            
            throw serviceEx;
        }
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

        var query = "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $first: Int, $offset: Int, $onlyType: EventType) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange, first: $first, offset: $offset, onlyType: $onlyType) { id isCancelled since until updatedAt event { id description name type locationText isRegistrationOpen isPublic eventTrainersList { name } eventTargetCohortsList { cohortId cohort { id name colorRgb } } eventRegistrationsList { person { name } couple { man { lastName } woman { lastName } } } location { id name } } tenant { couplesList { man { firstName name lastName } woman { name lastName firstName } } } } }";

        var (data, raw) = await GraphQlClient.PostWithRawAsync<EventInstancesForRangeData>(query, variables, ct);
        // Store raw JSON for debugging / UI display
        LastEventInstancesForRangeRawJson = raw;
        
        var list = data?.EventInstancesForRangeList ?? new List<MinimalEventInstance>();
        // Map minimal instances to the full EventInstance shape expected by the app
        var result = new List<EventInstance>(list.Count);
        foreach (var mi in list)
        {
            // Convert DateTimeOffset values from the API into local DateTime to avoid ambiguous kinds
            DateTime? sinceLocal = mi.Since.HasValue ? DateTime.SpecifyKind(mi.Since.Value.ToLocalTime().DateTime, DateTimeKind.Local) : (DateTime?)null;
            DateTime? untilLocal = mi.Until.HasValue ? DateTime.SpecifyKind(mi.Until.Value.ToLocalTime().DateTime, DateTimeKind.Local) : (DateTime?)null;
            DateTime updatedAt = mi.UpdatedAt.HasValue ? DateTime.SpecifyKind(mi.UpdatedAt.Value.ToLocalTime().DateTime, DateTimeKind.Local) : DateTime.MinValue;
            result.Add(new EventInstance(
                mi.Id,
                mi.IsCancelled,
                mi.LocationId,
                sinceLocal,
                untilLocal,
                updatedAt,
                mi.Tenant,
                mi.Event
            ));
        }

        return result;
    }

    /// <summary>
    /// Load all event instances for a date range with automatic pagination (loads batches of 15).
    /// </summary>
    public static async Task<List<EventInstance>> GetAllEventInstancesPagedAsync(
        DateTime startRange,
        DateTime endRange,
        string? onlyType = null,
        CancellationToken ct = default)
    {
        var allEvents = new List<EventInstance>();
        int offset = 0;
        const int batchSize = 15;
        bool hasMore = true;

        while (hasMore && !ct.IsCancellationRequested)
        {
            var batch = await GetEventInstancesForRangeListAsync(
                startRange, endRange, batchSize, offset, onlyType, ct);
            
            if (batch.Count == 0)
            {
                hasMore = false;
            }
            else
            {
                allEvents.AddRange(batch);
                offset += batch.Count;
                
                // If we got fewer than batchSize, we've reached the end
                if (batch.Count < batchSize)
                {
                    hasMore = false;
                }
            }
        }

        return allEvents;
    }

    

    private sealed class MyEventInstancesData
    {
        [JsonPropertyName("eventInstancesForRangeList")] public List<EventInstance>? EventInstancesForRangeList { get; set; }
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
        [JsonPropertyName("since")] public DateTimeOffset? Since { get; set; }
        [JsonPropertyName("until")] public DateTimeOffset? Until { get; set; }
        [JsonPropertyName("updatedAt")] public DateTimeOffset? UpdatedAt { get; set; }
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
        [property: JsonPropertyName("id")] string? Id,
        [property: JsonPropertyName("firstName")] string? FirstName,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("lastName")] string? LastName,
        [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
        [property: JsonPropertyName("suffixTitle")] string? SuffixTitle
    );

    public sealed record EventInfo(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("description")] string? Description,
        [property: JsonPropertyName("name")] string Name,
        [property: JsonPropertyName("type")] string? Type,
        [property: JsonPropertyName("locationText")] string? LocationText,
        [property: JsonPropertyName("since")] DateTime? Since,
        [property: JsonPropertyName("until")] DateTime? Until,
        [property: JsonPropertyName("isRegistrationOpen")] bool? IsRegistrationOpen,
        [property: JsonPropertyName("isPublic")] bool IsPublic,
        [property: JsonPropertyName("guestPrice")] Money? GuestPrice,
        [property: JsonPropertyName("eventTrainersList")] List<EventTrainer>? EventTrainersList,
        [property: JsonPropertyName("eventTargetCohortsList")] List<EventTargetCohortLink>? EventTargetCohortsList,
        [property: JsonPropertyName("eventRegistrationsList")] List<EventRegistrationShort>? EventRegistrationsList,
        [property: JsonPropertyName("location")] Location? Location
    );

    public sealed record EventTargetCohortLink(
        [property: JsonPropertyName("cohortId")] long? CohortId,
        [property: JsonPropertyName("cohort")] CohortRef? Cohort
    );

    public sealed record CohortRef(
        [property: JsonPropertyName("id")] long? Id,
        [property: JsonPropertyName("name")] string? Name,
        [property: JsonPropertyName("colorRgb")] string? ColorRgb
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

    public sealed record Location(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("name")] string? Name
    );
}
