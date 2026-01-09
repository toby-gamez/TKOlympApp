using System;
using TkOlympApp.Services;

namespace TkOlympApp.Helpers;

public static class DateHelpers
{
    public static string? ToFriendlyDateTimeString(DateTime? dt)
    {
        if (!dt.HasValue) return null;
        var d = dt.Value;
        var today = DateTime.Now.Date;
        // If time is exactly midnight or 23:59, show only the date (or the prefix) without time
        var t = d.TimeOfDay;
        var isMidnightOrEndOfDay = t == TimeSpan.Zero || (t.Hours == 23 && t.Minutes == 59);
        if (d.Date == today)
            return (LocalizationService.Get("Date_Today_Prefix") ?? "dnes ") + (isMidnightOrEndOfDay ? string.Empty : d.ToString("HH:mm"));
        if (d.Date == today.AddDays(1))
            return (LocalizationService.Get("Date_Tomorrow_Prefix") ?? "zítra ") + (isMidnightOrEndOfDay ? string.Empty : d.ToString("HH:mm"));
        return isMidnightOrEndOfDay ? d.ToString("dd.MM.yyyy") : d.ToString("dd.MM.yyyy HH:mm");
    }
}
