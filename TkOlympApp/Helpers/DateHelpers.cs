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
        if (d.Date == today)
            return (LocalizationService.Get("Date_Today_Prefix") ?? "dnes ") + d.ToString("HH:mm");
        if (d.Date == today.AddDays(1))
            return (LocalizationService.Get("Date_Tomorrow_Prefix") ?? "zítra ") + d.ToString("HH:mm");
        return d.ToString("dd.MM.yyyy HH:mm");
    }
}
