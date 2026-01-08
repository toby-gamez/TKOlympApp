using System;
using System.Globalization;
using Microsoft.Maui.Controls;

namespace TkOlympApp.Converters
{
    public class CohortItemHasColorConverter : IValueConverter
    {
        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            try
            {
                if (value == null) return false;
                // value expected to be an EventTargetCohortLink or JsonElement
                var t = value.GetType();
                var cohortProp = t.GetProperty("Cohort");
                if (cohortProp != null)
                {
                    var cohortVal = cohortProp.GetValue(value);
                    if (cohortVal != null)
                    {
                        var colorProp = cohortVal.GetType().GetProperty("ColorRgb");
                        if (colorProp != null)
                        {
                            var colorStr = colorProp.GetValue(cohortVal) as string;
                            return !string.IsNullOrWhiteSpace(colorStr);
                        }
                    }
                }

                // fallback: handle JsonElement
                if (value is System.Text.Json.JsonElement je)
                {
                    if (je.TryGetProperty("cohort", out var cohortEl) && cohortEl.ValueKind == System.Text.Json.JsonValueKind.Object)
                    {
                        if (cohortEl.TryGetProperty("colorRgb", out var colorEl) && colorEl.ValueKind == System.Text.Json.JsonValueKind.String)
                        {
                            var s = colorEl.GetString();
                            return !string.IsNullOrWhiteSpace(s);
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

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();
    }
}
