using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using TkOlympApp.Helpers;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class FirstRunViewModel : ViewModelBase
{
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    public ObservableCollection<SlideItem> Slides { get; } = new();

    [ObservableProperty]
    private string _titleText = string.Empty;

    [ObservableProperty]
    private string _bodyText = string.Empty;

    [ObservableProperty]
    private int _carouselPosition;

    [ObservableProperty]
    private SlideItem? _selectedSlide;

    [ObservableProperty]
    private string _currentButtonText = string.Empty;

    public FirstRunViewModel(INavigationService navigationService, IUserNotifier notifier)
    {
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));

        LoadSlides();
    }

    private void LoadSlides()
    {
        TitleText = LocalizationService.Get("FirstRun_Title") ?? "Welcome";
        BodyText = LocalizationService.Get("FirstRun_Body") ?? string.Empty;

        var nextText = LocalizationService.Get("Button_Next") ?? "Next";
        var continueText = LocalizationService.Get("Button_Continue") ?? "Continue";

        Slides.Clear();
        Slides.Add(new SlideItem("onboarding1.png", LocalizationService.Get("FirstRun_Slide1_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding2.png", LocalizationService.Get("FirstRun_Slide2_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding3.png", LocalizationService.Get("FirstRun_Slide3_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding4.png", LocalizationService.Get("FirstRun_Slide4_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding5.png", LocalizationService.Get("FirstRun_Slide5_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding6.png", LocalizationService.Get("FirstRun_Slide6_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding7.png", LocalizationService.Get("FirstRun_Slide7_Caption") ?? string.Empty, nextText));
        Slides.Add(new SlideItem("onboarding8.png", LocalizationService.Get("FirstRun_Slide8_Caption") ?? string.Empty, continueText));

        SelectedSlide = Slides.Count > 0 ? Slides[0] : null;
        CurrentButtonText = SelectedSlide?.ButtonText ?? nextText;
    }

    partial void OnCarouselPositionChanged(int value)
    {
        if (value >= 0 && value < Slides.Count)
        {
            SelectedSlide = Slides[value];
        }
    }

    partial void OnSelectedSlideChanged(SlideItem? value)
    {
        CurrentButtonText = value?.ButtonText ?? (LocalizationService.Get("Button_Next") ?? "Next");
    }

    [RelayCommand]
    private async Task NextAsync()
    {
        try
        {
            if (CarouselPosition < Slides.Count - 1)
            {
                CarouselPosition += 1;
                return;
            }

            try
            {
                FirstRunHelper.SetSeen();
            }
            catch (Exception ex)
            {
                await _notifier.ShowAsync("Chyba", $"Při ukládání nastavení došlo k chybě: {ex.Message}", "OK");
                return;
            }

            try
            {
                await _navigationService.NavigateToAsync(nameof(Pages.LoginPage));
            }
            catch (Exception ex)
            {
                await _notifier.ShowAsync("Chyba", $"Navigace selhala: {ex.Message}", "OK");
            }
        }
        catch
        {
        }
    }

    public sealed record SlideItem(string Image, string Caption, string ButtonText);
}
