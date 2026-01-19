using System;
using System.Globalization;
using Microsoft.Maui.Controls;
using TkOlympApp.Helpers;

namespace TkOlympApp.Converters
{
    /// <summary>
    /// Converts cohort data to a color Brush. Uses CohortColorHelper for consolidated logic.
    /// </summary>
    public class CohortColorConverter : IValueConverter
    {
        public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            var colorRgb = CohortColorHelper.GetColorRgb(value);
            return CohortColorHelper.ParseColorBrush(colorRgb) ?? (object)null!;
        }

        public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) 
            => throw new NotImplementedException();
    }
}
