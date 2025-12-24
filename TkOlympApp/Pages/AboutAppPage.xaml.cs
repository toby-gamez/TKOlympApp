using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class AboutAppPage : ContentPage
{
    public AboutAppPage()
    {
        InitializeComponent();
        try
        {
            AppTitleLabel.Text = AppInfo.Name ?? "TkOlympApp";
            var versionFormat = LocalizationService.Get("VersionFormat") ?? "Verze {0} (Build {1})";
            VersionLabel.Text = string.Format(versionFormat, AppInfo.VersionString, AppInfo.BuildString);
        }
        catch
        {
            // ignore
        }
    }
}
