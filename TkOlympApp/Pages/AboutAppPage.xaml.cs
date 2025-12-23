using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;

namespace TkOlympApp.Pages;

public partial class AboutAppPage : ContentPage
{
    public AboutAppPage()
    {
        InitializeComponent();
        try
        {
            AppTitleLabel.Text = AppInfo.Name ?? "TkOlympApp";
            VersionLabel.Text = $"Verze {AppInfo.VersionString} (Build {AppInfo.BuildString})";
        }
        catch
        {
            // ignore
        }
    }
}
