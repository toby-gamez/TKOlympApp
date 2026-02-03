using System;
using System.Collections.Concurrent;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Dispatching;

namespace TkOlympApp.Services
{
    // Helper to provide pan-based swipe transitions between Shell tabs
    public static class SwipePanHandler
    {
        private static readonly ConcurrentDictionary<View, PanState> States = new();
        private const double NavigateThreshold = 120.0; // px

        private sealed class PanState
        {
            public double StartTranslation { get; set; }
            public double TotalX { get; set; }
        }

        public static void HandlePan(View root, PanUpdatedEventArgs e)
        {
            if (root == null || e == null) return;

            var state = States.GetOrAdd(root, _ => new PanState { StartTranslation = root.TranslationX, TotalX = 0 });

            switch (e.StatusType)
            {
                case GestureStatus.Started:
                    state.StartTranslation = root.TranslationX; state.TotalX = 0; break;
                case GestureStatus.Running:
                    // only consider horizontal movement larger than vertical
                    var delta = e.TotalX;
                    state.TotalX = delta;
                    // apply translation
                    try { root.TranslationX = state.StartTranslation + delta; }
                    catch (Exception ex)
                    {
                        LoggerService.SafeLogWarning(nameof(SwipePanHandler), "Failed to update translation: {0}", new object[] { ex.Message });
                    }
                    break;
                case GestureStatus.Completed:
                case GestureStatus.Canceled:
                    _ = OnPanCompletedAsync(root, state.TotalX);
                    break;
            }
        }

        private static async Task OnPanCompletedAsync(View root, double totalX)
        {
            try
            {
                // decide navigation direction
                if (Math.Abs(totalX) >= NavigateThreshold)
                {
                    var toLeft = totalX < 0; // finger moved left -> show next (move content left)
                    var width = root.Width > 0 ? root.Width : 1;
                    var off = toLeft ? -width : width;

                    // animate content off-screen
                    await root.TranslateToAsync(off, 0, 200, Easing.SinIn);

                    // perform navigation by swipe direction
                    var direction = toLeft ? SwipeDirection.Left : SwipeDirection.Right;
                    await SwipeNavigationService.NavigateAdjacentAsync(direction);

                    // after navigation, ensure incoming page content animates in
                    // set translation to opposite off-screen and animate to 0
                    try { root.TranslationX = -off; }
                    catch (Exception ex)
                    {
                        LoggerService.SafeLogWarning(nameof(SwipePanHandler), "Failed to set translation before snap-in: {0}", new object[] { ex.Message });
                    }
                    await root.TranslateToAsync(0, 0, 200, Easing.SinOut);
                }
                else
                {
                    // snap back
                    await root.TranslateToAsync(0, 0, 150, Easing.SinOut);
                }
            }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning(nameof(SwipePanHandler), "Swipe pan failed: {0}", new object[] { ex.Message });
                try { await root.TranslateToAsync(0, 0, 150, Easing.SinOut); }
                catch (Exception resetEx)
                {
                    LoggerService.SafeLogWarning(nameof(SwipePanHandler), "Failed to reset translation after error: {0}", new object[] { resetEx.Message });
                }
            }
            finally
            {
                // clear state
                States.TryRemove(root, out _);
            }
        }
    }
}
