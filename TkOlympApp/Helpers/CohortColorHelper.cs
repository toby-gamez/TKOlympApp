using System;
using System.Collections;
using System.Text.Json;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;

namespace TkOlympApp.Helpers;

/// <summary>
/// Consolidated helper for extracting cohort colors from various data structures.
/// Replaces CohortColorConverter, CohortHasColorConverter, CohortItemHasColorConverter.
/// </summary>
public static class CohortColorHelper
{
    /// <summary>
    /// Try to extract ColorRgb from cohort data (supports JsonElement, reflection, collections).
    /// </summary>
    public static string? GetColorRgb(object? value)
    {
        if (value == null) return null;

        try
        {
            // Handle JsonElement array
            if (value is JsonElement je)
            {
                if (je.ValueKind == JsonValueKind.Array && je.GetArrayLength() > 0)
                {
                    foreach (var el in je.EnumerateArray())
                    {
                        var color = ExtractColorFromJsonElement(el);
                        if (!string.IsNullOrWhiteSpace(color)) return color;
                    }
                }
                else if (je.ValueKind == JsonValueKind.Object)
                {
                    return ExtractColorFromJsonElement(je);
                }
            }

            // Handle enumerable collections
            if (value is IEnumerable list)
            {
                foreach (var item in list)
                {
                    var color = GetColorRgb(item);
                    if (!string.IsNullOrWhiteSpace(color)) return color;
                }
            }

            // Handle single object via reflection
            return ExtractColorViaReflection(value);
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning(nameof(CohortColorHelper), "GetColorRgb failed: {0}", new object[] { ex.Message });
            return null;
        }
    }

    /// <summary>
    /// Check if cohort data has a non-empty ColorRgb value.
    /// </summary>
    public static bool HasColor(object? value)
    {
        return !string.IsNullOrWhiteSpace(GetColorRgb(value));
    }

    /// <summary>
    /// Parse ColorRgb string to a Brush (supports #hex, rgb(), or 6-digit hex).
    /// </summary>
    public static Brush? ParseColorBrush(string? colorRgb)
    {
        if (string.IsNullOrWhiteSpace(colorRgb)) return null;

        var s = colorRgb.Trim();
        try
        {
            // Handle #hex format
            if (s.StartsWith("#"))
                return new SolidColorBrush(Color.FromArgb(s));

            // Handle 6-digit hex without #
            if (s.Length == 6 && System.Text.RegularExpressions.Regex.IsMatch(s, "^[0-9A-Fa-f]{6}$"))
                return new SolidColorBrush(Color.FromArgb("#" + s));

            // Handle rgb(r, g, b) format
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
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning(nameof(CohortColorHelper), "ParseColorBrush failed: {0}", new object[] { ex.Message });
        }

        return null;
    }

    private static string? ExtractColorFromJsonElement(JsonElement je)
    {
        if (je.ValueKind != JsonValueKind.Object) return null;

        if (je.TryGetProperty("cohort", out var cohort) && cohort.ValueKind == JsonValueKind.Object)
        {
            if (cohort.TryGetProperty("colorRgb", out var colorEl) && colorEl.ValueKind == JsonValueKind.String)
            {
                return colorEl.GetString();
            }
        }

        return null;
    }

    private static string? ExtractColorViaReflection(object? item)
    {
        if (item == null) return null;

        try
        {
            var type = item.GetType();

            // Try Cohort.ColorRgb path
            var cohortProp = type.GetProperty("Cohort");
            if (cohortProp != null)
            {
                var cohortVal = cohortProp.GetValue(item);
                if (cohortVal != null)
                {
                    var colorProp = cohortVal.GetType().GetProperty("ColorRgb");
                    if (colorProp != null)
                    {
                        return colorProp.GetValue(cohortVal) as string;
                    }
                }
            }

            // Fallback: direct ColorRgb property
            var colorDirect = type.GetProperty("ColorRgb");
            if (colorDirect != null)
            {
                return colorDirect.GetValue(item) as string;
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning(nameof(CohortColorHelper), "ExtractColorViaReflection failed: {0}", new object[] { ex.Message });
        }

        return null;
    }
}
