using System.Globalization;
using System.Text.Json;

namespace TkOlympApp.Tests.Converters;

public class CohortHasColorConverterTests
{
    private readonly TkOlympApp.Converters.CohortHasColorConverter _converter = new();

    [Fact]
    public void Convert_NullInput_ReturnsFalse()
    {
        // Act
        var result = _converter.Convert(null, typeof(bool), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be(false);
    }

    [Fact]
    public void Convert_ValidColor_ReturnsTrue()
    {
        // Arrange
        var json = """
        {
            "cohort": {
                "colorRgb": "#FF5733"
            }
        }
        """;
        var element = JsonDocument.Parse(json).RootElement;

        // Act
        var result = _converter.Convert(element, typeof(bool), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be(true);
    }

    [Fact]
    public void Convert_NoColor_ReturnsFalse()
    {
        // Arrange
        var json = """
        {
            "cohort": {
                "name": "Test"
            }
        }
        """;
        var element = JsonDocument.Parse(json).RootElement;

        // Act
        var result = _converter.Convert(element, typeof(bool), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().Be(false);
    }

    [Fact]
    public void ConvertBack_ThrowsNotImplementedException()
    {
        // Act & Assert
        var act = () => _converter.ConvertBack(null, typeof(object), null, CultureInfo.InvariantCulture);
        act.Should().Throw<NotImplementedException>();
    }
}
