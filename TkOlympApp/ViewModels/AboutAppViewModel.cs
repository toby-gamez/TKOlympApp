using System;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Services;

namespace TkOlympApp.ViewModels;

public partial class AboutAppViewModel : ViewModelBase
{
    public string AppTitle { get; }
    public string VersionText { get; }

    public AboutAppViewModel()
    {
        AppTitle = AppInfo.Name ?? "TkOlympApp";
        var versionFormat = LocalizationService.Get("VersionFormat") ?? "Verze {0} (Build {1})";
        VersionText = string.Format(versionFormat, AppInfo.VersionString, AppInfo.BuildString);
    }
}
