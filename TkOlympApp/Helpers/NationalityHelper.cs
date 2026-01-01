using System.Collections.Generic;
using System.Globalization;
using TkOlympApp.Services;

namespace TkOlympApp.Helpers;

public static class NationalityHelper
{
    private static readonly Dictionary<int, string> _map = new()
    {
        // Common ISO 3166-1 numeric codes -> English/Czech names (short list, extend as needed)
        { 4, "Afghanistan" },
        { 8, "Albania" },
        { 12, "Algeria" },
        { 20, "Andorra" },
        { 24, "Angola" },
        { 28, "Antigua and Barbuda" },
        { 31, "Azerbaijan" },
        { 32, "Argentina" },
        { 36, "Australia" },
        { 40, "Austria" },
        { 44, "Bahamas" },
        { 48, "Bahrain" },
        { 50, "Bangladesh" },
        { 51, "Armenia" },
        { 52, "Barbados" },
        { 56, "Belgium" },
        { 60, "Bermuda" },
        { 64, "Bhutan" },
        { 68, "Bolivia" },
        { 70, "Bosnia and Herzegovina" },
        { 72, "Botswana" },
        { 76, "Brazil" },
        { 84, "Belize" },
        { 86, "British Indian Ocean Territory" },
        { 90, "Solomon Islands" },
        { 96, "Brunei Darussalam" },
        { 100, "Bulgaria" },
        { 104, "Myanmar" },
        { 108, "Burundi" },
        { 112, "Belarus" },
        { 116, "Cambodia" },
        { 120, "Cameroon" },
        { 124, "Canada" },
        { 132, "Cape Verde" },
        { 136, "Cayman Islands" },
        { 140, "Central African Republic" },
        { 144, "Sri Lanka" },
        { 148, "Chad" },
        { 152, "Chile" },
        { 156, "China" },
        { 170, "Colombia" },
        { 174, "Comoros" },
        { 178, "Congo" },
        { 180, "Democratic Republic of the Congo" },
        { 188, "Costa Rica" },
        { 191, "Croatia" },
        { 192, "Cuba" },
        { 196, "Cyprus" },
        { 203, "Czechia" },
        { 208, "Denmark" },
        { 212, "Dominica" },
        { 214, "Dominican Republic" },
        { 218, "Ecuador" },
        { 222, "El Salvador" },
        { 226, "Equatorial Guinea" },
        { 231, "Ethiopia" },
        { 232, "Eritrea" },
        { 233, "Estonia" },
        { 246, "Falkland Islands (Malvinas)" },
        { 250, "France" },
        { 276, "Germany" },
        { 300, "Greece" },
        { 312, "Guinea" },
        { 320, "Guatemala" },
        { 324, "Guinea-Bissau" },
        { 328, "Guyana" },
        { 332, "Haiti" },
        { 340, "Honduras" },
        { 344, "Hong Kong" },
        { 348, "Hungary" },
        { 352, "Iceland" },
        { 356, "India" },
        { 360, "Indonesia" },
        { 364, "Iran" },
        { 368, "Iraq" },
        { 372, "Ireland" },
        { 376, "Israel" },
        { 380, "Italy" },
        { 384, "Ivory Coast" },
        { 388, "Jamaica" },
        { 392, "Japan" },
        { 398, "Kazakhstan" },
        { 400, "Jordan" },
        { 404, "Kenya" },
        { 410, "Korea, Republic of" },
        { 414, "Kuwait" },
        { 417, "Kyrgyzstan" },
        { 418, "Lao People's Democratic Republic" },
        { 422, "Lebanon" },
        { 428, "Latvia" },
        { 430, "Liberia" },
        { 434, "Libya" },
        { 440, "Lithuania" },
        { 442, "Luxembourg" },
        { 450, "Madagascar" },
        { 458, "Macedonia" },
        { 462, "Malawi" },
        { 466, "Malaysia" },
        { 470, "Maldives" },
        { 478, "Mali" },
        { 480, "Malta" },
        { 484, "Mexico" },
        { 498, "Moldova" },
        { 499, "Montenegro" },
        { 504, "Morocco" },
        { 512, "Oman" },
        { 528, "Netherlands" },
        { 554, "New Zealand" },
        { 558, "Nicaragua" },
        { 566, "Nigeria" },
        { 578, "Norway" },
        { 591, "Panama" },
        { 604, "Peru" },
        { 616, "Poland" },
        { 620, "Portugal" },
        { 634, "Qatar" },
        { 642, "Romania" },
        { 643, "Russian Federation" },
        { 682, "Saudi Arabia" },
        { 702, "Singapore" },
        { 703, "Slovakia" },
        { 704, "Vietnam" },
        { 705, "Slovenia" },
        { 724, "Spain" },
        { 752, "Sweden" },
        { 756, "Switzerland" },
        { 764, "Thailand" },
        { 792, "Turkey" },
        { 804, "Ukraine" },
        { 826, "United Kingdom" },
        { 840, "United States of America" },
        { 850, "Puerto Rico" },
        { 858, "Uruguay" },
        { 862, "Venezuela" },
        { 894, "Zambia" }
    };

    public static string GetCountryName(string? numericCode)
    {
        if (string.IsNullOrWhiteSpace(numericCode)) return "—";

        // Accept either numeric string or plain name
        if (int.TryParse(numericCode.Trim(), out var code))
        {
            if (_map.TryGetValue(code, out var name)) return name;
            return numericCode; // unknown numeric code - return raw
        }

        // If it's not numeric, return as-is (already a name)
        return numericCode.Trim();
    }

    // Return a sorted list of available country names (culture-aware)
    public static List<string> GetAllCountryNamesSorted()
    {
        var comparer = StringComparer.Create(CultureInfo.CurrentUICulture, ignoreCase: true);
        var list = _map.Values.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        list.Sort((a, b) => StringComparer.Create(CultureInfo.CurrentUICulture, ignoreCase: false).Compare(a, b));
        return list;
    }

    // Try to find the numeric code for a given country name (case-insensitive)
    public static bool TryGetNumericCodeForName(string? name, out string numericCode)
    {
        numericCode = string.Empty;
        if (string.IsNullOrWhiteSpace(name)) return false;
        try
        {
            var kv = _map.FirstOrDefault(kv2 => string.Equals(kv2.Value, name.Trim(), StringComparison.OrdinalIgnoreCase));
            if (!kv.Equals(default(KeyValuePair<int, string>)))
            {
                numericCode = kv.Key.ToString();
                return true;
            }
        }
        catch
        {
            // ignore
        }
        return false;
    }

    // Return a localized adjectival (feminine for Slavic languages) form where available.
    public static string GetLocalizedAdjective(string? numericCodeOrName)
    {
        if (string.IsNullOrWhiteSpace(numericCodeOrName)) return "—";

        var lang = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName;
        var country = GetCountryName(numericCodeOrName);

        // English adjectives (fallback and for non-Slavic UIs)
        var en = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "Czech" },
            { "Czech Republic", "Czech" },
            { "Slovakia", "Slovak" },
            { "Poland", "Polish" },
            { "Germany", "German" },
            { "Austria", "Austrian" },
            { "Hungary", "Hungarian" },
            { "United States of America", "American" },
            { "United Kingdom", "British" },
            { "France", "French" },
            { "Italy", "Italian" },
            { "Spain", "Spanish" },
            { "Slovenia", "Slovenian" },
            { "Ukraine", "Ukrainian" },
            { "Russia", "Russian" }
        };

        // Czech feminine adjectives
        var cz = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "česká" },
            { "Czech Republic", "česká" },
            { "Slovakia", "slovenská" },
            { "Poland", "polská" },
            { "Germany", "německá" },
            { "Austria", "rakouská" },
            { "Hungary", "maďarská" },
            { "United States of America", "americká" },
            { "United Kingdom", "britská" },
            { "France", "francouzská" },
            { "Italy", "italská" },
            { "Spain", "španělská" },
            { "Slovenia", "slovinská" },
            { "Ukraine", "ukrajinská" },
            { "Russia", "ruská" }
        };

        // Slovak feminine adjectives
        var sk = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "česká" },
            { "Slovakia", "slovenská" },
            { "Poland", "poľská" },
            { "Germany", "nemecká" },
            { "Austria", "rakúska" },
            { "Hungary", "maďarská" },
            { "United States of America", "americká" },
            { "United Kingdom", "britská" },
            { "France", "francúzska" },
            { "Italy", "talianska" },
            { "Spain", "španielska" },
            { "Slovenia", "slovinská" },
            { "Ukraine", "ukrajinská" },
            { "Russia", "ruská" }
        };

        // Ukrainian feminine adjectives
        var uk = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "чеська" },
            { "Slovakia", "словацька" },
            { "Poland", "польська" },
            { "Germany", "німецька" },
            { "Austria", "австрійська" },
            { "Hungary", "угорська" },
            { "United States of America", "американська" },
            { "United Kingdom", "британська" },
            { "France", "французька" },
            { "Italy", "італійська" },
            { "Spain", "іспанська" },
            { "Slovenia", "словенська" },
            { "Ukraine", "українська" },
            { "Russia", "російська" }
        };

        // Slovenian feminine adjectives
        var sl = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "češka" },
            { "Slovakia", "slovaška" },
            { "Poland", "poljska" },
            { "Germany", "nemška" },
            { "Austria", "avstrijska" },
            { "Hungary", "madžarska" },
            { "United States of America", "ameriška" },
            { "United Kingdom", "britanska" },
            { "France", "francoska" },
            { "Italy", "italijanska" },
            { "Spain", "španska" },
            { "Slovenia", "slovenska" },
            { "Ukraine", "ukrajinska" },
            { "Russia", "ruska" }
        };

        // Vietnamese / Norwegian simple mappings (fall back to English otherwise)
        var vi = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "Séc" },
            { "Slovakia", "Slovákia" },
            { "Poland", "Ba Lan" },
            { "Germany", "Đức" },
            { "United States of America", "Mỹ" },
            { "United Kingdom", "Vương quốc Anh" }
        };

        var no = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "Czechia", "tsjekkisk" },
            { "Slovakia", "slovakisk" },
            { "Poland", "polsk" },
            { "Germany", "tysk" },
            { "United States of America", "amerikansk" },
            { "United Kingdom", "britisk" }
        };

        try
        {
            return lang switch
            {
                "cs" when cz.TryGetValue(country, out var czAdj) => czAdj,
                "sk" when sk.TryGetValue(country, out var skAdj) => skAdj,
                "uk" when uk.TryGetValue(country, out var ukAdj) => ukAdj,
                "sl" when sl.TryGetValue(country, out var slAdj) => slAdj,
                "vi" when vi.TryGetValue(country, out var viAdj) => viAdj,
                "no" when no.TryGetValue(country, out var noAdj) => noAdj,
                "en" when en.TryGetValue(country, out var enAdj) => enAdj,
                _ when en.TryGetValue(country, out var fallbackAdj) => fallbackAdj,
                _ => country
            };
        }
        catch
        {
            return country;
        }
    }
}
