using System.Linq;

namespace TkOlympApp.Helpers;

public static class PhoneHelpers
{
    // Format phone number by keeping digits and grouping them in threes from the start: "xxx xxx xxx"
    public static string? Format(string? input)
    {
        if (string.IsNullOrWhiteSpace(input)) return input;
        var digits = new string(input.Where(char.IsDigit).ToArray());
        if (string.IsNullOrEmpty(digits)) return input.Trim();

        var groups = Enumerable.Range(0, (digits.Length + 2) / 3)
            .Select(i => digits.Substring(i * 3, System.Math.Min(3, digits.Length - i * 3)));

        return string.Join(" ", groups);
    }
}
