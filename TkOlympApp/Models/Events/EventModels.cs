using System.Text.Json.Serialization;

namespace TkOlympApp.Models.Events;

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

public sealed record EventTrainer(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
    [property: JsonPropertyName("person")] Person? Person,
    [property: JsonPropertyName("firstName")] string? FirstName,
    [property: JsonPropertyName("lastName")] string? LastName,
    [property: JsonPropertyName("prefixTitle")] string? PrefixTitle,
    [property: JsonPropertyName("suffixTitle")] string? SuffixTitle,
    [property: JsonPropertyName("personId")] long? PersonId,
    [property: JsonPropertyName("lessonsOffered")] int? LessonsOffered,
    [property: JsonPropertyName("lessonsRemaining")] int? LessonsRemaining
);

public sealed record EventInstance(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("isCancelled")] bool IsCancelled,
    [property: JsonPropertyName("locationId")] long? LocationId,
    [property: JsonPropertyName("since")] DateTime? Since,
    [property: JsonPropertyName("until")] DateTime? Until,
    [property: JsonPropertyName("updatedAt")] DateTime UpdatedAt,
    [property: JsonPropertyName("event")] EventInfo? Event
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
