using Microsoft.Extensions.Logging;

namespace TkOlympApp.Services;

/// <summary>
/// Centralized logging service accessible from static classes and code-behind.
/// Provides structured logging with proper log levels and exception handling.
/// </summary>
public static class LoggerService
{
    private static ILoggerFactory? _loggerFactory;
    
    /// <summary>
    /// Initialize the logger factory. Must be called during app startup in MauiProgram.cs
    /// </summary>
    public static void Initialize(ILoggerFactory loggerFactory)
    {
        _loggerFactory = loggerFactory;
    }
    
    /// <summary>
    /// Create a logger for a specific category (usually class name)
    /// </summary>
    public static ILogger<T> CreateLogger<T>()
    {
        if (_loggerFactory == null)
        {
            throw new InvalidOperationException(
                "LoggerService not initialized. Call LoggerService.Initialize() in MauiProgram.cs");
        }
        
        return _loggerFactory.CreateLogger<T>();
    }
    
    /// <summary>
    /// Create a logger for a specific category name
    /// </summary>
    public static ILogger CreateLogger(string categoryName)
    {
        if (_loggerFactory == null)
        {
            throw new InvalidOperationException(
                "LoggerService not initialized. Call LoggerService.Initialize() in MauiProgram.cs");
        }
        
        return _loggerFactory.CreateLogger(categoryName);
    }
}
