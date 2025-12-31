using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Services;
using System.Windows.Input;
using System.Diagnostics;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(Text), "text")]
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

    public string? Text
    {
        get => _text;
        set
        {
            _text = value;
            if (EditorText != null)
            {
                EditorText.Text = _text ?? string.Empty;
            }
        }
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        if (EditorText != null)
        {
            EditorText.Text = _text ?? string.Empty;
        }
    }

    
}
