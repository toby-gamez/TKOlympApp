using CommunityToolkit.Mvvm.ComponentModel;

namespace TkOlympApp.ViewModels;

public sealed partial class EventNotificationRuleTrainerItem : ObservableObject
{
    public EventNotificationRuleTrainerItem(string id, string label)
    {
        Id = id;
        Label = label;
    }

    public string Id { get; }
    public string Label { get; }

    [ObservableProperty]
    private bool _selected;
}
