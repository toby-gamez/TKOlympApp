using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Services;
using System.Windows.Input;
using System.Diagnostics;
using System;
using Microsoft.Maui.ApplicationModel;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(PlainText), "text")]
public partial class PlainTextPage : ContentPage
{
    private string? _text;

    public ICommand PlainTextLongPressCommand { get; private set; }

        public PlainTextPage()
    {
        InitializeComponent();
        PlainTextLongPressCommand = new Command(async () =>
        {
            try
            {
                var text = EditorText?.Text ?? string.Empty;
                Debug.WriteLine($"PlainTextLongPressCommand invoked, length={text?.Length}");
                if (!string.IsNullOrEmpty(text))
                {
                    await Clipboard.SetTextAsync(text);
                    try { await DisplayAlertAsync(LocalizationService.Get("PlainText_Copied_Title") ?? "Copied", LocalizationService.Get("PlainText_Copied_Body") ?? "Text copied to clipboard", LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"PlainTextLongPressCommand error: {ex}");
                try { await DisplayAlertAsync(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
            }
        });
        BindingContext = this;
    }

    public static async Task ShowAsync(string text)
    {
        try
        {
            var page = new PlainTextPage();
            // Remove surrounding double quotes if present, and show the plain text
            var t = text ?? string.Empty;
            if (t.Length >= 2 && t.StartsWith("\"") && t.EndsWith("\""))
                t = t.Substring(1, t.Length - 2);
            page.PlainText = t;
            // keep fallback copy for OnAppearing if needed
            try { TkOlympApp.Services.PlainTextService.LastText = t; } catch { }
            await Shell.Current.Navigation.PushAsync(page);
        }
        catch (Exception ex)
        {
            try { await Shell.Current.DisplayAlert(LocalizationService.Get("Error_Title") ?? "Error", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
    }

    public static readonly BindableProperty PlainTextProperty = BindableProperty.Create(
        nameof(PlainText), typeof(string), typeof(PlainTextPage), default(string), propertyChanged: (b, o, n) =>
        {
            var page = (PlainTextPage)b;
            var s = n as string ?? string.Empty;
            if (page.EditorText != null)
                page.EditorText.Text = s;
            else
                page._text = s;
        });

    public string PlainText
    {
        get => (string)GetValue(PlainTextProperty);
        set => SetValue(PlainTextProperty, value);
    }

    // Backwards-compatible alias for query binding
    public string? Text
    {
        get => _text;
        set => PlainText = value ?? string.Empty;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        if (EditorText != null)
        {
            var pt = PlainText;
            if (string.IsNullOrEmpty(pt) && !string.IsNullOrEmpty(TkOlympApp.Services.PlainTextService.LastText))
            {
                EditorText.Text = TkOlympApp.Services.PlainTextService.LastText;
                TkOlympApp.Services.PlainTextService.LastText = null;
            }
            else
            {
                EditorText.Text = pt ?? string.Empty;
            }
        }
    }

    
}
