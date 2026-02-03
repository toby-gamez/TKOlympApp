using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;

namespace TkOlympApp.ViewModels;

public sealed class PersonCohortItem
{
    public string Name { get; set; } = string.Empty;
    public Brush Color { get; set; } = new SolidColorBrush(Colors.LightGray);
}
