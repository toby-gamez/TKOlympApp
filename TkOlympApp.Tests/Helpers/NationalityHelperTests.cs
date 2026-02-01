using System.Globalization;

namespace TkOlympApp.Tests.Helpers;

public class NationalityHelperTests
{
    [Theory]
    [InlineData("203", "Czechia")]
    [InlineData("840", "United States of America")]
    [InlineData("826", "United Kingdom")]
    [InlineData("276", "Germany")]
    [InlineData("250", "France")]
    [InlineData("380", "Italy")]
    [InlineData("724", "Spain")]
    [InlineData("616", "Poland")]
    [InlineData("703", "Slovakia")]
    [InlineData("705", "Slovenia")]
    [InlineData("804", "Ukraine")]
    [InlineData("643", "Russian Federation")]
    [InlineData("578", "Norway")]
    [InlineData("704", "Vietnam")]
    public void GetCountryName_ValidNumericCode_ReturnsCorrectName(string code, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetCountryName(code);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData(null, "—")]
    [InlineData("", "—")]
    [InlineData("   ", "—")]
    public void GetCountryName_NullOrWhitespace_ReturnsDash(string? input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetCountryName(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("9999", "9999")] // Unknown code
    [InlineData("12345", "12345")]
    public void GetCountryName_UnknownNumericCode_ReturnsRawCode(string code, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetCountryName(code);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("Czechia", "Czechia")]
    [InlineData("Germany", "Germany")]
    [InlineData("Some Country", "Some Country")]
    public void GetCountryName_NonNumericInput_ReturnsAsIs(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetCountryName(input);

        // Assert
        result.Should().Be(expected);
    }

    [Fact]
    public void GetAllCountryNamesSorted_ReturnsNonEmptyList()
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetAllCountryNamesSorted();

        // Assert
        result.Should().NotBeEmpty();
        result.Should().Contain("Czechia");
        result.Should().Contain("Germany");
        result.Should().Contain("United States of America");
    }

    [Fact]
    public void GetAllCountryNamesSorted_ReturnsSortedList()
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetAllCountryNamesSorted();

        // Assert
        result.Should().BeInAscendingOrder();
    }

    [Fact]
    public void GetAllCountryNamesSorted_HasNoDuplicates()
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetAllCountryNamesSorted();

        // Assert
        result.Should().OnlyHaveUniqueItems();
    }

    [Theory]
    [InlineData("Czechia", "203", true)]
    [InlineData("Germany", "276", true)]
    [InlineData("United States of America", "840", true)]
    [InlineData("Poland", "616", true)]
    [InlineData("Slovakia", "703", true)]
    [InlineData("Unknown Country", "", false)]
    public void TryGetNumericCodeForName_VariousInputs_ReturnsExpected(string name, string expectedCode, bool expectedResult)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.TryGetNumericCodeForName(name, out var code);

        // Assert
        result.Should().Be(expectedResult);
        if (expectedResult)
        {
            code.Should().Be(expectedCode);
        }
    }

    [Theory]
    [InlineData(null, false)]
    [InlineData("", false)]
    [InlineData("   ", false)]
    public void TryGetNumericCodeForName_NullOrWhitespace_ReturnsFalse(string? name, bool expected)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.TryGetNumericCodeForName(name, out var code);

        // Assert
        result.Should().Be(expected);
        code.Should().BeEmpty();
    }

    [Theory]
    [InlineData("CZECHIA", "203", true)] // Case insensitive
    [InlineData("czechia", "203", true)]
    [InlineData("CzEcHiA", "203", true)]
    public void TryGetNumericCodeForName_CaseInsensitive_Works(string name, string expectedCode, bool expectedResult)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.TryGetNumericCodeForName(name, out var code);

        // Assert
        result.Should().Be(expectedResult);
        code.Should().Be(expectedCode);
    }

    [Theory]
    [InlineData("cs", "203", "česká")] // Czech
    [InlineData("cs", "276", "německá")] // German
    [InlineData("cs", "840", "americká")] // USA
    [InlineData("en", "203", "Czech")] // English
    [InlineData("en", "840", "American")]
    [InlineData("sk", "203", "česká")] // Slovak
    [InlineData("sk", "703", "slovenská")]
    [InlineData("uk", "203", "чеська")] // Ukrainian
    [InlineData("uk", "804", "українська")]
    [InlineData("sl", "203", "češka")] // Slovenian
    [InlineData("sl", "705", "slovenska")]
    public void GetLocalizedAdjective_VariousLanguages_ReturnsCorrectAdjective(string language, string code, string expected)
    {
        // Arrange
        var originalCulture = CultureInfo.CurrentUICulture;
        try
        {
            CultureInfo.CurrentUICulture = new CultureInfo(language);

            // Act
            var result = TkOlympApp.Helpers.NationalityHelper.GetLocalizedAdjective(code);

            // Assert
            result.Should().Be(expected);
        }
        finally
        {
            CultureInfo.CurrentUICulture = originalCulture;
        }
    }

    [Theory]
    [InlineData("cs", "Czechia", "česká")]
    [InlineData("en", "Czechia", "Czech")]
    public void GetLocalizedAdjective_AcceptsCountryName_ReturnsAdjective(string language, string countryName, string expected)
    {
        // Arrange
        var originalCulture = CultureInfo.CurrentUICulture;
        try
        {
            CultureInfo.CurrentUICulture = new CultureInfo(language);

            // Act
            var result = TkOlympApp.Helpers.NationalityHelper.GetLocalizedAdjective(countryName);

            // Assert
            result.Should().Be(expected);
        }
        finally
        {
            CultureInfo.CurrentUICulture = originalCulture;
        }
    }

    [Theory]
    [InlineData(null, "—")]
    [InlineData("", "—")]
    [InlineData("   ", "—")]
    public void GetLocalizedAdjective_NullOrWhitespace_ReturnsDash(string? input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.GetLocalizedAdjective(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("en", "9999", "9999")] // Unknown code falls back to raw value
    [InlineData("cs", "Australia", "Australia")] // Unmapped country returns name
    public void GetLocalizedAdjective_UnknownCountry_ReturnsCountryName(string language, string input, string expected)
    {
        // Arrange
        var originalCulture = CultureInfo.CurrentUICulture;
        try
        {
            CultureInfo.CurrentUICulture = new CultureInfo(language);

            // Act
            var result = TkOlympApp.Helpers.NationalityHelper.GetLocalizedAdjective(input);

            // Assert
            result.Should().Be(expected);
        }
        finally
        {
            CultureInfo.CurrentUICulture = originalCulture;
        }
    }

    [Fact]
    public void TryGetNumericCodeForName_WithWhitespace_TrimsAndFinds()
    {
        // Act
        var result = TkOlympApp.Helpers.NationalityHelper.TryGetNumericCodeForName("  Czechia  ", out var code);

        // Assert
        result.Should().BeTrue();
        code.Should().Be("203");
    }
}
