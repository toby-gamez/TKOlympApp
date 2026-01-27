using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;

namespace TkOlympApp.Services
{
    public static class SwipeNavigationService
    {
        // Desired swipe order
        private static readonly string[] Order = new[] { "MainPage", "CalendarPage", "NoticeboardPage", "EventsPage", "OtherPage" };

        public static async Task NavigateAdjacentAsync(SwipeDirection direction)
        {
            try
            {
                var current = Shell.Current?.CurrentPage;
                if (current == null) return;

                var currentName = current.GetType().Name;

                var idx = Array.FindIndex(Order, s => string.Equals(s, currentName, StringComparison.OrdinalIgnoreCase));
                if (idx < 0)
                {
                    // try matching by suffix of fullname as a fallback
                    idx = Array.FindIndex(Order, s => current.GetType().FullName?.EndsWith(s, StringComparison.OrdinalIgnoreCase) == true);
                }
                if (idx < 0) return;

                var nextIdx = direction == SwipeDirection.Left ? idx + 1 : idx - 1;
                if (nextIdx < 0 || nextIdx >= Order.Length) return;

                // Attempt to switch the TabBar selection directly
                var shell = Shell.Current;
                var tabBar = shell?.Items?.FirstOrDefault();
                if (tabBar != null)
                {
                    var sections = tabBar.Items.ToList();
                    if (nextIdx >= 0 && nextIdx < sections.Count)
                    {
                        shell.CurrentItem = sections[nextIdx];
                        return;
                    }
                }

                // Fallback: navigate by route name if available
                var target = Order[nextIdx];
                await Shell.Current.GoToAsync(target);
            }
            catch
            {
                // best-effort; ignore errors
            }
        }
    }
}
