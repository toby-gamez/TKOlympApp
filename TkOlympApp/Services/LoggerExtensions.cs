using System.Diagnostics;
using Microsoft.Extensions.Logging;
using TkOlympApp.Exceptions;

namespace TkOlympApp.Services;

/// <summary>
/// Extension metody pro strukturované logování s bohatým kontextem.
/// Poskytuje konzistentní způsob logování operací, GraphQL dotazů a autentizace.
/// </summary>
public static class LoggerExtensions
{
    /// <summary>
    /// Loguje začátek operace s automatickým měřením času.
    /// Použití: using (logger.BeginOperation("LoadEvents", ("userId", 123))) { ... }
    /// </summary>
    public static IDisposable BeginOperation(
        this ILogger logger,
        string operationName,
        params (string Key, object? Value)[] context)
    {
        return new OperationScope(logger, operationName, context);
    }

    /// <summary>
    /// Loguje úspěšné dokončení operace s výsledkem a kontextem.
    /// </summary>
    public static void LogOperationSuccess<T>(
        this ILogger logger,
        string operationName,
        T result,
        TimeSpan duration,
        params (string Key, object? Value)[] context)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("Operation", operationName),
            new("DurationMs", duration.TotalMilliseconds),
            new("Success", true)
        };

        foreach (var (key, value) in context)
        {
            state.Add(new(key, value));
        }

        // Přidá metadata o výsledku bez citlivých dat
        if (result is System.Collections.ICollection collection)
        {
            state.Add(new("ResultCount", collection.Count));
        }
        else if (result != null)
        {
            state.Add(new("ResultType", result.GetType().Name));
        }

        logger.Log(
            LogLevel.Information,
            new EventId(1000, "OperationSuccess"),
            state,
            null,
            (s, _) => $"{operationName} úspěšně dokončeno za {duration.TotalMilliseconds:F2}ms"
        );
    }

    /// <summary>
    /// Loguje neúspěšné dokončení operace s výjimkou a kontextem.
    /// Automaticky rozpoznává transient vs. permanent failures.
    /// </summary>
    public static void LogOperationFailure(
        this ILogger logger,
        string operationName,
        Exception exception,
        TimeSpan duration,
        params (string Key, object? Value)[] context)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("Operation", operationName),
            new("DurationMs", duration.TotalMilliseconds),
            new("Success", false),
            new("ExceptionType", exception.GetType().Name)
        };

        foreach (var (key, value) in context)
        {
            state.Add(new(key, value));
        }

        // Přidá specifický kontext ze ServiceException
        if (exception is ServiceException serviceEx)
        {
            state.Add(new("IsTransient", serviceEx.IsTransient));
            if (serviceEx.HttpStatusCode.HasValue)
                state.Add(new("HttpStatusCode", serviceEx.HttpStatusCode.Value));
            
            foreach (var (key, value) in serviceEx.Context)
            {
                state.Add(new($"Context_{key}", value));
            }
        }

        // Přidá specifický kontext z GraphQLException
        if (exception is GraphQLException graphQLEx)
        {
            state.Add(new("GraphQLErrorCount", graphQLEx.Errors.Count));
            state.Add(new("GraphQLErrors", string.Join("; ", graphQLEx.Errors)));
        }

        // Transient chyby = Warning, permanent = Error
        var logLevel = exception is ServiceException se && se.IsTransient
            ? LogLevel.Warning
            : LogLevel.Error;

        logger.Log(
            logLevel,
            new EventId(1001, "OperationFailure"),
            state,
            exception,
            (_, e) => $"{operationName} selhalo po {duration.TotalMilliseconds:F2}ms: {e?.Message}"
        );
    }

    /// <summary>
    /// Loguje GraphQL request s detaily query a proměnných (bez citlivých dat).
    /// </summary>
    public static void LogGraphQLRequest(
        this ILogger logger,
        string queryName,
        Dictionary<string, object>? variables = null)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("QueryName", queryName),
            new("HasVariables", variables != null && variables.Count > 0)
        };

        if (variables != null && variables.Count > 0)
        {
            state.Add(new("VariableCount", variables.Count));
            // Loguje klíče proměnných, ale ne hodnoty (ochrana soukromí)
            state.Add(new("VariableKeys", string.Join(", ", variables.Keys)));
        }

        logger.Log(
            LogLevel.Debug,
            new EventId(2000, "GraphQLRequest"),
            state,
            null,
            (_, __) => $"GraphQL dotaz: {queryName}"
        );
    }

    /// <summary>
    /// Loguje GraphQL response s metrikami výkonu.
    /// </summary>
    public static void LogGraphQLResponse<T>(
        this ILogger logger,
        string queryName,
        T? data,
        TimeSpan duration,
        int? statusCode = null)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("QueryName", queryName),
            new("DurationMs", duration.TotalMilliseconds),
            new("HasData", data != null)
        };

        if (statusCode.HasValue)
            state.Add(new("StatusCode", statusCode.Value));

        if (data is System.Collections.ICollection collection)
        {
            state.Add(new("ResultCount", collection.Count));
        }

        var logLevel = duration.TotalMilliseconds > 2000 ? LogLevel.Warning : LogLevel.Debug;

        logger.Log(
            logLevel,
            new EventId(2001, "GraphQLResponse"),
            state,
            null,
            (_, __) => $"GraphQL odpověď: {queryName} ({duration.TotalMilliseconds:F2}ms)"
        );
    }

    /// <summary>
    /// Loguje autentizační událost (login, logout, token refresh).
    /// </summary>
    public static void LogAuthenticationEvent(
        this ILogger logger,
        string eventType,
        bool success,
        string? userId = null,
        string? reason = null)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("EventType", eventType),
            new("Success", success)
        };

        if (!string.IsNullOrEmpty(userId))
            state.Add(new("UserId", userId));
        
        if (!string.IsNullOrEmpty(reason))
            state.Add(new("Reason", reason));

        var logLevel = success ? LogLevel.Information : LogLevel.Warning;

        logger.Log(
            logLevel,
            new EventId(3000, "Authentication"),
            state,
            null,
            (_, __) => $"Autentizace: {eventType} - {(success ? "Úspěch" : "Selhání")}{(reason != null ? $" ({reason})" : "")}"
        );
    }

    /// <summary>
    /// Loguje výkonnostní metriku (užitečné pro profilování).
    /// </summary>
    public static void LogPerformanceMetric(
        this ILogger logger,
        string metricName,
        double value,
        string unit = "ms",
        params (string Key, object? Value)[] context)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("MetricName", metricName),
            new("Value", value),
            new("Unit", unit)
        };

        foreach (var (key, val) in context)
        {
            state.Add(new(key, val));
        }

        var logLevel = value switch
        {
            > 5000 => LogLevel.Warning, // Operace > 5s
            > 2000 => LogLevel.Information, // Operace > 2s
            _ => LogLevel.Debug
        };

        logger.Log(
            logLevel,
            new EventId(4000, "PerformanceMetric"),
            state,
            null,
            (_, __) => $"Metrika: {metricName} = {value:F2}{unit}"
        );
    }

    /// <summary>
    /// Loguje zrušení operace uživatelem nebo timeoutem.
    /// </summary>
    public static void LogOperationCancelled(
        this ILogger logger,
        string operationName,
        TimeSpan duration,
        string? reason = null)
    {
        var state = new List<KeyValuePair<string, object?>>
        {
            new("Operation", operationName),
            new("DurationMs", duration.TotalMilliseconds),
            new("Cancelled", true)
        };

        if (!string.IsNullOrEmpty(reason))
            state.Add(new("Reason", reason));

        logger.Log(
            LogLevel.Information,
            new EventId(1002, "OperationCancelled"),
            state,
            null,
            (_, __) => $"{operationName} zrušeno po {duration.TotalMilliseconds:F2}ms{(reason != null ? $": {reason}" : "")}"
        );
    }

    /// <summary>
    /// Vnitřní třída pro automatické měření času operace s pomocí 'using' statement.
    /// </summary>
    private class OperationScope : IDisposable
    {
        private readonly ILogger _logger;
        private readonly string _operationName;
        private readonly (string Key, object? Value)[] _context;
        private readonly Stopwatch _stopwatch;
        private bool _disposed;

        public OperationScope(
            ILogger logger,
            string operationName,
            (string Key, object? Value)[] context)
        {
            _logger = logger;
            _operationName = operationName;
            _context = context;
            _stopwatch = Stopwatch.StartNew();

            var state = new List<KeyValuePair<string, object?>>
            {
                new("Operation", operationName),
                new("Phase", "Start")
            };

            foreach (var (key, value) in context)
            {
                state.Add(new(key, value));
            }

            _logger.Log(
                LogLevel.Debug,
                new EventId(1003, "OperationStart"),
                state,
                null,
                (_, __) => $"{operationName} zahájeno"
            );
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;

            _stopwatch.Stop();
            
            var state = new List<KeyValuePair<string, object?>>
            {
                new("Operation", _operationName),
                new("Phase", "End"),
                new("DurationMs", _stopwatch.Elapsed.TotalMilliseconds)
            };

            foreach (var (key, value) in _context)
            {
                state.Add(new(key, value));
            }

            var logLevel = _stopwatch.Elapsed.TotalMilliseconds > 2000 
                ? LogLevel.Information 
                : LogLevel.Debug;

            _logger.Log(
                logLevel,
                new EventId(1004, "OperationEnd"),
                state,
                null,
                (_, __) => $"{_operationName} ukončeno ({_stopwatch.Elapsed.TotalMilliseconds:F2}ms)"
            );
        }
    }
}
