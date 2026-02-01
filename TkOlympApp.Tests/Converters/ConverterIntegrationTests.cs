using System.Text.Json;

namespace TkOlympApp.Tests.Converters;

/// <summary>
/// Edge case and integration tests for converters.
/// </summary>
public class ConverterIntegrationTests
{
    [Fact]
    public void AllConverters_NullInput_HandleGracefully()
    {
        // Arrange
        var cohortColor = new TkOlympApp.Converters.CohortColorConverter();
        var cohortHasColor = new TkOlympApp.Converters.CohortHasColorConverter();
        var friendlyDate = new TkOlympApp.Converters.FriendlyDateConverter();
        var eventType = new TkOlympApp.Converters.EventTypeToLabelConverter();
        var culture = System.Globalization.CultureInfo.InvariantCulture;

        // Act & Assert - none should throw
        var act1 = () => cohortColor.Convert(null, typeof(object), null, culture);
        var act2 = () => cohortHasColor.Convert(null, typeof(object), null, culture);
        var act3 = () => friendlyDate.Convert(null, typeof(object), null, culture);
        var act4 = () => eventType.Convert(null, typeof(object), null, culture);

        act1.Should().NotThrow();
        act2.Should().NotThrow();
        act3.Should().NotThrow();
        act4.Should().NotThrow();
    }

    [Fact]
    public void AllConverters_ConvertBack_ThrowsNotSupportedOrNotImplemented()
    {
        // Arrange
        var cohortColor = new TkOlympApp.Converters.CohortColorConverter();
        var cohortHasColor = new TkOlympApp.Converters.CohortHasColorConverter();
        var friendlyDate = new TkOlympApp.Converters.FriendlyDateConverter();
        var eventType = new TkOlympApp.Converters.EventTypeToLabelConverter();
        var culture = System.Globalization.CultureInfo.InvariantCulture;

        // Act & Assert
        var act1 = () => cohortColor.ConvertBack(null, typeof(object), null, culture);
        var act2 = () => cohortHasColor.ConvertBack(null, typeof(object), null, culture);
        var act3 = () => friendlyDate.ConvertBack(null, typeof(object), null, culture);
        var act4 = () => eventType.ConvertBack(null, typeof(object), null, culture);

        act1.Should().Throw<Exception>();
        act2.Should().Throw<Exception>();
        act3.Should().Throw<Exception>();
        act4.Should().Throw<Exception>();
    }

    [Fact]
    public void CohortConverters_SameInput_ConsistentResults()
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
        var colorConverter = new TkOlympApp.Converters.CohortColorConverter();
        var hasColorConverter = new TkOlympApp.Converters.CohortHasColorConverter();
        var culture = System.Globalization.CultureInfo.InvariantCulture;

        // Act
        var color = colorConverter.Convert(element, typeof(object), null, culture);
        var hasColor = hasColorConverter.Convert(element, typeof(object), null, culture);

        // Assert - if color converter returns brush, hasColor should be true
        color.Should().NotBeNull();
        hasColor.Should().Be(true);
    }

    [Fact]
    public void CohortConverters_NoColorInput_BothReturnNullOrFalse()
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
        var colorConverter = new TkOlympApp.Converters.CohortColorConverter();
        var hasColorConverter = new TkOlympApp.Converters.CohortHasColorConverter();
        var culture = System.Globalization.CultureInfo.InvariantCulture;

        // Act
        var color = colorConverter.Convert(element, typeof(object), null, culture);
        var hasColor = hasColorConverter.Convert(element, typeof(object), null, culture);

        // Assert
        color.Should().BeNull();
        hasColor.Should().Be(false);
    }

    [Theory]
    [InlineData("2026-05-15 14:30:00")]
    [InlineData("2026-01-01 00:00:00")]
    [InlineData("2026-12-31 23:59:00")]
    public void FriendlyDateConverter_ValidDates_ReturnsNonEmpty(string dateStr)
    {
        // Arrange
        var date = DateTime.Parse(dateStr);
        var converter = new TkOlympApp.Converters.FriendlyDateConverter();
        var culture = System.Globalization.CultureInfo.InvariantCulture;

        // Act
        var result = converter.Convert(date, typeof(string), null, culture);

        // Assert
        result.Should().NotBeNull();
        result!.ToString().Should().NotBeEmpty();
    }

    [Theory]
    [InlineData("CAMP")]
    [InlineData("LESSON")]
    [InlineData("HOLIDAY")]
    [InlineData("RESERVATION")]
    [InlineData("GROUP")]
    public void EventTypeConverter_AllKnownTypes_ReturnsLocalizationKey(string eventType)
    {
        // Arrange
        var converter = new TkOlympApp.Converters.EventTypeToLabelConverter();
        var culture = System.Globalization.CultureInfo.InvariantCulture;

        // Act
        var result = converter.Convert(eventType, typeof(string), null, culture);

        // Assert
        result.Should().NotBeNull();
        result!.ToString().Should().StartWith("EventType_");
    }
}
