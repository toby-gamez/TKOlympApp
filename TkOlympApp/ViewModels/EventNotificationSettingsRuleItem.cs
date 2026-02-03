using System;
using CommunityToolkit.Mvvm.ComponentModel;
using TkOlympApp.Services;

namespace TkOlympApp.ViewModels;

public sealed partial class EventNotificationSettingsRuleItem : ObservableObject
{
    public EventNotificationSettingsRuleItem(EventNotificationSetting setting, string displayTypes, string displayTrainers)
    {
        Setting = setting;
        DisplayTypes = displayTypes;
        DisplayTrainers = displayTrainers;
        _isEnabled = setting.Enabled;
    }

    public EventNotificationSetting Setting { get; private set; }
    public string Name => Setting.Name ?? string.Empty;
    public string DisplayTimeBefore => (int)Setting.TimeBefore.TotalMinutes + " min";
    public string DisplayTypes { get; }
    public string DisplayTrainers { get; }

    [ObservableProperty]
    private bool _isEnabled;

    partial void OnIsEnabledChanged(bool value)
    {
        try
        {
            Setting = Setting with { Enabled = value };
            NotificationSettingsService.AddOrUpdate(Setting);
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EventNotificationSettingsRuleItem>("Failed to update setting: {0}", new object[] { ex.Message });
        }
    }
}
