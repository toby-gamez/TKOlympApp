using CommunityToolkit.Mvvm.ComponentModel;

namespace TkOlympApp.ViewModels;

public sealed partial class EventNotificationRuleTypeItem : ObservableObject
{
    public EventNotificationRuleTypeItem(string code, string label)
    {
        Code = code;
        Label = label;
    }

    public string Code { get; }
    public string Label { get; }

    [ObservableProperty]
    private bool _selected;
}
