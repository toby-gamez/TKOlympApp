using Microsoft.Maui.Storage;

namespace TkOlympApp.Helpers;

public static class FirstRunHelper
{
    private const string Key = "has_seen_first_run";

    public static bool HasSeen()
    {
        try
        {
            return Preferences.Get(Key, false);
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
            Preferences.Set(Key, true);
        }
        catch
        {
            // ignore
        }
    }
}
