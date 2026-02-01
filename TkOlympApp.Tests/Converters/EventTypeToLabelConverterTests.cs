using System.Globalization;

namespace TkOlympApp.Tests.Converters;

public class EventTypeToLabelConverterTests
{
    private readonly TkOlympApp.Converters.EventTypeToLabelConverter _converter = new();

    [Theory]
    [InlineData("CAMP", "EventType_Camp")]
    [InlineData("LESSON", "EventType_Lesson")]
    [InlineData("HOLIDAY", "EventType_Holiday")]
    [InlineData("RESERVATION", "EventType_Reservation")]
    [InlineData("GROUP", "EventType_Group")]
    public void Convert_ValidEventType_ReturnsLocalizedKey(string eventType, string expectedKey)
    {
        // Act
        var result = _converter.Convert(eventType, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert - mock LocalizationService returns key if not found
        result.Should().Be(expectedKey);
    }

    [Theory]
    [InlineData(null, "")]
    [InlineData("", "")]
    public void Convert_NullOrEmpty_ReturnsEmpty(string? eventType, string expected)
    {
        // Act
        var result = _converter.Convert(eventType, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be(expected);
    }

    [Fact]
    public void Convert_UnknownType_ReturnsOriginalString()
    {
        // Arrange
        var eventType = "UNKNOWN_TYPE";

        // Act
        var result = _converter.Convert(eventType, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be("UNKNOWN_TYPE");
    }

    [Theory]
    [InlineData("camp", "EventType_Camp")]
    [InlineData("Camp", "EventType_Camp")]
    [InlineData("CAMP", "EventType_Camp")]
    [InlineData("lesson", "EventType_Lesson")]
    [InlineData("Lesson", "EventType_Lesson")]
    public void Convert_CaseInsensitive_Works(string eventType, string expectedKey)
    {
        // Act
        var result = _converter.Convert(eventType, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be(expectedKey);
    }

    [Fact]
    public void Convert_WithWhitespace_HandlesCorrectly()
    {
        // Arrange - the converter doesn't trim, so whitespace will not match
        var eventType = "  CAMP  ";

        // Act
        var result = _converter.Convert(eventType, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert - returns as-is because doesn't match any pattern
        result.Should().Be("  CAMP  ");
    }

    [Fact]
    public void ConvertBack_ThrowsNotSupportedException()
    {
        // Act & Assert
        var act = () => _converter.ConvertBack(null, typeof(object), null, CultureInfo.InvariantCulture);
        act.Should().Throw<NotSupportedException>();
    }
}

