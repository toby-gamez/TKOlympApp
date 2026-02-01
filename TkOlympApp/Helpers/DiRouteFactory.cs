using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;

namespace TkOlympApp.Helpers;

/// <summary>
/// Shell route factory that resolves pages from the DI container.
/// Enables constructor injection on pages used via Shell routing.
/// </summary>
public sealed class DiRouteFactory : RouteFactory
{
    private readonly IServiceProvider _services;
    private readonly Type _pageType;

    public DiRouteFactory(IServiceProvider services, Type pageType)
    {
        _services = services ?? throw new ArgumentNullException(nameof(services));
        _pageType = pageType ?? throw new ArgumentNullException(nameof(pageType));
    }

    public override Element GetOrCreate()
    {
        return (Element)_services.GetRequiredService(_pageType);
    }

    public override Element GetOrCreate(IServiceProvider services)
    {
        var provider = services ?? _services;
        return (Element)provider.GetRequiredService(_pageType);
    }
}
