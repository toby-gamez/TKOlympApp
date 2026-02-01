namespace TkOlympApp.Tests.Helpers;

/// <summary>
/// Tests for DateHelpers (note: závislost na LocalizationService ztěžuje testování).
/// TODO: Extrahovat do Core projektu a mockovat lokalizaci.
/// </summary>
public class DateHelpersTests
{
    [Fact]
    public void ToFriendlyDateTimeString_NullInput_ReturnsNull()
    {
        // Arrange & Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(null);

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void ToFriendlyDateTimeString_MidnightTime_DoesNotShowTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 0, 0, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026"); // Midnight => only date
    }

    [Fact]
    public void ToFriendlyDateTimeString_EndOfDayTime_DoesNotShowTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 23, 59, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026"); // 23:59 => only date
    }

    [Fact]
    public void ToFriendlyDateTimeString_NormalTime_ShowsDateAndTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 14, 30, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026 14:30");
    }

    [Fact]
    public void ToFriendlyDateTimeString_MorningTime_ShowsDateAndTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 9, 5, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026 09:05");
    }

    [Fact]
    public void ToFriendlyDateTimeString_EveningTime_ShowsDateAndTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 20, 45, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026 20:45");
    }

    [Fact]
    public void ToFriendlyDateTimeString_OneMinutePastMidnight_ShowsTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 0, 1, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026 00:01");
    }

    [Fact]
    public void ToFriendlyDateTimeString_TwentyThreeFiftyEight_ShowsTime()
    {
        // Arrange
        var testDate = new DateTime(2026, 5, 15, 23, 58, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("15.05.2026 23:58");
    }

    [Fact]
    public void ToFriendlyDateTimeString_LeapYearDate_FormatsCorrectly()
    {
        // Arrange
        var testDate = new DateTime(2024, 2, 29, 15, 30, 0); // Leap year

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("29.02.2024 15:30");
    }

    [Fact]
    public void ToFriendlyDateTimeString_NewYearsDay_FormatsCorrectly()
    {
        // Arrange
        var testDate = new DateTime(2027, 1, 1, 12, 0, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("01.01.2027 12:00");
    }

    [Fact]
    public void ToFriendlyDateTimeString_ChristmasEve_FormatsCorrectly()
    {
        // Arrange
        var testDate = new DateTime(2026, 12, 24, 18, 0, 0);

        // Act
        var result = TkOlympApp.Helpers.DateHelpers.ToFriendlyDateTimeString(testDate);

        // Assert
        result.Should().NotBeNull();
        result.Should().Be("24.12.2026 18:00");
    }

    // Note: Testy pro "dnes" a "zítra" vyžadují mockování DateTime.Now
    // nebo extrakci do testovatelné komponenty s time provider.
}

