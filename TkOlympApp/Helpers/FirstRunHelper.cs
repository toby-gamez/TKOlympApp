using Microsoft.Maui.Storage;

namespace TkOlympApp.Helpers;

public static class FirstRunHelper
{

    public static bool HasSeen()
    {
        try
        {
            return Preferences.Get(AppConstants.FirstRunSeenKey, false);
        }
        catch
        {
            return false;
        }
    }

    public static void SetSeen()
    {
        try
        {
            Preferences.Set(AppConstants.FirstRunSeenKey, true);
        }
        catch
        {
            // ignore
        }
    }
}
