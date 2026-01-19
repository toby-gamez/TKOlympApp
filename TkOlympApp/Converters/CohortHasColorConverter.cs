using System;
using System.Globalization;
using Microsoft.Maui.Controls;
using TkOlympApp.Helpers;

namespace TkOlympApp.Converters
{
    /// <summary>
    /// Checks if cohort data has a color. Uses CohortColorHelper for consolidated logic.
    /// </summary>
    public class CohortHasColorConverter : IValueConverter
    {
        public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            return CohortColorHelper.HasColor(value);
        }

        public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) 
            => throw new NotImplementedException();
    }
}
