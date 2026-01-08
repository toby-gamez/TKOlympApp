using System;
using System.Collections.Generic;
using System.Globalization;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;

namespace TkOlympApp.Converters
{
    public class CohortColorConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            try
            {
                if (value == null) return null!;

                // Expecting a list of EventTargetCohortLink or similar
                if (value is System.Text.Json.JsonElement je)
                {
                    // fallback: try to extract first cohort.colorRgb from JsonElement
                    if (je.ValueKind == System.Text.Json.JsonValueKind.Array && je.GetArrayLength() > 0)
                    {
                        foreach (var el in je.EnumerateArray())
                        {
                            if (el.TryGetProperty("cohort", out var cohort) && cohort.ValueKind == System.Text.Json.JsonValueKind.Object)
                            {
                                if (cohort.TryGetProperty("colorRgb", out var colorEl) && colorEl.ValueKind == System.Text.Json.JsonValueKind.String)
                                {
                                    var s = colorEl.GetString();
                                    var brush = TryParseColorBrush(s);
                                    if (brush != null) return brush;
                                }
                            }
                        }
                    }
                }

                if (value is System.Collections.IEnumerable list)
                {
                    foreach (var item in list)
                    {
                        if (item == null) continue;
                        // item may be EventService.EventTargetCohortLink
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
                                    var brush = TryParseColorBrush(colorStr);
                                    if (brush != null) return brush;
                                }
                            }
                        }
                    }
                }

                return null!;
            }
            catch
            {
                return null!;
            }
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) => throw new NotImplementedException();

        private Brush? TryParseColorBrush(string? colorRgb)
        {
            if (string.IsNullOrWhiteSpace(colorRgb)) return null;
            var s = colorRgb.Trim();
            try
            {
                if (s.StartsWith("#"))
                    return new SolidColorBrush(Color.FromArgb(s));

                if (s.Length == 6)
                    return new SolidColorBrush(Color.FromArgb("#" + s));

                if (s.StartsWith("rgb", StringComparison.OrdinalIgnoreCase))
                {
                    var digits = System.Text.RegularExpressions.Regex.Matches(s, "\\d+");
                    if (digits.Count >= 3)
                    {
                        var r = int.Parse(digits[0].Value);
                        var g = int.Parse(digits[1].Value);
                        var b = int.Parse(digits[2].Value);
                        return new SolidColorBrush(Color.FromRgb(r, g, b));
                    }
                }
            }
            catch { }
            return null;
        }
    }
}
