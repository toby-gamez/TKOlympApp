using Microsoft.Extensions.Logging;
using System.Diagnostics;
using System.Runtime.CompilerServices;

namespace TkOlympApp.Services;

/// <summary>
/// Centrální logging service s podporou strukturovaného logování.
/// Poskytuje konzistentní API pro logování napříč celou aplikací.
/// Připraveno pro integraci s Application Insights nebo Sentry.
/// </summary>
public static class LoggerService
{
    private static ILoggerFactory? _loggerFactory;
    private static readonly object Lock = new();

    /// <summary>
    /// Inicializuje logger factory. Mělo by být voláno při startu aplikace v MauiProgram.cs
    /// </summary>
    public static void Initialize(ILoggerFactory loggerFactory)
    {
        lock (Lock)
        {
            _loggerFactory = loggerFactory;
        }
    }

    /// <summary>
    /// Získá logger pro konkrétní typ. S fallback mechanismem pokud není inicializováno.
    /// </summary>
    public static ILogger<T> CreateLogger<T>()
    {
        lock (Lock)
        {
            if (_loggerFactory == null)
            {
                // Fallback pokud není inicializováno - pouze Debug.WriteLine
                return new FallbackLogger<T>();
            }
            return _loggerFactory.CreateLogger<T>();
        }
    }

    /// <summary>
    /// Získá logger pro konkrétní název kategorie. S fallback mechanismem pokud není inicializováno.
    /// </summary>
    public static ILogger CreateLogger(string categoryName)
    {
        lock (Lock)
        {
            if (_loggerFactory == null)
            {
                return new FallbackLogger(categoryName);
            }
            return _loggerFactory.CreateLogger(categoryName);
        }
    }

    /// <summary>
    /// Extension metoda pro logování výkonu operace.
    /// Použití: using (LoggerService.LogPerformance&lt;T&gt;("OperationName")) { ... }
    /// </summary>
    public static IDisposable LogPerformance<T>(string operationName, [CallerMemberName] string? callerName = null)
    {
        return new PerformanceLogger<T>(operationName, callerName);
    }

    /// <summary>
    /// Bezpečné logování výjimky v catch bloku - nikdy nevyhazuje exception.
    /// </summary>
    public static void SafeLogError<T>(Exception exception, string message, params object?[] args)
    {
        try
        {
            CreateLogger<T>().LogError(exception, message, args);
        }
        catch
        {
            // Fallback na Debug.WriteLine pokud logger selže
            try
            {
                Debug.WriteLine($"[ERROR] {typeof(T).Name}: {string.Format(message, args)}\n{exception}");
            }
            catch
            {
                // Absolutní poslední fallback
                Debug.WriteLine($"[ERROR] {typeof(T).Name}: Logging failed");
            }
        }
    }

    /// <summary>
    /// Bezpečné logování warningů - nikdy nevyhazuje exception.
    /// </summary>
    public static void SafeLogWarning<T>(string message, params object?[] args)
    {
        try
        {
            CreateLogger<T>().LogWarning(message, args);
        }
        catch
        {
            try
            {
                Debug.WriteLine($"[WARNING] {typeof(T).Name}: {string.Format(message, args)}");
            }
            catch
            {
                Debug.WriteLine($"[WARNING] {typeof(T).Name}: Logging failed");
            }
        }
    }

    /// <summary>
    /// Bezpečné logování warningů podle názvu kategorie (pro statické třídy).
    /// </summary>
    public static void SafeLogWarning(string categoryName, string message, params object?[] args)
    {
        try
        {
            CreateLogger(categoryName).LogWarning(message, args);
        }
        catch
        {
            try
            {
                Debug.WriteLine($"[WARNING] {categoryName}: {string.Format(message, args)}");
            }
            catch
            {
                Debug.WriteLine($"[WARNING] {categoryName}: Logging failed");
            }
        }
    }

    private class PerformanceLogger<T> : IDisposable
    {
        private readonly string _operationName;
        private readonly string? _callerName;
        private readonly Stopwatch _stopwatch;
        private readonly ILogger<T> _logger;

        public PerformanceLogger(string operationName, string? callerName)
        {
            _operationName = operationName;
            _callerName = callerName;
            _logger = CreateLogger<T>();
            _stopwatch = Stopwatch.StartNew();
            _logger.LogTrace("Starting {Operation} from {Caller}", _operationName, _callerName);
        }

        public void Dispose()
        {
            _stopwatch.Stop();
            var elapsed = _stopwatch.ElapsedMilliseconds;
            
            if (elapsed > 1000)
            {
                _logger.LogWarning("{Operation} took {ElapsedMs}ms (from {Caller}) - consider optimization",
                    _operationName, elapsed, _callerName);
            }
            else if (elapsed > 500)
            {
                _logger.LogInformation("{Operation} took {ElapsedMs}ms (from {Caller})",
                    _operationName, elapsed, _callerName);
            }
            else
            {
                _logger.LogTrace("{Operation} completed in {ElapsedMs}ms (from {Caller})",
                    _operationName, elapsed, _callerName);
            }
        }
    }

    /// <summary>
    /// Fallback logger když ILoggerFactory není dostupný - pouze Debug.WriteLine.
    /// </summary>
    private class FallbackLogger<T> : ILogger<T>
    {
        private readonly string _categoryName = typeof(T).Name;

        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
        {
            var message = formatter(state, exception);
            var logMessage = $"[{logLevel}] {_categoryName}: {message}";
            if (exception != null)
            {
                logMessage += $"\n{exception}";
            }
            Debug.WriteLine(logMessage);
        }
    }

    /// <summary>
    /// Fallback logger bez typu.
    /// </summary>
    private class FallbackLogger : ILogger
    {
        private readonly string _categoryName;

        public FallbackLogger(string categoryName)
        {
            _categoryName = categoryName;
        }

        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
        {
            var message = formatter(state, exception);
            var logMessage = $"[{logLevel}] {_categoryName}: {message}";
            if (exception != null)
            {
                logMessage += $"\n{exception}";
            }
            Debug.WriteLine(logMessage);
        }
    }
}
