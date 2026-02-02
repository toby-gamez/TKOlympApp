using Microsoft.Extensions.Logging;

namespace TkOlympApp.Tests.Mocks;

/// <summary>
/// A no-op logger implementation for testing.
/// </summary>
public class NullLogger<T> : ILogger<T>
{
    public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

    public bool IsEnabled(LogLevel logLevel) => false;

    public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
    {
        // No-op
    }
}
