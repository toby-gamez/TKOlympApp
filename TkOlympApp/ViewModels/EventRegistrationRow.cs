using System.Collections.Generic;

namespace TkOlympApp.ViewModels;

public sealed class EventRegistrationRow
{
    public string Text { get; set; } = string.Empty;
    public string? Secondary { get; set; }
    public List<string> Trainers { get; set; } = new();
    public bool HasTrainers => Trainers != null && Trainers.Count > 0;
    public bool HasSecondary => !string.IsNullOrWhiteSpace(Secondary);
    public string? CoupleId { get; set; }
    public string? PersonId { get; set; }
    public bool IsCurrentUserOrCouple { get; set; }
}
