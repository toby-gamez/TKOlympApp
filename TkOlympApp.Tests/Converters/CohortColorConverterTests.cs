using System.Globalization;
using System.Text.Json;
using Microsoft.Maui.Controls;

namespace TkOlympApp.Tests.Converters;

public class CohortColorConverterTests
{
    private readonly TkOlympApp.Converters.CohortColorConverter _converter = new();

    [Fact]
    public void Convert_NullInput_ReturnsNull()
    {
        // Act
        var result = _converter.Convert(null, typeof(Brush), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void Convert_ValidColorJson_ReturnsBrush()
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
        var result = _converter.Convert(element, typeof(Brush), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeOfType<SolidColorBrush>();
    }

    [Fact]
    public void Convert_NoColor_ReturnsNull()
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
        var result = _converter.Convert(element, typeof(Brush), null, CultureInfo.InvariantCulture);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void ConvertBack_ThrowsNotImplementedException()
    {
        // Act & Assert
        var act = () => _converter.ConvertBack(null, typeof(object), null, CultureInfo.InvariantCulture);
        act.Should().Throw<NotImplementedException>();
    }
}
