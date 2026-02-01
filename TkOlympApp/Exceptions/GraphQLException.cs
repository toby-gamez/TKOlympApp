namespace TkOlympApp.Exceptions;

/// <summary>
/// Exception thrown when GraphQL API returns errors.
/// </summary>
public class GraphQLException : ServiceException
{
    /// <summary>
    /// List of GraphQL error messages returned by the server.
    /// </summary>
    public List<string> Errors { get; }
    
    /// <summary>
    /// Raw GraphQL response body for debugging.
    /// </summary>
    public string? RawResponse { get; }

    public GraphQLException(
        string message,
        List<string> errors,
        string? rawResponse = null,
        Exception? innerException = null)
        : base(message, innerException, isTransient: false)
    {
        Errors = errors;
        RawResponse = rawResponse;
    }

    public GraphQLException(string message, string error, string? rawResponse = null)
        : this(message, new List<string> { error }, rawResponse)
    {
    }
}
