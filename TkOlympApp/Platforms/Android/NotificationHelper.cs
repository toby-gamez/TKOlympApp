using Android.App;
using Android.Content;
using AndroidX.Core.App;
using Android.Graphics;
namespace TkOlympApp.Platforms.AndroidHelpers;

public static class NotificationHelper
{
    const string CHANNEL_ID = "tkolymp_updates";
    const string CHANNEL_NAME = "Aktuality";

    public static void ShowNotification(string title, string message)
    {
            var context = Android.App.Application.Context;

                var intent = new Intent(context, typeof(global::TkOlympApp.MainActivity));
        intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
        intent.PutExtra("openNoticeboard", true);
        intent.PutExtra("notificationTitle", title ?? string.Empty);
        intent.PutExtra("notificationMessage", message ?? string.Empty);

        var pendingIntent = PendingIntent.GetActivity(context, 0, intent, PendingIntentFlags.Immutable | PendingIntentFlags.UpdateCurrent);

            var notificationManagerCompat = NotificationManagerCompat.From(context);

        // Create channel for Android O+
            if (global::Android.OS.Build.VERSION.SdkInt >= global::Android.OS.BuildVersionCodes.O)
        {
            var channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationImportance.Default)
            {
                Description = "Notifikace o nových aktualitách"
            };
                    var nm = context.GetSystemService(global::Android.Content.Context.NotificationService) as global::Android.App.NotificationManager;
                nm?.CreateNotificationChannel(channel);
        }

        var builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                      .SetContentTitle(title)
                      .SetContentText(message)
                      .SetSmallIcon(Resource.Mipmap.appicon)
                      .SetAutoCancel(true)
                      .SetContentIntent(pendingIntent)
                      .SetPriority((int)NotificationPriority.Default);

            notificationManagerCompat.Notify(System.DateTime.Now.GetHashCode(), builder.Build());
    }
}
