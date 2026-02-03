using System;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(PlainText), "text")]
public partial class PlainTextPage : ContentPage
{
    private readonly PlainTextViewModel _viewModel;

    public PlainTextPage(PlainTextViewModel viewModel)
    {
        _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        InitializeComponent();
        BindingContext = _viewModel;
    }

    public string? PlainText
    {
        get => _viewModel?.PlainText;
        set
        {
            if (_viewModel != null)
                _viewModel.PlainText = value ?? string.Empty;
        }
    }

    // Backwards-compatible alias for query binding
    public string? Text
    {
        get => PlainText;
        set => PlainText = value;
    }

    public static async Task ShowAsync(string text)
    {
        try
        {
            var t = text ?? string.Empty;
            if (t.Length >= 2 && t.StartsWith("\"") && t.EndsWith("\""))
                t = t.Substring(1, t.Length - 2);
            try { PlainTextService.LastText = t; } catch { }

            const int maxQueryLength = 1500;
            if (!string.IsNullOrEmpty(t) && t.Length <= maxQueryLength)
            {
                await Shell.Current.GoToAsync($"{nameof(PlainTextPage)}?text={Uri.EscapeDataString(t)}");
            }
            else
            {
                await Shell.Current.GoToAsync(nameof(PlainTextPage));
            }
        }
        catch (Exception ex)
        {
            try { await Shell.Current.DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await _viewModel.OnAppearingAsync();
    }

    protected override async void OnDisappearing()
    {
        await _viewModel.OnDisappearingAsync();
        base.OnDisappearing();
    }
}
