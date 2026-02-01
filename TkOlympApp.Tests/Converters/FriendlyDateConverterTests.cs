using System.Globalization;

namespace TkOlympApp.Tests.Converters;

public class FriendlyDateConverterTests
{
    private readonly TkOlympApp.Converters.FriendlyDateConverter _converter = new();

    [Fact]
    public void Convert_NullInput_ReturnsEmptyString()
    {
        // Act
        var result = _converter.Convert(null, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be(string.Empty);
    }

    [Fact]
    public void Convert_ValidDateTime_ReturnsFormattedString()
    {
        // Arrange
        var date = new DateTime(2026, 5, 15, 14, 30, 0);

        // Act
        var result = _converter.Convert(date, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be("15.05.2026 14:30");
    }

    [Fact]
    public void Convert_MidnightDateTime_ReturnsDateOnly()
    {
        // Arrange
        var date = new DateTime(2026, 5, 15, 0, 0, 0);

        // Act
        var result = _converter.Convert(date, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be("15.05.2026");
    }

    [Fact]
    public void Convert_NullableDateTime_ReturnsFormattedString()
    {
        // Arrange
        DateTime? date = new DateTime(2026, 5, 15, 14, 30, 0);

        // Act
        var result = _converter.Convert(date, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be("15.05.2026 14:30");
    }

    [Fact]
    public void Convert_NonDateTimeObject_ReturnsToString()
    {
        // Arrange
        var obj = "test string";

        // Act
        var result = _converter.Convert(obj, typeof(string), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be("test string");
    }

    [Fact]
    public void ConvertBack_ThrowsNotImplementedException()
    {
        // Act & Assert
        var act = () => _converter.ConvertBack(null, typeof(object), null, CultureInfo.InvariantCulture);
        act.Should().Throw<NotImplementedException>();
    }
}
