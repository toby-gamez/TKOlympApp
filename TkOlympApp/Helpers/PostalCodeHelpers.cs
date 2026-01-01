using System.Linq;

namespace TkOlympApp.Helpers;

public static class PostalCodeHelpers
{
    // Format Czech postal code (PSČ) as "xxx xx" when input contains exactly 5 digits.
    public static string? Format(string? input)
    {
        if (string.IsNullOrWhiteSpace(input)) return input;
        var digits = new string(input.Where(char.IsDigit).ToArray());
        if (digits.Length == 5)
            return digits.Substring(0, 3) + " " + digits.Substring(3, 2);
        return input.Trim();
    }
}
