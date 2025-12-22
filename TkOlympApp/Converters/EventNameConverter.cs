using System;
using System.Globalization;
using System.Linq;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Converters;

public class EventNameConverter : IValueConverter
{
    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is EventService.EventInfo ev)
        {
            var name = ev.Name?.Trim();
            if (!string.IsNullOrWhiteSpace(name)) return name;

            var trainers = ev.EventTrainersList;
            if (trainers != null && trainers.Count > 0)
            {
                var first = trainers.FirstOrDefault()?.Name?.Trim();
                if (!string.IsNullOrWhiteSpace(first)) return $"Lekce: {first}";
            }

            return "Lekce";
        }

        // Fallback if value is not the expected type
        return value?.ToString() ?? string.Empty;
    }

    public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        throw new NotSupportedException();
    }
}
