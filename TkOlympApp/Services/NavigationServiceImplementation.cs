namespace TkOlympApp.Services;

/// <summary>
/// Implementation of INavigationService using .NET MAUI Shell navigation.
/// </summary>
public class NavigationServiceImplementation : INavigationService
{
    /// <inheritdoc />
    public Task NavigateToAsync(string route)
    {
        return Shell.Current.GoToAsync(route);
    }

    /// <inheritdoc />
    public Task NavigateToAsync(string route, Dictionary<string, object> parameters)
    {
        return Shell.Current.GoToAsync(route, parameters);
    }

    /// <inheritdoc />
    public Task GoBackAsync()
    {
        return Shell.Current.GoToAsync("..");
    }

    /// <inheritdoc />
    public Task NavigateToAsync(string route, bool animate)
    {
        return Shell.Current.GoToAsync(route, animate);
    }
}
