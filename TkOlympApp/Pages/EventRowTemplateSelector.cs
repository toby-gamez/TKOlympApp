using Microsoft.Maui.Controls;

namespace TkOlympApp.Pages;

/// <summary>
/// Selector for flat CalendarPage rows. Chooses the correct DataTemplate based on EventRow type.
/// </summary>
public sealed class EventRowTemplateSelector : DataTemplateSelector
{
    public DataTemplate? WeekHeaderTemplate { get; set; }
    public DataTemplate? DayHeaderTemplate { get; set; }
    public DataTemplate? SingleEventTemplate { get; set; }
    public DataTemplate? TrainerGroupHeaderTemplate { get; set; }
    public DataTemplate? TrainerDetailTemplate { get; set; }

    protected override DataTemplate? OnSelectTemplate(object item, BindableObject container)
    {
        return item switch
        {
            WeekHeaderRow => WeekHeaderTemplate,
            DayHeaderRow => DayHeaderTemplate,
            SingleEventRow => SingleEventTemplate,
            TrainerGroupHeaderRow => TrainerGroupHeaderTemplate,
            TrainerDetailRow => TrainerDetailTemplate,
            _ => null
        };
    }
}
