using System.Collections.ObjectModel;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.Controls;

namespace TkOlympApp.ViewModels;

public sealed class PeoplePersonItem
{
    public string? Id { get; set; }
    public string FullName { get; set; } = string.Empty;
    public string DisplayBirthDate { get; set; } = string.Empty;
    public bool HasBirthDate { get; set; }
    public bool IsBirthdayToday { get; set; }
    public Brush StrokeBrush { get; set; } = new SolidColorBrush(Colors.LightGray);
    public double StrokeThickness { get; set; } = 1;
    public Color BirthdayIconColor { get; set; } = Colors.Blue;
    public ObservableCollection<PeopleCohortDot> CohortDots { get; } = new();
}
