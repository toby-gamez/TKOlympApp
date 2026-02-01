namespace TkOlympApp.Tests.Helpers;

public class PostalCodeHelpersTests
{
    [Theory]
    [InlineData(null, null)]
    [InlineData("", "")]
    [InlineData("   ", "   ")]
    public void Format_NullOrWhitespace_ReturnsInput(string? input, string? expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("12345", "123 45")]
    [InlineData("10000", "100 00")]
    [InlineData("79801", "798 01")]
    public void Format_ExactlyFiveDigits_FormatsCorrectly(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("123 45", "123 45")]
    [InlineData("798-01", "798 01")]
    [InlineData("1 0 0 0 0", "100 00")]
    public void Format_FiveDigitsWithNonDigits_ExtractsAndFormats(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Theory]
    [InlineData("1234", "1234")] // Less than 5 digits
    [InlineData("123456", "123456")] // More than 5 digits
    [InlineData("12", "12")]
    [InlineData("123456789", "123456789")]
    public void Format_NotExactlyFiveDigits_ReturnsTrimmedInput(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }

    [Fact]
    public void Format_NoDigits_ReturnsTrimmedInput()
    {
        // Arrange
        var input = "   abcde   ";

        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be("abcde");
    }

    [Theory]
    [InlineData("CZ-12345", "123 45")] // With prefix
    [InlineData("PSČ: 12345", "123 45")]
    public void Format_FiveDigitsWithPrefix_ExtractsAndFormats(string input, string expected)
    {
        // Act
        var result = TkOlympApp.Helpers.PostalCodeHelpers.Format(input);

        // Assert
        result.Should().Be(expected);
    }
}
