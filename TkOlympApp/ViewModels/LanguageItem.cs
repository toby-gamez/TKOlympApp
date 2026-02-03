using CommunityToolkit.Mvvm.ComponentModel;

namespace TkOlympApp.ViewModels;

public sealed partial class LanguageItem : ObservableObject
{
    public string Code { get; init; } = string.Empty;
    public string Name { get; init; } = string.Empty;
    public string Flag { get; init; } = string.Empty;

    [ObservableProperty]
    private bool _isCurrent;
}
