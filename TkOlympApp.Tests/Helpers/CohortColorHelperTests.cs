using System.Text.Json;
using Microsoft.Maui.Controls;

namespace TkOlympApp.Tests.Helpers;

public class CohortColorHelperTests
{
    [Fact]
    public void GetColorRgb_NullInput_ReturnsNull()
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.GetColorRgb(null);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void GetColorRgb_JsonElementWithColorRgb_ReturnsColor()
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
        var result = TkOlympApp.Helpers.CohortColorHelper.GetColorRgb(element);

        // Assert
        result.Should().Be("#FF5733");
    }

    [Fact]
    public void GetColorRgb_JsonArrayWithColorRgb_ReturnsFirstColor()
    {
        // Arrange
        var json = """
        [
            {
                "cohort": {
                    "colorRgb": "#FF5733"
                }
            },
            {
                "cohort": {
                    "colorRgb": "#00FF00"
                }
            }
        ]
        """;
        var element = JsonDocument.Parse(json).RootElement;

        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.GetColorRgb(element);

        // Assert
        result.Should().Be("#FF5733");
    }

    [Fact]
    public void GetColorRgb_JsonWithoutColorRgb_ReturnsNull()
    {
        // Arrange
        var json = """
        {
            "cohort": {
                "name": "Test Cohort"
            }
        }
        """;
        var element = JsonDocument.Parse(json).RootElement;

        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.GetColorRgb(element);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void GetColorRgb_EmptyJsonArray_ReturnsNull()
    {
        // Arrange
        var json = "[]";
        var element = JsonDocument.Parse(json).RootElement;

        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.GetColorRgb(element);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void HasColor_WithValidColor_ReturnsTrue()
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
        var result = TkOlympApp.Helpers.CohortColorHelper.HasColor(element);

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public void HasColor_NullInput_ReturnsFalse()
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.HasColor(null);

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void HasColor_NoColor_ReturnsFalse()
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
        var result = TkOlympApp.Helpers.CohortColorHelper.HasColor(element);

        // Assert
        result.Should().BeFalse();
    }

    [Theory]
    [InlineData("#FF5733")]
    [InlineData("#00FF00")]
    [InlineData("#123ABC")]
    public void ParseColorBrush_HexWithHash_ReturnsBrush(string color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeOfType<SolidColorBrush>();
    }

    [Theory]
    [InlineData("FF5733")]
    [InlineData("00FF00")]
    [InlineData("123ABC")]
    public void ParseColorBrush_HexWithoutHash_ReturnsBrush(string color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeOfType<SolidColorBrush>();
    }

    [Theory]
    [InlineData("rgb(255, 87, 51)")]
    [InlineData("RGB(0, 255, 0)")]
    [InlineData("rgb(18, 58, 188)")]
    public void ParseColorBrush_RgbFormat_ReturnsBrush(string color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeOfType<SolidColorBrush>();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void ParseColorBrush_NullOrWhitespace_ReturnsNull(string? color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().BeNull();
    }

    [Theory]
    [InlineData("invalid")]
    [InlineData("#GGGGGG")] // Invalid hex
    [InlineData("12345")] // Not 6 digits
    public void ParseColorBrush_InvalidFormat_ReturnsNull(string color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void ParseColorBrush_RgbWithInvalidValues_StillParses()
    {
        // Note: CohortColorHelper doesn't validate RGB value ranges,
        // it just extracts numbers and passes them to Color.FromRgb
        // which clamps values to 0-255 range
        
        // Arrange & Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush("rgb(256, 300, 400)");

        // Assert - helper accepts and parses (MAUI will clamp internally)
        result.Should().NotBeNull();
    }

    [Fact]
    public void ParseColorBrush_ValidHex_CreatesCorrectColor()
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush("#FF5733");

        // Assert
        result.Should().NotBeNull();
        var brush = result as SolidColorBrush;
        brush.Should().NotBeNull();
        
        // Check color components (approximate due to float precision)
        brush!.Color.Red.Should().BeApproximately(1.0f, 0.01f); // FF = 255 = 1.0
        brush.Color.Green.Should().BeApproximately(0.341f, 0.01f); // 57 = 87 / 255 ≈ 0.341
        brush.Color.Blue.Should().BeApproximately(0.2f, 0.01f); // 33 = 51 / 255 = 0.2
    }

    [Fact]
    public void ParseColorBrush_RgbFormat_CreatesCorrectColor()
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush("rgb(255, 87, 51)");

        // Assert
        result.Should().NotBeNull();
        var brush = result as SolidColorBrush;
        brush.Should().NotBeNull();
        
        brush!.Color.Red.Should().BeApproximately(1.0f, 0.01f);
        brush.Color.Green.Should().BeApproximately(0.341f, 0.01f);
        brush.Color.Blue.Should().BeApproximately(0.2f, 0.01f);
    }

    [Theory]
    [InlineData("  #FF5733  ")] // With whitespace
    [InlineData("  rgb(255, 87, 51)  ")]
    public void ParseColorBrush_WithWhitespace_TrimsAndParses(string color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeOfType<SolidColorBrush>();
    }

    [Fact]
    public void GetColorRgb_ListOfObjects_ReturnsFirstColor()
    {
        // Arrange
        var json = """
        [
            {
                "cohort": {
                    "colorRgb": "#FF0000"
                }
            },
            {
                "cohort": {
                    "colorRgb": "#00FF00"
                }
            }
        ]
        """;
        var list = JsonSerializer.Deserialize<List<JsonElement>>(json);

        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.GetColorRgb(list);

        // Assert
        result.Should().Be("#FF0000");
    }

    [Theory]
    [InlineData("000000")] // Black
    [InlineData("FFFFFF")] // White
    [InlineData("123456")]
    [InlineData("ABCDEF")]
    [InlineData("abcdef")] // Lowercase
    public void ParseColorBrush_ValidSixDigitHex_ReturnsBrush(string color)
    {
        // Act
        var result = TkOlympApp.Helpers.CohortColorHelper.ParseColorBrush(color);

        // Assert
        result.Should().NotBeNull();
        result.Should().BeOfType<SolidColorBrush>();
    }
}

