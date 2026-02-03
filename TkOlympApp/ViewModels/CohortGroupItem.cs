using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;

namespace TkOlympApp.ViewModels;

public sealed class CohortGroupItem
{
    public CohortGroupItem(string name, FormattedString? descriptionFormatted, Brush? colorBrush)
    {
        Name = name;
        DescriptionFormatted = descriptionFormatted;
        ColorBrush = colorBrush;
    }

    public string Name { get; }
    public FormattedString? DescriptionFormatted { get; }
    public bool HasDescription => DescriptionFormatted != null && DescriptionFormatted.Spans.Count > 0;
    public Brush? ColorBrush { get; }
    public bool HasColor => ColorBrush != null;
}
