using System;
using System.Collections;
using System.Globalization;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;

namespace TkOlympApp.Converters
{
    public class CohortHasColorConverter : IValueConverter
    {
        public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            try
            {
                if (value == null) return false;

                if (value is System.Text.Json.JsonElement je)
                {
                    if (je.ValueKind == System.Text.Json.JsonValueKind.Array && je.GetArrayLength() > 0)
                    {
                        foreach (var el in je.EnumerateArray())
                        {
                            if (el.TryGetProperty("cohort", out var cohort) && cohort.ValueKind == System.Text.Json.JsonValueKind.Object)
                            {
                                if (cohort.TryGetProperty("colorRgb", out var colorEl) && colorEl.ValueKind == System.Text.Json.JsonValueKind.String)
                                {
                                    var s = colorEl.GetString();
                                    if (!string.IsNullOrWhiteSpace(s)) return true;
                                }
                            }
                        }
                    }
                    return false;
                }

                if (value is IEnumerable list)
                {
                    foreach (var item in list)
                    {
                        if (item == null) continue;
                        var type = item.GetType();
                        var cohortProp = type.GetProperty("Cohort");
                        if (cohortProp != null)
                        {
                            var cohortVal = cohortProp.GetValue(item);
                            if (cohortVal != null)
                            {
                                var colorProp = cohortVal.GetType().GetProperty("ColorRgb");
                                if (colorProp != null)
                                {
                                    var colorStr = colorProp.GetValue(cohortVal) as string;
                                    if (!string.IsNullOrWhiteSpace(colorStr)) return true;
                                }
                            }
                        }
                    }
                }

                return false;
            }
            catch
            {
                return false;
            }
        }

        public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }
}
