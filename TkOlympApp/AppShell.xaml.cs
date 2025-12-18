using Microsoft.Maui.Controls;
using MauiIcons.Core;

namespace TkOlympApp;

public partial class AppShell : Shell
{
    public AppShell()
    {
        InitializeComponent();
        // Workaround for URL-based XAML namespace resolution issues
        _ = new MauiIcon();
    }
}