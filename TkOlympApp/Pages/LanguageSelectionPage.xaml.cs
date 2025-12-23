using System;
using Microsoft.Maui.Controls;
using TkOlympApp.Services;
using Microsoft.Maui.Controls.Xaml;

namespace TkOlympApp.Pages;

[XamlCompilation(XamlCompilationOptions.Compile)]
public partial class LanguageSelectionPage : ContentPage
{
    readonly (string code, string name)[] langs = new[]
    {
        ("cs", "Čeština"),
        ("en", "English"),
        ("vi", "Tiếng Việt"),
        ("uk", "Українська"),
    };

    public LanguageSelectionPage(string preselected)
    {
        InitializeComponent();

        TitleLabel.Text = LocalizationService.Get("Language_Choose");
        DescriptionLabel.Text = "";
        ContinueButton.Text = LocalizationService.Get("Button_Continue");

        foreach (var l in langs)
            LanguagePicker.Items.Add(l.name);

        var idx = Array.FindIndex(langs, x => x.code == preselected);
        LanguagePicker.SelectedIndex = idx >= 0 ? idx : 1; // default to English
    }

    async void OnContinueClicked(object? sender, EventArgs e)
    {
        var idx = LanguagePicker.SelectedIndex;
        if (idx < 0 || idx >= langs.Length) return;
        var code = langs[idx].code;
        LocalizationService.ApplyLanguage(code);

        try
        {
            if (Shell.Current != null)
            {
                await Shell.Current.Navigation.PopModalAsync();
                await Shell.Current.GoToAsync("//Kalendář");
            }
            else if (Navigation.ModalStack.Count > 0)
            {
                await Navigation.PopModalAsync();
            }
        }
        catch { }
    }
}
