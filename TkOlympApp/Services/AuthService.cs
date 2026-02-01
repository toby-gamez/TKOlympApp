using System.Net.Http;
using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Storage;
using Microsoft.Extensions.Logging;
using TkOlympApp.Helpers;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

/// <summary>
/// Static wrapper for AuthService to maintain backward compatibility during DI migration.
/// TODO: Remove after all call sites are updated to use IAuthService via DI.
/// </summary>
public static class AuthService
{
    private static IAuthService? _instance;

    /// <summary>
    /// Lazily resolves the IAuthService instance from DI container.
    /// </summary>
    private static IAuthService Instance
    {
        get
        {
            if (_instance == null)
            {
                var services = Application.Current?.Handler?.MauiContext?.Services;
                if (services == null)
                    throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
                
                _instance = services.GetRequiredService<IAuthService>();
            }
            return _instance;
        }
    }

    internal static void SetInstance(IAuthService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    public static HttpClient Http => Instance.Http;

    public static async Task InitializeAsync(CancellationToken ct = default)
    {
        await Instance.InitializeAsync(ct);
    }

    public static async Task<bool> TryRefreshIfNeededAsync(CancellationToken ct = default)
    {
        return await Instance.TryRefreshIfNeededAsync(ct);
    }

    public static async Task<string?> RefreshJwtAsync(CancellationToken ct = default)
    {
        return await Instance.RefreshJwtAsync(ct);
    }

    public static async Task<bool> HasTokenAsync(CancellationToken ct = default)
    {
        return await Instance.HasTokenAsync(ct);
    }

    public static async Task<string?> LoginAsync(string login, string passwd, CancellationToken ct = default)
    {
        return await Instance.LoginAsync(login, passwd, ct);
    }

    public static async Task LogoutAsync(CancellationToken ct = default)
    {
        await Instance.LogoutAsync(ct);
    }
}
