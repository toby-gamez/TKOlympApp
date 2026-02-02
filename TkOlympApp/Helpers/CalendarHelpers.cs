using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TkOlympApp.Models.Events;
using TkOlympApp.Services;

namespace TkOlympApp.Helpers;

/// <summary>
/// Helper methods for calendar-related operations.
/// Extracted from CalendarPage for reusability and testability.
/// </summary>
public static class CalendarHelpers
{
    /// <summary>
    /// Normalizes a name by removing accents and converting to lowercase.
    /// Used for case-insensitive name matching with accent normalization.
    /// </summary>
    public static string NormalizeName(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return string.Empty;
        var normalized = s.Normalize(System.Text.NormalizationForm.FormD);
        var chars = normalized.Where(c => System.Globalization.CharUnicodeInfo.GetUnicodeCategory(c) 
            != System.Globalization.UnicodeCategory.NonSpacingMark).ToArray();
        return new string(chars).ToLowerInvariant().Trim();
    }

    /// <summary>
    /// Computes the display name for an event.
    /// </summary>
    public static string ComputeEventName(EventInstance inst)
    {
        try
        {
            var evt = inst.Event;
            if (evt == null) return string.Empty;
            return evt.Name ?? string.Empty;
        }
        catch { return string.Empty; }
    }

    /// <summary>
    /// Computes the localized label for an event type.
    /// </summary>
    public static string ComputeEventTypeLabel(string? type)
    {
        try
        {
            if (string.IsNullOrEmpty(type)) return string.Empty;
            return type.ToUpperInvariant() switch
            {
                "CAMP" => LocalizationService.Get("EventType_Camp") ?? "soustředění",
                "LESSON" => LocalizationService.Get("EventType_Lesson") ?? "lekce",
                "HOLIDAY" => LocalizationService.Get("EventType_Holiday") ?? "prázdniny",
                "RESERVATION" => LocalizationService.Get("EventType_Reservation") ?? "rezervace",
                "GROUP" => LocalizationService.Get("EventType_Group") ?? "vedená",
                _ => type
            };
        }
        catch { return string.Empty; }
    }

    /// <summary>
    /// Computes the location or trainers display text for an event.
    /// Prefers location text, falls back to trainer names.
    /// </summary>
    public static string ComputeLocationOrTrainers(EventInstance inst)
    {
        try
        {
            var evt = inst.Event;
            if (evt == null) return string.Empty;

            // Try locationText first (preferred)
            if (!string.IsNullOrWhiteSpace(evt.LocationText))
                return evt.LocationText;

            // Fallback to trainers
            var trainers = evt.EventTrainersList?
                .Select(EventTrainerDisplayHelper.GetTrainerDisplayName)
                .Where(n => !string.IsNullOrWhiteSpace(n))
                .ToList() ?? new List<string>();
            
            if (trainers.Count > 0)
                return string.Join(", ", trainers);

            return string.Empty;
        }
        catch { return string.Empty; }
    }

    /// <summary>
    /// Computes the first registrant display text for an event instance.
    /// Uses complex logic to handle person vs couple registrations.
    /// </summary>
    public static string ComputeFirstRegistrant(EventInstance inst)
    {
        try
        {
            // prefer eventRegistrationsList if available
            var evt = inst.Event;
            if (evt?.EventRegistrationsList != null && evt.EventRegistrationsList.Count > 0)
            {
                var regs = evt.EventRegistrationsList;
                int count = regs.Count;

                // collect best-possible surname/identifier for each registration
                var surnames = new List<string>();
                
                foreach (var node in regs)
                {
                    try
                    {
                        if (node?.Person != null)
                        {
                            var full = node.Person.Name ?? string.Empty;
                            if (!string.IsNullOrWhiteSpace(full))
                                surnames.Add(ExtractSurname(full));
                        }
                        else if (node?.Couple != null)
                        {
                            var manLn = node.Couple.Man?.LastName;
                            var womanLn = node.Couple.Woman?.LastName;
                            if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn))
                                surnames.Add(manLn + " - " + womanLn);
                            else if (!string.IsNullOrWhiteSpace(manLn)) surnames.Add(manLn);
                            else if (!string.IsNullOrWhiteSpace(womanLn)) surnames.Add(womanLn);
                        }
                    }
                    catch { }
                }

                if (count == 1)
                {
                    var node = regs[0];
                    if (node?.Person != null)
                    {
                        var full = node.Person.Name ?? string.Empty;
                        if (!string.IsNullOrWhiteSpace(full)) return full;
                    }
                    if (node?.Couple != null)
                    {
                        var manLn = node.Couple.Man?.LastName;
                        var womanLn = node.Couple.Woman?.LastName;
                        if (!string.IsNullOrWhiteSpace(manLn) && !string.IsNullOrWhiteSpace(womanLn)) 
                            return manLn + " - " + womanLn;
                        if (!string.IsNullOrWhiteSpace(manLn)) return manLn;
                        if (!string.IsNullOrWhiteSpace(womanLn)) return womanLn;
                    }
                }
                else if (count == 2)
                {
                    if (surnames.Count >= 2)
                        return surnames[0] + " - " + surnames[1];
                    if (surnames.Count == 1) return surnames[0];
                }
                else if (count == 3)
                {
                    if (surnames.Count > 0) return string.Join(", ", surnames);
                }
                else if (count > 3)
                {
                    var take = surnames.Take(2).ToList();
                    if (take.Count > 0) return string.Join(", ", take) + "...";
                }
            }

        }
        catch { }
        return string.Empty;
    }

    /// <summary>
    /// Computes duration text from event instance times.
    /// </summary>
    public static string ComputeDuration(EventInstance inst)
    {
        try
        {
            if (inst.Since.HasValue && inst.Until.HasValue)
            {
                var mins = (int)(inst.Until.Value - inst.Since.Value).TotalMinutes;
                return mins > 0 ? mins + "'" : string.Empty;
            }
        }
        catch { }
        return string.Empty;
    }

    /// <summary>
    /// Computes the time range display string for an event instance.
    /// </summary>
    public static string ComputeTimeRange(EventInstance inst)
    {
        var since = inst.Since;
        var until = inst.Until;
        return (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " – " + 
               (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
    }

    /// <summary>
    /// Formats a day label for the calendar, localizing "Today" and "Tomorrow".
    /// </summary>
    public static string FormatDayLabel(DateTime date, CultureInfo? culture = null)
    {
        culture ??= CultureInfo.CurrentUICulture ?? CultureInfo.CurrentCulture;
        var todayDate = DateTime.Now.Date;
        
        if (date == todayDate) 
            return LocalizationService.Get("Today") ?? "Dnes";
        
        if (date == todayDate.AddDays(1)) 
            return LocalizationService.Get("Tomorrow") ?? "Zítra";
        
        var name = culture.DateTimeFormat.GetDayName(date.DayOfWeek);
        return char.ToUpper(name[0], culture) + name.Substring(1) + $" {date:dd.MM.}";
    }

    /// <summary>
    /// Formats a week label for the calendar header.
    /// </summary>
    public static string FormatWeekLabel(DateTime weekStart, DateTime weekEnd)
    {
        return $"{weekStart:dd.MM.yyyy} – {weekEnd:dd.MM.yyyy}";
    }

    /// <summary>
    /// Adds name variants (full name + surname) to a list for matching.
    /// </summary>
    public static void AddNameVariants(List<string> list, string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return;
        var v = name.Trim();
        if (!list.Contains(v, StringComparer.OrdinalIgnoreCase)) list.Add(v);
        
        // add surname (last token)
        var parts = v.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length > 0)
        {
            var last = parts[^1];
            if (!list.Contains(last, StringComparer.OrdinalIgnoreCase)) list.Add(last);
        }
    }

    private static string ExtractSurname(string full)
    {
        if (string.IsNullOrWhiteSpace(full)) return string.Empty;
        var parts = full.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return parts.Length > 1 ? parts[parts.Length - 1] : full;
    }
}
