namespace TkOlympApp.Models.Events;

public sealed record EventRegistrationScanResult(
    IReadOnlyCollection<string> RegisteredCoupleIds,
    IReadOnlyCollection<string> RegisteredPersonNames);
