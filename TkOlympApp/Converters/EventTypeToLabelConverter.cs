using System;
using System.Globalization;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Converters
{
    public class EventTypeToLabelConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            var s = value as string;
            if (string.IsNullOrEmpty(s)) return string.Empty;
            return s.ToUpperInvariant() switch
            {
                "CAMP" => LocalizationService.Get("EventType_Camp") ?? "soustředění",
                "LESSON" => LocalizationService.Get("EventType_Lesson") ?? "lekce",
                "HOLIDAY" => LocalizationService.Get("EventType_Holiday") ?? "prázdniny",
                "RESERVATION" => LocalizationService.Get("EventType_Reservation") ?? "rezervace",
                "GROUP" => LocalizationService.Get("EventType_Group") ?? "vedená",
                _ => s
            };
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        {
            throw new NotSupportedException();
        }
    }
}
