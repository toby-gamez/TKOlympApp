namespace TkOlympApp.Services.Abstractions;

/// <summary>
/// Abstraction for GraphQL client operations.
/// Enables dependency injection and testability by decoupling GraphQL communication
/// from static implementations.
/// </summary>
public interface IGraphQlClient
{
    /// <summary>
    /// Executes a GraphQL query and deserializes the response to the specified type.
    /// Uses async stream deserialization for optimal performance with large responses.
    /// </summary>
    /// <typeparam name="T">The type to deserialize the response data into.</typeparam>
    /// <param name="query">The GraphQL query string.</param>
    /// <param name="variables">Optional dictionary of query variables.</param>
    /// <param name="ct">Cancellation token to allow request cancellation.</param>
    /// <returns>The deserialized response data.</returns>
    /// <exception cref="InvalidOperationException">Thrown when the request fails or GraphQL returns errors.</exception>
    Task<T> PostAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default);

    /// <summary>
    /// Executes a GraphQL query and returns both parsed data and raw JSON response.
    /// Useful for debugging and logging purposes.
    /// Note: This method reads the full response body, use PostAsync for better performance.
    /// </summary>
    /// <typeparam name="T">The type to deserialize the response data into.</typeparam>
    /// <param name="query">The GraphQL query string.</param>
    /// <param name="variables">Optional dictionary of query variables.</param>
    /// <param name="ct">Cancellation token to allow request cancellation.</param>
    /// <returns>A tuple containing the deserialized data and raw JSON response.</returns>
    /// <exception cref="InvalidOperationException">Thrown when the request fails or GraphQL returns errors.</exception>
    Task<(T Data, string Raw)> PostWithRawAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default);
}
