using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using TkOlympApp.Exceptions;
using TkOlympApp.Services;

namespace TkOlympApp.Tests.Services;

public class LoggerExtensionsTests
{
    private readonly TestLogger _logger;

    public LoggerExtensionsTests()
    {
        _logger = new TestLogger();
    }

    [Fact]
    public void BeginOperation_LogsOperationStartAndEnd()
    {
        // Arrange & Act
        using (_logger.BeginOperation("TestOperation", ("UserId", 123)))
        {
            // Simulate some work
            Thread.Sleep(10);
        }

        // Assert
        Assert.Contains(_logger.Logs, log => 
            log.Level == LogLevel.Debug && 
            log.Message.Contains("TestOperation") &&
            log.Message.Contains("zahájeno"));
        
        Assert.Contains(_logger.Logs, log => 
            log.Level == LogLevel.Debug && 
            log.Message.Contains("TestOperation") &&
            log.Message.Contains("ukončeno"));
    }

    [Fact]
    public void LogOperationSuccess_LogsSuccessWithDuration()
    {
        // Arrange
        var result = new List<string> { "Item1", "Item2", "Item3" };
        var duration = TimeSpan.FromMilliseconds(250);

        // Act
        _logger.LogOperationSuccess("LoadData", result, duration, ("Source", "Database"));

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Information);
        Assert.Contains("LoadData", logEntry.Message);
        Assert.Contains("úspěšně dokončeno", logEntry.Message);
        Assert.Contains("250", logEntry.Message); // Duration
        
        // Verify context
        Assert.Contains(logEntry.State, kvp => kvp.Key == "Operation" && kvp.Value?.ToString() == "LoadData");
        Assert.Contains(logEntry.State, kvp => kvp.Key == "Success" && (bool)kvp.Value! == true);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "ResultCount" && (int)kvp.Value! == 3);
    }

    [Fact]
    public void LogOperationFailure_WithServiceException_LogsAsWarningIfTransient()
    {
        // Arrange
        var exception = new ServiceException("Network timeout", null, isTransient: true);
        var duration = TimeSpan.FromMilliseconds(500);

        // Act
        _logger.LogOperationFailure("FetchData", exception, duration, ("Retry", 2));

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Warning);
        Assert.Contains("FetchData", logEntry.Message);
        Assert.Contains("selhalo", logEntry.Message);
        
        // Verify IsTransient is logged
        Assert.Contains(logEntry.State, kvp => kvp.Key == "IsTransient" && (bool)kvp.Value! == true);
    }

    [Fact]
    public void LogOperationFailure_WithNonTransientException_LogsAsError()
    {
        // Arrange
        var exception = new ServiceException("Database constraint violation", null, isTransient: false);
        var duration = TimeSpan.FromMilliseconds(100);

        // Act
        _logger.LogOperationFailure("SaveData", exception, duration);

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Error);
        Assert.Contains("SaveData", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "IsTransient" && (bool)kvp.Value! == false);
    }

    [Fact]
    public void LogOperationFailure_WithGraphQLException_LogsErrorDetails()
    {
        // Arrange
        var errors = new List<string> { "Invalid query", "Missing field" };
        var exception = new GraphQLException("GraphQL failed", errors);
        var duration = TimeSpan.FromMilliseconds(200);

        // Act
        _logger.LogOperationFailure("ExecuteQuery", exception, duration);

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Error);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "GraphQLErrorCount" && (int)kvp.Value! == 2);
        Assert.Contains(logEntry.State, kvp => 
            kvp.Key == "GraphQLErrors" && 
            kvp.Value?.ToString()!.Contains("Invalid query") == true);
    }

    [Fact]
    public void LogGraphQLRequest_LogsQueryNameAndVariables()
    {
        // Arrange
        var variables = new Dictionary<string, object>
        {
            { "userId", 123 },
            { "startDate", "2026-02-01" }
        };

        // Act
        _logger.LogGraphQLRequest("GetUserEvents", variables);

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Debug);
        Assert.Contains("GetUserEvents", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "QueryName");
        Assert.Contains(logEntry.State, kvp => kvp.Key == "VariableCount" && (int)kvp.Value! == 2);
        Assert.Contains(logEntry.State, kvp => 
            kvp.Key == "VariableKeys" && 
            kvp.Value?.ToString()!.Contains("userId") == true);
    }

    [Fact]
    public void LogGraphQLResponse_WithSlowResponse_LogsWarning()
    {
        // Arrange
        var data = new List<string> { "Result1" };
        var duration = TimeSpan.FromMilliseconds(2500); // > 2000ms

        // Act
        _logger.LogGraphQLResponse("GetData", data, duration, 200);

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Warning);
        Assert.Contains("GetData", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "DurationMs" && (double)kvp.Value! > 2000);
    }

    [Fact]
    public void LogGraphQLResponse_WithFastResponse_LogsDebug()
    {
        // Arrange
        var data = new List<string> { "Result1" };
        var duration = TimeSpan.FromMilliseconds(150); // < 2000ms

        // Act
        _logger.LogGraphQLResponse("GetData", data, duration, 200);

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Debug);
        Assert.Contains("GetData", logEntry.Message);
    }

    [Fact]
    public void LogAuthenticationEvent_WithSuccess_LogsInformation()
    {
        // Act
        _logger.LogAuthenticationEvent("Login", success: true, userId: "user123");

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Information);
        Assert.Contains("Login", logEntry.Message);
        Assert.Contains("Úspěch", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "UserId" && kvp.Value?.ToString() == "user123");
    }

    [Fact]
    public void LogAuthenticationEvent_WithFailure_LogsWarning()
    {
        // Act
        _logger.LogAuthenticationEvent("Login", success: false, reason: "Invalid password");

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Warning);
        Assert.Contains("Login", logEntry.Message);
        Assert.Contains("Selhání", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "Reason" && kvp.Value?.ToString() == "Invalid password");
    }

    [Fact]
    public void LogPerformanceMetric_WithSlowOperation_LogsWarning()
    {
        // Act
        _logger.LogPerformanceMetric("DatabaseQuery", 6500, "ms", ("Table", "Users"));

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Warning);
        Assert.Contains("DatabaseQuery", logEntry.Message);
        Assert.Contains("6500", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "Value" && (double)kvp.Value! == 6500);
    }

    [Fact]
    public void LogOperationCancelled_LogsCancellationWithReason()
    {
        // Act
        _logger.LogOperationCancelled("LongOperation", TimeSpan.FromSeconds(5), "User navigated away");

        // Assert
        var logEntry = Assert.Single(_logger.Logs, log => log.Level == LogLevel.Information);
        Assert.Contains("LongOperation", logEntry.Message);
        Assert.Contains("zrušeno", logEntry.Message);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "Cancelled" && (bool)kvp.Value! == true);
        Assert.Contains(logEntry.State, kvp => kvp.Key == "Reason" && kvp.Value?.ToString() == "User navigated away");
    }

    /// <summary>
    /// Test logger that captures all log entries for verification.
    /// </summary>
    private class TestLogger : ILogger
    {
        public List<LogEntry> Logs { get; } = new();

        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(
            LogLevel logLevel,
            EventId eventId,
            TState state,
            Exception? exception,
            Func<TState, Exception?, string> formatter)
        {
            var stateList = new List<KeyValuePair<string, object?>>();
            if (state is IEnumerable<KeyValuePair<string, object?>> enumerable)
            {
                stateList.AddRange(enumerable);
            }

            Logs.Add(new LogEntry
            {
                Level = logLevel,
                EventId = eventId,
                Message = formatter(state, exception),
                Exception = exception,
                State = stateList
            });
        }

        public class LogEntry
        {
            public LogLevel Level { get; init; }
            public EventId EventId { get; init; }
            public string Message { get; init; } = string.Empty;
            public Exception? Exception { get; init; }
            public List<KeyValuePair<string, object?>> State { get; init; } = new();
        }
    }
}
