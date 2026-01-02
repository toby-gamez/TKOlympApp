using Microsoft.Maui.Controls;
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
                var slides = new List<Slide>
                {
                    new Slide("onboarding1.png", "Rychlé hledání závodů", "Další"),
                    new Slide("onboarding2.png", "Sledujte své výsledky", "Další"),
                    new Slide("onboarding3.png", "Správa přihlášek", "Další"),
                    new Slide("onboarding4.png", "Notifikace a upozornění", "Další"),
                    new Slide("onboarding5.png", "Seznam trenérů a prostor", "Další"),
                    new Slide("onboarding6.png", "Žebříčky a výsledky", "Další"),
                    new Slide("onboarding7.png", "Sdílejte a komunikujte", "Další"),
                    new Slide("onboarding8.png", "Přizpůsobte si nastavení", "Pokračovat")
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
            DisplayAlert("Chyba", $"Při inicializaci stránky došlo k chybě: {x.Message}", "OK");
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
            await DisplayAlert("Chyba", $"Při ukládání nastavení došlo k chybě: {ex.Message}", "OK");
            return;
        }

        try
        {
            // Always navigate to LoginPage after first-run
            await Shell.Current.GoToAsync(nameof(LoginPage));
        }
        catch (Exception ex)
        {
            await DisplayAlert("Chyba", $"Navigace selhala: {ex.Message}", "OK");
        }
    }
}
