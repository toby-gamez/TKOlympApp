using System;
using Microsoft.Maui.Controls;
using System.Globalization;
using TkOlympApp.Helpers;

namespace TkOlympApp.Converters;

public class FriendlyDateConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value == null) return string.Empty;
        if (value is DateTime dt) return DateHelpers.ToFriendlyDateTimeString(dt) ?? string.Empty;
        // Nullable DateTime when boxed will appear as DateTime when it has a value, otherwise null.
        return value.ToString() ?? string.Empty;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
    {
        throw new NotImplementedException();
    }
}
