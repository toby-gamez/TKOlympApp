using Android.App;
using Android.Content;
using AndroidX.Core.App;
using Android.Graphics;
namespace TkOlympApp.Platforms.AndroidHelpers;

#pragma warning disable CA1416
public static class NotificationHelper
{
    const string CHANNEL_ID = "tkolymp_updates";
    static string ChannelName => TkOlympApp.Services.LocalizationService.Get("Notifications_ChannelName") ?? "Updates";
    static string ChannelDescription => TkOlympApp.Services.LocalizationService.Get("Notifications_ChannelDescription") ?? "Notifications";

    #pragma warning disable CS8602
    public static void ShowNotification(string title, string message)
    {
        var context = global::Android.App.Application.Context;
        if (context == null) return;

        var intent = new Intent(context, typeof(TkOlympApp.MainActivity));
        intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
        intent.PutExtra("openNoticeboard", true);
        intent.PutExtra("notificationTitle", title ?? string.Empty);
        intent.PutExtra("notificationMessage", message ?? string.Empty);

        var pendingIntent = PendingIntent.GetActivity(context, 0, intent, PendingIntentFlags.Immutable | PendingIntentFlags.UpdateCurrent);
        if (pendingIntent == null) return;

        var notificationManagerCompat = NotificationManagerCompat.From(context);
        if (notificationManagerCompat == null) return;

        // Create channel for Android O+
        if (global::Android.OS.Build.VERSION.SdkInt >= global::Android.OS.BuildVersionCodes.O)
        {
            var channel = new NotificationChannel(CHANNEL_ID, ChannelName, NotificationImportance.Default)
            {
                Description = ChannelDescription
            };
            var nm = context.GetSystemService(global::Android.Content.Context.NotificationService) as global::Android.App.NotificationManager;
            nm?.CreateNotificationChannel(channel);
        }

        var builder = new NotificationCompat.Builder(context!, CHANNEL_ID)
                      .SetContentTitle(title ?? string.Empty)
                      .SetContentText(message ?? string.Empty)
                      .SetSmallIcon(Resource.Mipmap.appicon)
                      .SetAutoCancel(true)
                      .SetContentIntent(pendingIntent)
                      .SetPriority((int)NotificationPriority.Default);

        var notification = builder.Build();
        if (notification != null)
        {
            notificationManagerCompat.Notify(System.DateTime.Now.GetHashCode(), notification);
        }
    }
    #pragma warning restore CS8602
    #pragma warning restore CA1416
}
