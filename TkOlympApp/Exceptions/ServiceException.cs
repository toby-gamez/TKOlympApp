namespace TkOlympApp.Exceptions;

/// <summary>
/// Base exception for all service-layer errors.
/// Provides context for retry logic and HTTP status codes.
/// </summary>
public class ServiceException : Exception
{
    /// <summary>
    /// Indicates if this error is transient (network issues, timeouts)
    /// and can be retried.
    /// </summary>
    public bool IsTransient { get; }
    
    /// <summary>
    /// HTTP status code if the error originated from an HTTP response.
    /// </summary>
    public int? HttpStatusCode { get; }
    
    /// <summary>
    /// Additional context data for structured logging.
    /// </summary>
    public Dictionary<string, object?> Context { get; } = new();

    public ServiceException(
        string message, 
        Exception? innerException = null,
        bool isTransient = false,
        int? httpStatusCode = null)
        : base(message, innerException)
    {
        IsTransient = isTransient;
        HttpStatusCode = httpStatusCode;
    }

    /// <summary>
    /// Add context data for logging.
    /// </summary>
    public ServiceException WithContext(string key, object? value)
    {
        Context[key] = value;
        return this;
    }
}
