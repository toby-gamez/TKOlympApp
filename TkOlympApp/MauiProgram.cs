using Microsoft.Extensions.Logging;
using Microsoft.Maui.Controls.Hosting;
using Microsoft.Maui.Hosting;
using MauiIcons.Material;
using MauiIcons.FontAwesome;
using MauiIcons.Fluent;
using Indiko.Maui.Controls.SelectableLabel;
using Plugin.LocalNotification;

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

        // Initialize notification manager singleton so platform code can forward intents
        TkOlympApp.Services.NotificationManagerService.EnsureInitialized();

        var app = builder.Build();
        
        // Initialize services with logger
        var loggerFactory = app.Services.GetRequiredService<ILoggerFactory>();
        var logger = loggerFactory.CreateLogger<App>();
        TkOlympApp.Services.EventNotificationService.Initialize(logger);

        return app;
    }
}