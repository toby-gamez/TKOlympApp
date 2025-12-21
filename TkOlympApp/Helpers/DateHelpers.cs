using System;

namespace TkOlympApp.Helpers;

public static class DateHelpers
{
    public static string? ToFriendlyDateTimeString(DateTime? dt)
    {
        if (!dt.HasValue) return null;
        var d = dt.Value;
        var today = DateTime.Now.Date;
        if (d.Date == today)
            return $"dnes {d:HH:mm}";
        if (d.Date == today.AddDays(1))
            return $"zítra {d:HH:mm}";
        return d.ToString("dd.MM.yyyy HH:mm");
    }
}
