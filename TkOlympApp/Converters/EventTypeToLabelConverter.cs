using System;
using System.Globalization;
using Microsoft.Maui.Controls;

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
                "CAMP" => "tábor",
                "LESSON" => "lekce",
                "HOLIDAY" => "prázdniny",
                "RESERVATION" => "rezervace",
                "GROUP" => "vedená hodina",
                _ => s
            };
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        {
            throw new NotSupportedException();
        }
    }
}
