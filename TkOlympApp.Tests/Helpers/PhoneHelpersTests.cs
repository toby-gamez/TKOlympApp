namespace TkOlympApp.Tests.Helpers;

public class PhoneHelpersTests
{
    [Theory]
    [InlineData(null, null)]
    [InlineData("", "")]
    [InlineData("   ", "   ")]
    public void Format_NullOrWhitespace_ReturnsInput(string? input, string? expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("123456789", "123 456 789")]
    [InlineData("420123456789", "420 123 456 789")]
    [InlineData("777888999", "777 888 999")]
    public void Format_ValidPhoneNumber_FormatsInGroups(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("+420 123 456 789", "420 123 456 789")]
    [InlineData("(+420) 123-456-789", "420 123 456 789")]
    [InlineData("777.888.999", "777 888 999")]
    public void Format_WithNonDigits_ExtractsAndFormats(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("12", "12")]
    [InlineData("1234", "123 4")]
    [InlineData("12345", "123 45")]
    [InlineData("1", "1")]
    public void Format_ShortNumbers_FormatsCorrectly(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Fact]
    public void Format_NoDigits_ReturnsTrimmedInput()
    {
        // Arrange
        var input = "   abc   ";

        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be("abc");
    }

    [Theory]
    [InlineData("12345678901234567890", "123 456 789 012 345 678 90")] // Very long number
    public void Format_VeryLongNumber_FormatsCorrectly(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("+420123456789", "420 123 456 789")] // Czech prefix
    [InlineData("+1234567890", "123 456 789 0")] // US number
    [InlineData("+44 20 1234 5678", "442 012 345 678")] // UK number with spaces
    public void Format_InternationalFormats_ExtractsDigits(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("123-456-789", "123 456 789")]
    [InlineData("123.456.789", "123 456 789")]
    [InlineData("123/456/789", "123 456 789")]
    [InlineData("(123) 456 789", "123 456 789")]
    public void Format_VariousSeparators_NormalizesToSpaces(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Fact]
    public void Format_MixedWithLetters_ExtractsOnlyDigits()
    {
        // Arrange
        var input = "Call 1-800-FLOWERS (1-800-356-9377)";

        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert - extracts ALL digits (both representations)
        result.Should().Be("180 018 003 569 377");
    }

    [Theory]
    [InlineData("000", "000")]
    [InlineData("0000000000", "000 000 000 0")]
    public void Format_AllZeros_FormatsCorrectly(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PhoneHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }
}
