using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class ChangePasswordPage : ContentPage
{
    public ChangePasswordPage()
    {
        InitializeComponent();
    }

    private void OnNewPassTextChanged(object? sender, TextChangedEventArgs e)
    {
        var newText = e.NewTextValue ?? string.Empty;
        // length feedback
            if (newText.Length >= 8)
            {
                LengthLabel!.Text = string.Format(LocalizationService.Get("ChangePassword_Length_OK_Format") ?? "Délka: {0}/8 (OK)", newText.Length);
            NewPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["SuccessBrush"];
            NewPassBorder!.StrokeThickness = 2;
        }
        else
        {
                LengthLabel!.Text = string.Format(LocalizationService.Get("ChangePassword_Length_Format") ?? "Délka: {0}/8", newText.Length);
            NewPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["Gray300Brush"];
            NewPassBorder!.StrokeThickness = 1;
        }

        // match feedback (use confirm field)
        var confirm = ConfirmPassEntry!.Text ?? string.Empty;
        if (!string.IsNullOrEmpty(confirm))
        {
            if (newText == confirm)
            {
                    MatchLabel!.Text = LocalizationService.Get("ChangePassword_Match_OK") ?? "Hesla se shodují";
                ConfirmPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["SuccessBrush"];
                ConfirmPassBorder!.StrokeThickness = 2;
                // also ensure new-pass border shows success when both conditions met
                if (newText.Length >= 8)
                {
                    NewPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["SuccessBrush"];
                    NewPassBorder!.StrokeThickness = 2;
                }
            }
            else
            {
                    MatchLabel!.Text = LocalizationService.Get("ChangePassword_Match_MISMATCH") ?? "Hesla se neshodují";
                ConfirmPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["DangerBrush"];
                ConfirmPassBorder!.StrokeThickness = 2;
            }
        }
        else
        {
                MatchLabel!.Text = LocalizationService.Get("ChangePassword_Match_None") ?? "Hesla: -";
            ConfirmPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["Gray300Brush"];
        }
    }

    private void OnConfirmPassTextChanged(object? sender, TextChangedEventArgs e)
    {
        var confirm = e.NewTextValue ?? string.Empty;
        var newPass = NewPassEntry!.Text ?? string.Empty;

        // match feedback
        if (string.IsNullOrEmpty(confirm))
        {
                MatchLabel!.Text = LocalizationService.Get("ChangePassword_Match_None") ?? "Hesla: -";
            ConfirmPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["Gray300Brush"];
            ConfirmPassBorder!.StrokeThickness = 1;
        }
        else if (confirm == newPass)
        {
                MatchLabel!.Text = LocalizationService.Get("ChangePassword_Match_OK") ?? "Hesla se shodují";
            ConfirmPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["SuccessBrush"];
            ConfirmPassBorder!.StrokeThickness = 2;
            if ((NewPassEntry.Text ?? string.Empty).Length >= 8)
            {
                NewPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["SuccessBrush"];
                    NewPassBorder.StrokeThickness = 2;
            }
        }
        else
        {
                MatchLabel.Text = LocalizationService.Get("ChangePassword_Match_MISMATCH") ?? "Hesla se neshodují";
            ConfirmPassBorder!.Stroke = (Microsoft.Maui.Controls.Brush)Application.Current!.Resources["DangerBrush"];
            ConfirmPassBorder.StrokeThickness = 2;
        }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class GraphQlRespWithErrors<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public GraphQlError[]? Errors { get; set; }
    }

    private sealed class ChangePasswordData
    {
        [JsonPropertyName("changePassword")] public ChangePasswordResult? ChangePassword { get; set; }
    }

    private sealed class ChangePasswordResult
    {
        [JsonPropertyName("clientMutationId")] public string? ClientMutationId { get; set; }
    }

    private async void OnCancelClicked(object? sender, EventArgs e)
    {
        try { await Shell.Current.Navigation.PopModalAsync(); } catch { }
    }

    private async void OnChangeClicked(object? sender, EventArgs e)
    {
        try
        {
            ErrorLabel.IsVisible = false;
            var newPass = NewPassEntry.Text ?? string.Empty;
            var confirm = ConfirmPassEntry.Text ?? string.Empty;

            if (string.IsNullOrWhiteSpace(newPass))
            {
                    ErrorLabel.Text = LocalizationService.Get("ChangePassword_EnterNew") ?? "Zadejte nové heslo.";
                ErrorLabel.IsVisible = true;
                return;
            }

            if (newPass != confirm)
            {
                    ErrorLabel.Text = LocalizationService.Get("ChangePassword_Error_Mismatch") ?? "Hesla se neshodují.";
                ErrorLabel.IsVisible = true;
                return;
            }

            var gqlReq = new
            {
                query = "mutation MyMutation($newPass: String!) { changePassword(input: {newPass: $newPass}) { clientMutationId } }",
                variables = new { newPass }
            };

            var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
            var json = JsonSerializer.Serialize(gqlReq, options);
            using var content = new StringContent(json, Encoding.UTF8, "application/json");
            using var resp = await AuthService.Http.PostAsync("", content);

            var body = await resp.Content.ReadAsStringAsync();
            var parsed = JsonSerializer.Deserialize<GraphQlRespWithErrors<ChangePasswordData>>(body, options);
            if (parsed?.Errors != null && parsed.Errors.Length > 0)
            {
                var msg = parsed.Errors[0].Message ?? "Chyba při změně hesla.";
                ErrorLabel.Text = msg;
                ErrorLabel.IsVisible = true;
                return;
            }

            if (!resp.IsSuccessStatusCode)
            {
                ErrorLabel.Text = $"HTTP: {resp.StatusCode}";
                ErrorLabel.IsVisible = true;
                return;
            }

            // success: close modal, log out and navigate to login
            try { await Shell.Current.Navigation.PopModalAsync(); } catch { }
            try
            {
                await AuthService.LogoutAsync();
                // Navigate to login page (relative route used during startup)
                try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { try { await Shell.Current.GoToAsync($"//{nameof(LoginPage)}"); } catch { } }
            }
            catch
            {
                    try { await Shell.Current.DisplayAlertAsync(LocalizationService.Get("ChangePassword_Success_Title") ?? "Hotovo", LocalizationService.Get("ChangePassword_Success_LoggedOut_Message") ?? "Heslo bylo úspěšně změněno. Proběhlo automatické odhlášení.", LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
            }
        }
        catch (Exception ex)
        {
            ErrorLabel.Text = ex.Message;
            ErrorLabel.IsVisible = true;
        }
    }
}
