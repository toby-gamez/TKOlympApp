using Microsoft.Maui.Controls;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class AboutMePage : ContentPage
{
    public AboutMePage()
    {
        InitializeComponent();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        LoadingIndicator.IsVisible = true;
        LoadingIndicator.IsRunning = true;
        ErrorLabel.IsVisible = false;
        try
        {
            var user = await UserService.GetCurrentUserAsync();
            if (user == null)
            {
                ErrorLabel.IsVisible = true;
                ErrorLabel.Text = "Nepodařilo se načíst uživatele.";
                return;
            }

            NameValue.Text = NonEmpty(user.UJmeno);
            SurnameValue.Text = NonEmpty(user.UPrijmeni);
            LoginValue.Text = NonEmpty(user.ULogin);
            EmailValue.Text = NonEmpty(user.UEmail);
            LastLoginValue.Text = NonEmpty(FormatDt(user.LastLogin));
            LastActiveValue.Text = NonEmpty(FormatDt(user.LastActiveAt));
            UCreatedAtValue.Text = NonEmpty(FormatDt(user.UCreatedAt));
            CreatedAtValue.Text = NonEmpty(FormatDt(user.CreatedAt));
            UpdatedAtValue.Text = NonEmpty(FormatDt(user.UpdatedAt));
            // lastVersion removed from API — no longer displayed

            // Fetch userProxiesList -> person -> id and display in second border
            try
            {
                var gqlReq = new { query = "query MyQuery { userProxiesList { person { id } } }" };
                var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
                {
                    PropertyNameCaseInsensitive = true
                };

                var json = JsonSerializer.Serialize(gqlReq, options);
                using var content = new StringContent(json, Encoding.UTF8, "application/json");
                using var resp = await AuthService.Http.PostAsync("", content);
                resp.EnsureSuccessStatusCode();

                var body = await resp.Content.ReadAsStringAsync();
                var parsed = JsonSerializer.Deserialize<GraphQlResp<UserProxiesData>>(body, options);
                var proxyId = parsed?.Data?.UserProxiesList?.FirstOrDefault()?.Person?.Id;
                ProxyIdValue.Text = NonEmpty(proxyId);
                // Treat this proxy id as my personId globally
                try
                {
                    UserService.SetCurrentPersonId(proxyId);
                }
                catch
                {
                    // ignore failures setting person id
                }
            }
            catch
            {
                ProxyIdValue.Text = "—";
            }
        }
        catch (Exception ex)
        {
            ErrorLabel.IsVisible = true;
            ErrorLabel.Text = ex.Message;
        }
        finally
        {
            LoadingIndicator.IsRunning = false;
            LoadingIndicator.IsVisible = false;
        }
    }

    private static string FormatDt(DateTime? dt)
        => dt.HasValue ? dt.Value.ToLocalTime().ToString("dd.MM.yyyy HH:mm") : "";

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? "—" : s;

    private async void OnLogoutClicked(object? sender, EventArgs e)
    {
        try
        {
            await AuthService.LogoutAsync();

            // Navigate to login route
            await Shell.Current.GoToAsync($"//{nameof(LoginPage)}");
        }
        catch
        {
            try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { /* ignore */ }
        }
    }

    private sealed class GraphQlResp<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
    }

    private sealed class UserProxiesData
    {
        [JsonPropertyName("userProxiesList")] public UserProxy[]? UserProxiesList { get; set; }
    }

    private sealed class UserProxy
    {
        [JsonPropertyName("person")] public Person? Person { get; set; }
    }

    private sealed class Person
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
    }
}
