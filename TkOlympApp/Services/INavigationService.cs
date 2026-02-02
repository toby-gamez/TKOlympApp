namespace TkOlympApp.Services;

/// <summary>
/// Service for handling navigation between pages in the application.
/// Abstracts Shell navigation for testability.
/// </summary>
public interface INavigationService
{
    /// <summary>
    /// Navigate to a route asynchronously.
    /// </summary>
    /// <param name="route">The route to navigate to (e.g., "EventPage").</param>
    /// <returns>A task representing the navigation operation.</returns>
    Task NavigateToAsync(string route);

    /// <summary>
    /// Navigate to a route with parameters asynchronously.
    /// </summary>
    /// <param name="route">The route to navigate to (e.g., "EventPage").</param>
    /// <param name="parameters">Dictionary of query string parameters.</param>
    /// <returns>A task representing the navigation operation.</returns>
    Task NavigateToAsync(string route, Dictionary<string, object> parameters);

    /// <summary>
    /// Navigate back to the previous page.
    /// </summary>
    /// <returns>A task representing the navigation operation.</returns>
    Task GoBackAsync();

    /// <summary>
    /// Navigate to a route, replacing the current page in the navigation stack.
    /// </summary>
    /// <param name="route">The route to navigate to.</param>
    /// <returns>A task representing the navigation operation.</returns>
    Task NavigateToAsync(string route, bool animate);
}
