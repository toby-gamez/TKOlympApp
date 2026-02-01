namespace TkOlympApp.Exceptions;

/// <summary>
/// Exception thrown when authentication or authorization fails.
/// </summary>
public class AuthenticationException : ServiceException
{
    public enum AuthErrorType
    {
        InvalidCredentials,
        TokenExpired,
        TokenInvalid,
        Unauthorized,
        NetworkError
    }

    public AuthErrorType ErrorType { get; }

    public AuthenticationException(
        string message,
        AuthErrorType errorType,
        Exception? innerException = null,
        int? httpStatusCode = null)
        : base(message, innerException, isTransient: errorType == AuthErrorType.NetworkError, httpStatusCode)
    {
        ErrorType = errorType;
    }
}
