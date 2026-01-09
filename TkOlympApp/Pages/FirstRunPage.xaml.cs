using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using TkOlympApp.Helpers;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class FirstRunPage : ContentPage
{
    public FirstRunPage()
    {
        try
        {
            InitializeComponent();
            try
            {
                // Localized onboarding slides
                Title = LocalizationService.Get("FirstRun_Title");
                TitleLabel.Text = LocalizationService.Get("FirstRun_Title");
                BodyLabel.Text = LocalizationService.Get("FirstRun_Body");

                var nextText = LocalizationService.Get("Button_Next");
                var continueText = LocalizationService.Get("Button_Continue");

                var slides = new List<Slide>
                {
                    new Slide("onboarding1.png", LocalizationService.Get("FirstRun_Slide1_Caption"), nextText),
                    new Slide("onboarding2.png", LocalizationService.Get("FirstRun_Slide2_Caption"), nextText),
                    new Slide("onboarding3.png", LocalizationService.Get("FirstRun_Slide3_Caption"), nextText),
                    new Slide("onboarding4.png", LocalizationService.Get("FirstRun_Slide4_Caption"), nextText),
                    new Slide("onboarding5.png", LocalizationService.Get("FirstRun_Slide5_Caption"), nextText),
                    new Slide("onboarding6.png", LocalizationService.Get("FirstRun_Slide6_Caption"), nextText),
                    new Slide("onboarding7.png", LocalizationService.Get("FirstRun_Slide7_Caption"), nextText),
                    new Slide("onboarding8.png", LocalizationService.Get("FirstRun_Slide8_Caption"), continueText)
                };

                OnboardingCarousel.ItemsSource = slides;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to populate onboarding carousel: {ex}");
            }
        }
        catch (Exception x)
        {
            MainThread.BeginInvokeOnMainThread(() => { _ = Shell.Current.DisplayAlertAsync("Chyba", $"Při inicializaci stránky došlo k chybě: {x.Message}", "OK"); });
        }
        
    }

    private record Slide(string Image, string Caption, string ButtonText);

    private void OnSlideButtonClicked(object? sender, EventArgs e)
    {
        try
        {
            var items = OnboardingCarousel.ItemsSource as System.Collections.IList;
            if (items == null) return;

            int pos = OnboardingCarousel.Position;
            int count = items.Count;

            if (pos < count - 1)
            {
                OnboardingCarousel.ScrollTo(index: pos + 1, position: Microsoft.Maui.Controls.ScrollToPosition.Center, animate: true);
            }
            else
            {
                OnStartClicked(sender, e);
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"OnSlideButtonClicked error: {ex}");
        }
    }

    private async void OnStartClicked(object? sender, EventArgs e)
    {
        try
        {
            FirstRunHelper.SetSeen();
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba", $"Při ukládání nastavení došlo k chybě: {ex.Message}", "OK");
            return;
        }

        try
        {
            // Always navigate to LoginPage after first-run
            await Shell.Current.GoToAsync(nameof(LoginPage));
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync("Chyba", $"Navigace selhala: {ex.Message}", "OK");
        }
    }
}
