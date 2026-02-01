using Microsoft.Extensions.Logging;
using Microsoft.Maui.Controls.Hosting;
using Microsoft.Maui.Hosting;
using MauiIcons.Material;
using MauiIcons.FontAwesome;
using MauiIcons.Fluent;
using Indiko.Maui.Controls.SelectableLabel;
using Plugin.LocalNotification;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Microsoft.Maui.Storage;

namespace TkOlympApp;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseLocalNotification()
            .UseMaterialMauiIcons()
            .UseFontAwesomeMauiIcons()
            .UseFluentMauiIcons()
            .UseSelectableLabel()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
                fonts.AddFont("OpenSans-Italic.ttf", "OpenSansItalic");
            });

#if DEBUG
        builder.Logging.AddDebug();
#endif

        // Register platform abstractions
        builder.Services.AddSingleton<ISecureStorage>(SecureStorage.Default);
        builder.Services.AddSingleton<IPreferences>(Preferences.Default);

        // Register HTTP clients with IHttpClientFactory
        // AddHttpClient automatically registers the typed client as Transient
        builder.Services.AddHttpClient<IGraphQlClient, GraphQlClientImplementation>(client =>
        {
            client.BaseAddress = new Uri(AppConstants.BaseApiUrl);
            client.DefaultRequestHeaders.Add(AppConstants.TenantHeader, AppConstants.TenantId);
            client.Timeout = TimeSpan.FromSeconds(AppConstants.DefaultTimeoutSeconds);
        })
        // Most services call GraphQlClient.* (typed client). They require the same auth behavior as legacy AuthService.Http.
        .AddHttpMessageHandler<AuthDelegatingHandler>();

        // Bare client for auth refresh (no delegating handler to avoid recursion)
        builder.Services.AddHttpClient("AuthService.Bare", client =>
        {
            client.BaseAddress = new Uri(AppConstants.BaseApiUrl);
            client.DefaultRequestHeaders.Add(AppConstants.TenantHeader, AppConstants.TenantId);
            client.Timeout = TimeSpan.FromSeconds(AppConstants.AuthTimeoutSeconds);
        });

        // Auth delegating handler (registered as transient, created per HttpClient)
        builder.Services.AddTransient<AuthDelegatingHandler>();

        // Auth client with delegating handler
        // AddHttpClient automatically registers IAuthService -> AuthServiceImplementation as Transient
        builder.Services.AddHttpClient<IAuthService, AuthServiceImplementation>((sp, client) =>
        {
            client.BaseAddress = new Uri(AppConstants.BaseApiUrl);
            client.DefaultRequestHeaders.Add(AppConstants.TenantHeader, AppConstants.TenantId);
            // NOTE: IAuthService.Http is used by many legacy pages for general GraphQL queries.
            // Keep those on the default timeout; the short auth timeout is reserved for the bare refresh client.
            client.Timeout = TimeSpan.FromSeconds(AppConstants.DefaultTimeoutSeconds);
        })
        .AddHttpMessageHandler<AuthDelegatingHandler>();

        // Services are already registered by AddHttpClient above (as Transient)
        // No need to re-register them - that would create new instances without proper HttpClient!
        // TODO: Refactor EventService to instance-based
        // builder.Services.AddTransient<IEventService, EventService>();

        // Register Pages for DI
        builder.Services.AddTransient<AppShell>();

        // Initialize notification manager singleton so platform code can forward intents
        NotificationManagerService.EnsureInitialized();

        var app = builder.Build();
        
        // Initialize centralized logger service
        var loggerFactory = app.Services.GetRequiredService<ILoggerFactory>();
        LoggerService.Initialize(loggerFactory);
        
        // Initialize services with logger
        var logger = loggerFactory.CreateLogger<App>();
        EventNotificationService.Initialize(logger);

        // Initialize static wrappers with DI instances (for backward compatibility).
        // This is safe now because AuthDelegatingHandler no longer depends on IAuthService.
        var authService = app.Services.GetRequiredService<IAuthService>();
        var graphQlClient = app.Services.GetRequiredService<IGraphQlClient>();
        AuthService.SetInstance(authService);
        GraphQlClient.SetInstance(graphQlClient);

        return app;
    }
}