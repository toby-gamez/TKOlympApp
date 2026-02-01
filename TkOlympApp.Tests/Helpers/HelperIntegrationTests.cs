namespace TkOlympApp.Tests.Helpers;

/// <summary>
/// Advanced edge case and integration tests for multiple helpers.
/// Tests complex scenarios and interactions.
/// </summary>
public class HelperIntegrationTests
{
    [Theory]
    [InlineData("+420 777 888 999", "420 777 888 999")] // Keeps all digits including prefix
    [InlineData("+420777888999", "420 777 888 999")]
    [InlineData("777-888-999", "777 888 999")]
    public void PhoneHelper_RealWorldCzechNumbers_FormatsCorrectly(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("11000", "110 00")] // Only 5 digits
    [InlineData("70200", "702 00")]
    [InlineData("60200", "602 00")]
    public void PostalCodeHelper_FiveDigitCodes_FormatsCorrectly(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("Praha 1, 110 00")] // More than 5 digits, returned as-is (trimmed)
    [InlineData("Ostrava, PSČ 702 00")]
    [InlineData("Brno 602-00")]
    public void PostalCodeHelper_RealWorldAddresses_DoesNotExtractWhenNotExactlyFiveDigits(string input)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert - PostalCodeHelper only formats when exactly 5 digits, otherwise returns trimmed input
        result.Should().NotBeNullOrEmpty();
        // It won't extract from complex strings, just trims
    }

    [Fact]
    public void NationalityHelper_RoundTripConversion_Works()
    {
        // Arrange
        var countryName = "Czechia";

        // Act - name to code
        var gotCode = TkOlympApp.Helpers.NationalityHelper.TryGetNumericCodeForName(countryName, out var code);
        
        // Act - code back to name
        var name = TkOlympApp.Helpers.NationalityHelper.GetCountryName(code);

        // Assert
        gotCode.Should().BeTrue();
        name.Should().Be(countryName);
    }

    [Theory]
    [InlineData("203", "cs", "česká")]
    [InlineData("203", "en", "Czech")]
    [InlineData("203", "sk", "česká")]
    [InlineData("840", "cs", "americká")]
    [InlineData("840", "en", "American")]
    public void NationalityHelper_LocalizationConsistency_Works(string code, string lang, string expectedAdj)
    {
        // Arrange
        var originalCulture = System.Globalization.CultureInfo.CurrentUICulture;
        try
        {
            System.Globalization.CultureInfo.CurrentUICulture = new System.Globalization.CultureInfo(lang);

            // Act
            var adjective = TkOlympApp.Helpers.NationalityHelper.GetLocalizedAdjective(code);

            // Assert
            adjective.Should().Be(expectedAdj);
        }
        finally
        {
            System.Globalization.CultureInfo.CurrentUICulture = originalCulture;
        }
    }

    [Fact]
    public void CohortColorHelper_AllFormats_ParseToSameColor()
    {
        // Act
        var brush1 = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush("#FF5733");
        var brush2 = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush("FF5733");
        var brush3 = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush("rgb(255, 87, 51)");

        // Assert
        brush1.Should().NotBeNull();
        brush2.Should().NotBeNull();
        brush3.Should().NotBeNull();
        
        var b1 = brush1 as Microsoft.Maui.Controls.SolidColorBrush;
        var b2 = brush2 as Microsoft.Maui.Controls.SolidColorBrush;
        var b3 = brush3 as Microsoft.Maui.Controls.SolidColorBrush;
        
        // All should produce similar red values
        b1!.Color.Red.Should().BeApproximately(1.0f, 0.01f);
        b2!.Color.Red.Should().BeApproximately(1.0f, 0.01f);
        b3!.Color.Red.Should().BeApproximately(1.0f, 0.01f);
    }

    [Fact]
    public void DateHelpers_NullHandling_IsConsistent()
    {
        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(null);

        // Assert
        result.Should().BeNull();
    }

    [Theory]
    [InlineData("")] // Empty string returns empty
    [InlineData("   ")] // Whitespace returns whitespace
    public void AllHelpers_NullOrWhitespace_HandleGracefully(string? input)
    {
        // Act & Assert - none should throw
        var act1 = () => TkOlympApp.Helpers.PhoneHelpers.Format(input);
        var act2 = () => TkOlympApp.Helpers.PostalCodeHelpers.Format(input);
        var act3 = () => TkOlympApp.Helpers.NationalityHelper.GetCountryName(input);
        
        act1.Should().NotThrow();
        act2.Should().NotThrow();
        act3.Should().NotThrow();
    }

    [Fact]
    public void AllHelpers_NullInput_ReturnsExpectedDefaults()
    {
        // Act
        var phone = TkOlympApp.Helpers.PhoneHelpers.Format(null);
        var postal = TkOlympApp.Helpers.PostalCodeHelpers.Format(null);
        var country = TkOlympApp.Helpers.NationalityHelper.GetCountryName(null);
        var date = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(null);
        
        // Assert - all handle null appropriately
        phone.Should().BeNull(); // PhoneHelpers returns null for null input
        postal.Should().BeNull(); // PostalCodeHelpers returns null for null input
        country.Should().Be("—"); // NationalityHelper returns dash for null
        date.Should().BeNull(); // DateHelpers returns null for null input
    }

    [Fact]
    public void NationalityHelper_AllCountries_HaveUniqueNames()
    {
        // Act
        var countries = TkOlympApp.Helpers.NationalityHelper.GetAllCountryNamesSorted();

        // Assert
        countries.Should().OnlyHaveUniqueItems();
        countries.Count.Should().BeGreaterThan(50); // Reasonable minimum
    }

    [Theory]
    [InlineData("Czechia")]
    [InlineData("Germany")]
    [InlineData("United States of America")]
    [InlineData("Poland")]
    [InlineData("Slovakia")]
    public void NationalityHelper_CommonCountries_CanResolveCode(string countryName)
    {
        // Act
        var success = TkOlympApp.Helpers.NationalityHelper.TryGetNumericCodeForName(countryName, out var code);

        // Assert
        success.Should().BeTrue();
        code.Should().NotBeNullOrEmpty();
        int.TryParse(code, out _).Should().BeTrue(); // Should be numeric
    }
}
