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
}
