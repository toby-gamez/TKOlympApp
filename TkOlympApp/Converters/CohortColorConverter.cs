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
        public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            try
            {
                if (value == null) return null!;

                // Handle JsonElement array (raw deserialized JSON)
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
                                    var brush = TryParseColorBrush(s);
                                    if (brush != null) return brush;
                                }
                            }
                        }
                    }
                    // If JsonElement is a single object, try cohort.colorRgb there too
                    if (je.ValueKind == System.Text.Json.JsonValueKind.Object)
                    {
                        if (je.TryGetProperty("cohort", out var cohort) && cohort.ValueKind == System.Text.Json.JsonValueKind.Object)
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

                // If value is an enumerable (list of links), extract first non-empty color
                if (value is System.Collections.IEnumerable list)
                {
                    foreach (var item in list)
                    {
                        var b = BrushFromPossibleCohortItem(item);
                        if (b != null) return b;
                    }
                }

                // If value is a single item (EventTargetCohortLink), try to extract its cohort.colorRgb
                var singleBrush = BrushFromPossibleCohortItem(value);
                if (singleBrush != null) return singleBrush;

                return null!;
            }
            catch
            {
                return null!;
            }
        }

        private Brush? BrushFromPossibleCohortItem(object? item)
        {
            if (item == null) return null;
            try
            {
                // If item is JsonElement object
                if (item is System.Text.Json.JsonElement je && je.ValueKind == System.Text.Json.JsonValueKind.Object)
                {
                    if (je.TryGetProperty("cohort", out var cohort) && cohort.ValueKind == System.Text.Json.JsonValueKind.Object)
                    {
                        if (cohort.TryGetProperty("colorRgb", out var colorEl) && colorEl.ValueKind == System.Text.Json.JsonValueKind.String)
                        {
                            return TryParseColorBrush(colorEl.GetString());
                        }
                    }
                    return null;
                }

                // Reflection: look for Cohort property with ColorRgb
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
                            return TryParseColorBrush(colorStr);
                        }
                    }
                }

                // Fallback: if object itself has ColorRgb property
                var colorDirect = type.GetProperty("ColorRgb");
                if (colorDirect != null)
                {
                    var s = colorDirect.GetValue(item) as string;
                    return TryParseColorBrush(s);
                }
            }
            catch { }
            return null;
        }

        public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) => throw new NotImplementedException();

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
