namespace TkOlympApp.Models.Events;

public sealed record EventRegistrationRequest(
    string? PersonId,
    string? CoupleId,
    IReadOnlyList<EventRegistrationLessonRequest> Lessons);

public sealed record EventRegistrationLessonRequest(
    string TrainerId,
    int LessonCount);
