using System;
using System.Collections.Generic;
using System.Timers;
using Microsoft.Maui.Dispatching;
using Microsoft.Maui.Devices;
using Microsoft.Maui.ApplicationModel;
using System.Linq;

namespace TkOlympApp.Services
{
    public class NotificationManagerService : INotificationManagerService
    {
        // Singleton instance for easy access from platform code
        public static NotificationManagerService? Instance { get; private set; }

        private static readonly List<(string title, string message)> _pending = new();

        public event EventHandler? NotificationReceived;

        public NotificationManagerService()
        {
            // consume any pending notifications forwarded from platform before instance was ready
            lock (_pending)
            {
                foreach (var p in _pending)
                {
                    ReceiveNotification(p.title, p.message);
                }
                _pending.Clear();
            }
        }

        public static void EnsureInitialized()
        {
            if (Instance == null)
            {
                Instance = new NotificationManagerService();
            }
        }

        public void SendNotification(string title, string message, DateTime? notifyTime = null)
        {
            if (notifyTime == null || notifyTime <= DateTime.Now)
            {
                // immediate
                if (DeviceInfo.Platform == DevicePlatform.Android)
                {
                    // Use Android helper if available
#if ANDROID
                    Platforms.AndroidHelpers.NotificationHelper.ShowNotification(title, message);
#else
                    // Fallback to in-app delivery
                    MainThread.BeginInvokeOnMainThread(() => ReceiveNotification(title, message));
#endif
                }
                else
                {
                    MainThread.BeginInvokeOnMainThread(() => ReceiveNotification(title, message));
                }
            }
            else
            {
                // schedule
                var due = notifyTime.Value - DateTime.Now;
                var timer = new System.Timers.Timer(due.TotalMilliseconds);
                timer.AutoReset = false;
                timer.Elapsed += (s, e) =>
                {
                    timer.Dispose();
                    SendNotification(title, message, null);
                };
                timer.Start();
            }
        }

        public void ReceiveNotification(string title, string message)
        {
            // Notify subscribers
            NotificationReceived?.Invoke(this, EventArgs.Empty);

            // Optionally show a visual alert when app is in foreground
            MainThread.BeginInvokeOnMainThread(async () =>
            {
                try
                {
                    var page = Application.Current?.Windows?.FirstOrDefault()?.Page;
                    if (page != null)
                    {
                        await page.DisplayAlertAsync(title, message, "OK");
                    }
                }
                catch
                {
                    // ignore UI errors
                }
            });
        }

        // Called from platform code when app launched from notification
        public static void HandleIntent(string title, string message)
        {
            if (Instance != null)
            {
                Instance.ReceiveNotification(title, message);
            }
            else
            {
                lock (_pending)
                {
                    _pending.Add((title, message));
                }
            }
        }
    }
}
