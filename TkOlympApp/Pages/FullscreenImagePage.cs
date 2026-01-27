using System;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;

namespace TkOlympApp.Pages
{
    public class FullscreenImagePage : ContentPage
    {
        private readonly Image _image;
        private double _currentScale = 1;
        private double _startScale = 1;
        private double _xOffset = 0;
        private double _yOffset = 0;

        public FullscreenImagePage(ImageSource source)
        {
            BackgroundColor = Colors.Black;
            _image = new Image
            {
                Source = source,
                Aspect = Aspect.AspectFit,
                HorizontalOptions = LayoutOptions.Center,
                VerticalOptions = LayoutOptions.Center
            };

            var grid = new Grid();
            grid.Children.Add(_image);

            // pinch (two-finger) disabled - keep only double-tap zoom
            var pan = new PanGestureRecognizer { TouchPoints = 1 };
            pan.PanUpdated += OnPanUpdated;
            grid.GestureRecognizers.Add(pan);

            var dbl = new TapGestureRecognizer { NumberOfTapsRequired = 2 };
            dbl.Tapped += async (_, __) =>
            {
                try
                {
                    if (Math.Abs(_currentScale - 1) > 0.1)
                    {
                        await _image.ScaleTo(1, 200, Easing.SinOut);
                        _image.TranslationX = 0;
                        _image.TranslationY = 0;
                        _currentScale = 1;
                        _xOffset = _yOffset = 0;
                    }
                    else
                    {
                        await _image.ScaleTo(2, 200, Easing.SinIn);
                        _currentScale = 2;
                    }
                }
                catch { }
            };
            grid.GestureRecognizers.Add(dbl);

            var closeTap = new TapGestureRecognizer { NumberOfTapsRequired = 1 };
            closeTap.Tapped += async (_, __) => { await Navigation.PopAsync(); };
            // single tap closes if desired; keep double-tap separate
            // grid.GestureRecognizers.Add(closeTap);

            Content = grid;
        }

        private void OnPinchUpdated(object? sender, PinchGestureUpdatedEventArgs e)
        {
            try
            {
                if (e.Status == GestureStatus.Started)
                {
                    _startScale = _image.Scale;
                }
                else if (e.Status == GestureStatus.Running)
                {
                    var current = Math.Max(1, Math.Min(_startScale * e.Scale, 5));
                    _image.Scale = current;
                    _currentScale = current;
                }
                else if (e.Status == GestureStatus.Completed)
                {
                    // nothing
                }
            }
            catch { }
        }

        private void OnPanUpdated(object? sender, PanUpdatedEventArgs e)
        {
            try
            {
                if (_image.Scale <= 1) return;

                if (e.StatusType == GestureStatus.Running)
                {
                    var newX = _xOffset + e.TotalX;
                    var newY = _yOffset + e.TotalY;
                    _image.TranslationX = newX;
                    _image.TranslationY = newY;
                }
                else if (e.StatusType == GestureStatus.Completed || e.StatusType == GestureStatus.Canceled)
                {
                    _xOffset = _image.TranslationX;
                    _yOffset = _image.TranslationY;
                }
            }
            catch { }
        }
    }
}
