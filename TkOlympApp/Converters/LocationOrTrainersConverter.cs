using System;
using System.Globalization;
using System.Linq;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Converters
{
    public class LocationOrTrainersConverter : IValueConverter
    {
        public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            if (value is not EventService.EventInfo evt) return string.Empty;

            if (!string.IsNullOrWhiteSpace(evt.LocationText))
                return evt.LocationText ?? string.Empty;

            if (evt.EventTrainersList != null && evt.EventTrainersList.Count > 0)
            {
                var names = evt.EventTrainersList.Select(t => EventService.GetTrainerDisplayName(t)).Where(n => !string.IsNullOrWhiteSpace(n));
                return string.Join(", ", names);
            }

            return string.Empty;
        }

        public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            throw new NotImplementedException();
        }
    }
}
