using System.Diagnostics;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using TkOlympApp.Models.Events;
using TkOlympApp.Exceptions;
using TkOlympApp.Services.Abstractions;
using EventInstance = TkOlympApp.Models.Events.EventInstance;

namespace TkOlympApp.Services;

public sealed class EventServiceImplementation : IEventService
{
    private readonly IGraphQlClient _graphQlClient;
    private readonly ILogger _logger;

    public EventServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
        _logger = LoggerService.CreateLogger(nameof(EventServiceImplementation));
    }

    public string? LastEventInstancesForRangeRawJson { get; private set; }

    public async Task<EventInstanceDetails?> GetEventInstanceAsync(long instanceId, CancellationToken ct = default)
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

        var data = await _graphQlClient.PostAsync<EventInstanceData>(query, variables, ct);
        return data?.EventInstance;
    }

    public async Task<EventDetails?> GetEventAsync(long id, CancellationToken ct = default)
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
        var data = await _graphQlClient.PostAsync<EventData>(query, variables, ct);
        return data?.Event;
    }

    public async Task<List<EventInstance>> GetMyEventInstancesForRangeAsync(
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
            using (_logger.BeginOperation(
                       "GetMyEventInstancesForRange",
                       ("StartRange", startRange.ToString("o")),
                       ("EndRange", endRange.ToString("o")),
                       ("First", first),
                       ("Offset", offset),
                       ("OnlyType", onlyType)))
            {
                var variables = new Dictionary<string, object>
                {
                    { "startRange", startRange.ToString("o") },
                    { "endRange", endRange.ToString("o") },
                };

                if (first.HasValue) variables["first"] = first.Value;
                if (offset.HasValue) variables["offset"] = offset.Value;
                if (!string.IsNullOrEmpty(onlyType)) variables["onlyType"] = onlyType;

                var query =
                    "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $first: Int, $offset: Int, $onlyType: EventType) { eventInstancesForRangeList(onlyMine: true, startRange: $startRange, endRange: $endRange, first: $first, offset: $offset, onlyType: $onlyType) { id isCancelled since until updatedAt event { id description name type locationText isRegistrationOpen isPublic eventTrainersList { name } eventTargetCohortsList { cohortId cohort { id name colorRgb } } eventRegistrationsList { person { name } couple { man { lastName } woman { lastName } } } location { id name } } tenant { couplesList { man { firstName name lastName } woman { name lastName firstName } } } } }";

                _logger.LogGraphQLRequest("GetMyEventInstancesForRange", variables);

                var data = await _graphQlClient.PostAsync<MyEventInstancesData>(query, variables, ct);
                var result = data?.EventInstancesForRangeList ?? new List<EventInstance>();

                sw.Stop();
                _logger.LogGraphQLResponse("GetMyEventInstancesForRange", result, sw.Elapsed);
                _logger.LogOperationSuccess(
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
            _logger.LogOperationCancelled("GetMyEventInstancesForRange", sw.Elapsed, "User or timeout");
            throw;
        }
        catch (GraphQLException ex)
        {
            sw.Stop();
            _logger.LogOperationFailure(
                "GetMyEventInstancesForRange",
                ex,
                sw.Elapsed,
                ("StartRange", startRange.ToString("o")),
                ("EndRange", endRange.ToString("o")));
            throw;
        }
        catch (ServiceException ex)
        {
            sw.Stop();
            _logger.LogOperationFailure(
                "GetMyEventInstancesForRange",
                ex,
                sw.Elapsed,
                ("StartRange", startRange.ToString("o")),
                ("EndRange", endRange.ToString("o")),
                ("IsTransient", ex.IsTransient));
            throw;
        }
        catch (Exception ex)
        {
            sw.Stop();
            var serviceEx = new ServiceException(
                    "Neočekávaná chyba při načítání událostí",
                    ex,
                    isTransient: false)
                .WithContext("StartRange", startRange.ToString("o"))
                .WithContext("EndRange", endRange.ToString("o"));

            _logger.LogOperationFailure(
                "GetMyEventInstancesForRange",
                serviceEx,
                sw.Elapsed);

            throw serviceEx;
        }
    }

    public async Task<List<EventInstance>> GetEventInstancesForRangeListAsync(
        DateTime startRange,
        DateTime endRange,
        int? first = null,
        int? offset = null,
        string? onlyType = null,
        CancellationToken ct = default)
    {
        var variables = new Dictionary<string, object>
        {
            { "startRange", startRange.ToString("o") },
            { "endRange", endRange.ToString("o") }
        };

        if (first.HasValue) variables["first"] = first.Value;
        if (offset.HasValue) variables["offset"] = offset.Value;
        if (!string.IsNullOrEmpty(onlyType)) variables["onlyType"] = onlyType;

        var query =
            "query MyQuery($startRange: Datetime!, $endRange: Datetime!, $first: Int, $offset: Int, $onlyType: EventType) { eventInstancesForRangeList(startRange: $startRange, endRange: $endRange, first: $first, offset: $offset, onlyType: $onlyType) { id isCancelled since until updatedAt event { id description name type locationText isRegistrationOpen isPublic eventTrainersList { name } eventTargetCohortsList { cohortId cohort { id name colorRgb } } eventRegistrationsList { person { name } couple { man { lastName } woman { lastName } } } location { id name } } tenant { couplesList { man { firstName name lastName } woman { name lastName firstName } } } } }";

        var (data, raw) = await _graphQlClient.PostWithRawAsync<EventInstancesForRangeData>(query, variables, ct);
        LastEventInstancesForRangeRawJson = raw;

        var list = data?.EventInstancesForRangeList ?? new List<MinimalEventInstance>();
        var result = new List<EventInstance>(list.Count);
        foreach (var mi in list)
        {
            DateTime? sinceLocal = mi.Since.HasValue
                ? DateTime.SpecifyKind(mi.Since.Value.ToLocalTime().DateTime, DateTimeKind.Local)
                : null;
            DateTime? untilLocal = mi.Until.HasValue
                ? DateTime.SpecifyKind(mi.Until.Value.ToLocalTime().DateTime, DateTimeKind.Local)
                : null;
            DateTime updatedAt = mi.UpdatedAt.HasValue
                ? DateTime.SpecifyKind(mi.UpdatedAt.Value.ToLocalTime().DateTime, DateTimeKind.Local)
                : DateTime.MinValue;

            result.Add(new EventInstance(
                mi.Id,
                mi.IsCancelled,
                mi.LocationId,
                sinceLocal,
                untilLocal,
                updatedAt,
                mi.Tenant,
                mi.Event));
        }

        return result;
    }

    public async Task<List<EventInstance>> GetAllEventInstancesPagedAsync(
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
            var batch = await GetEventInstancesForRangeListAsync(startRange, endRange, batchSize, offset, onlyType, ct);

            if (batch.Count == 0)
            {
                hasMore = false;
            }
            else
            {
                allEvents.AddRange(batch);
                offset += batch.Count;

                if (batch.Count < batchSize)
                {
                    hasMore = false;
                }
            }
        }

        return allEvents;
    }

    private sealed class EventInstanceData
    {
        [JsonPropertyName("eventInstance")] public EventInstanceDetails? EventInstance { get; set; }
    }

    private sealed class EventData
    {
        [JsonPropertyName("event")] public EventDetails? Event { get; set; }
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
}
