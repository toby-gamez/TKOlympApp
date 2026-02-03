using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.ApplicationModel;

namespace TkOlympApp.ViewModels;

public partial class PrivacyPolicyViewModel : ViewModelBase
{
    [RelayCommand]
    private async Task OpenContactAsync()
    {
        try
        {
            await Launcher.OpenAsync("https://tkolymp.cz/kontakt");
        }
        catch
        {
            // best-effort; ignore failures
        }
    }
}
