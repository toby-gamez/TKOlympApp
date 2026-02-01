using Microsoft.Maui.Controls;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services;
using TkOlympApp.Helpers;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Pages;

public partial class AboutMePage : ContentPage
{
    private readonly IServiceProvider _services;
    private readonly IAuthService _authService;
    private readonly IUserService _userService;

    public AboutMePage(IServiceProvider services, IAuthService authService, IUserService userService)
    {
        _services = services;
        _authService = authService;
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        InitializeComponent();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        
        // Subscribe to events
        if (PageRefresh != null)
            PageRefresh.Refreshing += OnRefresh;
        if (ChangePasswordButton != null)
            ChangePasswordButton.Clicked += OnChangePasswordClicked;
        if (EditSelfButton != null)
            EditSelfButton.Clicked += OnEditSelfClicked;
        if (LogoutButton != null)
            LogoutButton.Clicked += OnLogoutClicked;
        
        await LoadAsync();
    }

    protected override void OnDisappearing()
    {
        // Unsubscribe from events to prevent memory leaks
        if (PageRefresh != null)
            PageRefresh.Refreshing -= OnRefresh;
        if (ChangePasswordButton != null)
            ChangePasswordButton.Clicked -= OnChangePasswordClicked;
        if (EditSelfButton != null)
            EditSelfButton.Clicked -= OnEditSelfClicked;
        if (LogoutButton != null)
            LogoutButton.Clicked -= OnLogoutClicked;
        
        base.OnDisappearing();
    }

    private async void OnRefresh(object? sender, EventArgs e)
    {
        try
        {
            await LoadAsync();
        }
        finally
        {
            try { if (PageRefresh != null) PageRefresh.IsRefreshing = false; } catch { }
        }
    }

    private async Task LoadAsync()
    {
        ErrorLabel.IsVisible = false;
        try
        {
            var user = await _userService.GetCurrentUserAsync();
            if (user == null)
            {
                ErrorLabel.IsVisible = true;
                ErrorLabel.Text = LocalizationService.Get("Error_Loading_User") ?? "Nepodařilo se načíst uživatele.";
                return;
            }

            NameValue.Text = NonEmpty(user.UJmeno);
            SurnameValue.Text = NonEmpty(user.UPrijmeni);
            LoginValue.Text = NonEmpty(user.ULogin);
            EmailValue.Text = NonEmpty(user.UEmail);
            LastLoginValue.Text = NonEmpty(FormatDt(user.LastLogin));
            LastActiveValue.Text = NonEmpty(FormatDt(user.LastActiveAt));
            UCreatedAtValue.Text = NonEmpty(FormatDt(user.CreatedAt));
            UpdatedAtValue.Text = NonEmpty(FormatDt(user.UpdatedAt));
            // Hide rows that have no meaningful value
            
            try { EmailRow.IsVisible = !string.IsNullOrWhiteSpace(user.UEmail); } catch { }
            try { LastLoginRow.IsVisible = user.LastLogin.HasValue; } catch { }
            try { LastActiveRow.IsVisible = user.LastActiveAt.HasValue; } catch { }
            try { UCreatedAtRow.IsVisible = true; } catch { }
            try { UpdatedAtRow.IsVisible = true; } catch { }
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
                using var resp = await _authService.Http.PostAsync("", content);
                
                var body = await resp.Content.ReadAsStringAsync();
                
                if (!resp.IsSuccessStatusCode)
                {
                    var errorBody = await resp.Content.ReadAsStringAsync();
                    throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {errorBody}");
                }
                
                var parsed = JsonSerializer.Deserialize<GraphQlResp<UserProxiesData>>(body, options);
                
                // Check for GraphQL errors even with 200 status
                if (parsed?.Errors != null && parsed.Errors.Length > 0)
                {
                    var msg = parsed.Errors[0]?.Message ?? LocalizationService.Get("GraphQL_UnknownError");
                    throw new InvalidOperationException(msg);
                }
                var proxyId = parsed?.Data?.UserProxiesList?.FirstOrDefault()?.Person?.Id;
                ProxyIdValue.Text = NonEmpty(proxyId);
                try { ProxyIdRow.IsVisible = !string.IsNullOrWhiteSpace(proxyId); } catch { }
                // Treat this proxy id as my personId globally
                try
                {
                    _userService.SetCurrentPersonId(proxyId);
                }
                catch
                {
                    // ignore failures setting person id
                }
                // Fetch full person data for this person id
                if (!string.IsNullOrWhiteSpace(proxyId))
                {
                        try
                        {
                            var query = "query MyQuery { person(id: \"" + proxyId + "\") { address { city conscriptionNumber district orientationNumber postalCode region street } bio birthDate createdAt cstsId email firstName prefixTitle suffixTitle gender isTrainer lastName nationalIdNumber nationality phone wdsfId cohortMembershipsList { cohort { colorRgb id name ordering isVisible } } } }";

                        var gqlReqP = new { query };
                        var jsonP = JsonSerializer.Serialize(gqlReqP, options);
                        using var contentP = new StringContent(jsonP, Encoding.UTF8, "application/json");
                        using var respP = await _authService.Http.PostAsync("", contentP);
                        
                        var bodyP = await respP.Content.ReadAsStringAsync();
                        
                        if (!respP.IsSuccessStatusCode)
                        {
                            var errorBodyP = await respP.Content.ReadAsStringAsync();
                            throw new InvalidOperationException($"HTTP {(int)respP.StatusCode}: {errorBodyP}");
                        }
                        
                        var parsedP = JsonSerializer.Deserialize<GraphQlResp<PersonRespData>>(bodyP, options);
                        
                        // Check for GraphQL errors even with 200 status
                        if (parsedP?.Errors != null && parsedP.Errors.Length > 0)
                        {
                            var msg = parsedP.Errors[0]?.Message ?? LocalizationService.Get("GraphQL_UnknownError");
                            throw new InvalidOperationException(msg);
                        }
                        var person = parsedP?.Data?.Person;
                        if (person != null)
                        {
                            // Format name with optional prefix/suffix titles
                            static string FormatPrefixName(string? prefix, string? name)
                            {
                                prefix = prefix?.Trim();
                                name = name?.Trim();
                                if (string.IsNullOrWhiteSpace(prefix) && string.IsNullOrWhiteSpace(name)) return "—";
                                if (string.IsNullOrWhiteSpace(name)) return prefix ?? "—";
                                if (string.IsNullOrWhiteSpace(prefix)) return name!;
                                return prefix + " " + name;
                            }

                            static string FormatNameSuffix(string? name, string? suffix)
                            {
                                name = name?.Trim();
                                suffix = suffix?.Trim();
                                if (string.IsNullOrWhiteSpace(name) && string.IsNullOrWhiteSpace(suffix)) return "—";
                                if (string.IsNullOrWhiteSpace(name)) return suffix ?? "—";
                                if (string.IsNullOrWhiteSpace(suffix)) return name!;
                                return name + " " + suffix;
                            }

                            NameValue.Text = FormatPrefixName(person.PrefixTitle, person.FirstName);
                            SurnameValue.Text = FormatNameSuffix(person.LastName, person.SuffixTitle);

                            BioValue.Text = NonEmpty(person.Bio?.Trim());
                            BirthDateValue.Text = FormatDtString(person.BirthDate);
                            PhoneValue.Text = NonEmpty(PhoneHelpers.Format(person.Phone?.Trim()));
                            NationalityValue.Text = NonEmpty(NationalityHelper.GetLocalizedAdjective(person.Nationality?.Trim()));
                            AddressValue.Text = ComposeAddress(person.Address);
                            GenderValue.Text = MapGender(person.Gender);
                            IsTrainerValue.Text = person.IsTrainer.HasValue ? (person.IsTrainer.Value ? LocalizationService.Get("About_Yes") : LocalizationService.Get("About_No")) : "—";
                            WdsfIdValue.Text = NonEmpty(person.WdsfId?.Trim());
                            CstsIdValue.Text = NonEmpty(person.CstsId?.Trim());
                            NationalIdValue.Text = NonEmpty(person.NationalIdNumber?.Trim());
                            // Toggle visibility per-row
                            try { BioRow.IsVisible = !string.IsNullOrWhiteSpace(person.Bio); } catch { }
                            try { BirthDateRow.IsVisible = !string.IsNullOrWhiteSpace(person.BirthDate); } catch { }
                            try { PhoneRow.IsVisible = !string.IsNullOrWhiteSpace(person.Phone); } catch { }
                            try { NationalityRow.IsVisible = !string.IsNullOrWhiteSpace(person.Nationality); } catch { }
                            try { AddressRow.IsVisible = AddressValue.Text != "—"; } catch { }
                            try { GenderRow.IsVisible = !string.IsNullOrWhiteSpace(person.Gender); } catch { }
                            try { IsTrainerRow.IsVisible = person.IsTrainer.HasValue; } catch { }
                            try { WdsfRow.IsVisible = !string.IsNullOrWhiteSpace(person.WdsfId); } catch { }
                            try { CstsRow.IsVisible = !string.IsNullOrWhiteSpace(person.CstsId); } catch { }
                            try { NationalIdRow.IsVisible = !string.IsNullOrWhiteSpace(person.NationalIdNumber); } catch { }

                            // Render cohort color dots (training groups)
                            try
                            {
                                CohortDots.Children.Clear();
                                var cohortsList = (person.CohortMembershipsList ?? new List<CohortMembership>())
                                    .Where(m => m?.Cohort?.IsVisible != false)
                                    .OrderBy(m => m?.Cohort?.Ordering ?? int.MaxValue)
                                    .ToList();
                                foreach (var membership in cohortsList)
                                {
                                    try
                                    {
                                        var c = membership?.Cohort;
                                        if (c == null) continue;
                                        var name = c.Name ?? string.Empty;
                                        var colorBrush = CohortColorHelper.ParseColorBrush(c.ColorRgb) ?? new Microsoft.Maui.Controls.SolidColorBrush(Microsoft.Maui.Graphics.Colors.LightGray);

                                        var row = new Microsoft.Maui.Controls.Grid { VerticalOptions = Microsoft.Maui.Controls.LayoutOptions.Center, HorizontalOptions = Microsoft.Maui.Controls.LayoutOptions.Fill };
                                        row.ColumnDefinitions.Add(new Microsoft.Maui.Controls.ColumnDefinition { Width = Microsoft.Maui.GridLength.Star });
                                        row.ColumnDefinitions.Add(new Microsoft.Maui.Controls.ColumnDefinition { Width = Microsoft.Maui.GridLength.Auto });

                                        var nameLabel = new Microsoft.Maui.Controls.Label { Text = name, VerticalOptions = Microsoft.Maui.Controls.LayoutOptions.Center, HorizontalOptions = Microsoft.Maui.Controls.LayoutOptions.Start };
                                        row.Add(nameLabel);

                                        var dot = new Microsoft.Maui.Controls.Border
                                        {
                                            WidthRequest = 20,
                                            HeightRequest = 20,
                                            Padding = 0,
                                            Margin = new Microsoft.Maui.Thickness(0),
                                            HorizontalOptions = Microsoft.Maui.Controls.LayoutOptions.End,
                                            VerticalOptions = Microsoft.Maui.Controls.LayoutOptions.Center,
                                            Background = colorBrush,
                                            Stroke = null,
                                            StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 }
                                        };
                                        row.Add(dot, 1, 0);

                                        CohortDots.Children.Add(row);
                                    }
                                    catch { }
                                }

                                CohortDots.IsVisible = CohortDots.Children.Count > 0;
                                try { CohortsFrame.IsVisible = CohortDots.IsVisible; } catch { CohortsFrame.IsVisible = false; }
                            }
                            catch { try { CohortDots.IsVisible = false; CohortsFrame.IsVisible = false; } catch { } }

                            // Parent border visibility
                            try { ContactBorder.IsVisible = EmailRow.IsVisible || PhoneRow.IsVisible; } catch { }
                            try { PersonalBorder.IsVisible = BioRow.IsVisible || BirthDateRow.IsVisible || NationalityRow.IsVisible || GenderRow.IsVisible || IsTrainerRow.IsVisible; } catch { }
                            try { AddressBorder.IsVisible = AddressRow.IsVisible; } catch { }
                            try { IdsBorder.IsVisible = ProxyIdRow.IsVisible || WdsfRow.IsVisible || CstsRow.IsVisible || NationalIdRow.IsVisible; } catch { }
                        }
                    }
                    catch (Exception exPerson)
                    {
                        var title = LocalizationService.Get("Error_Title") ?? "Chyba";
                        var ok = LocalizationService.Get("Button_OK") ?? "OK";
                        try { await DisplayAlertAsync(title, exPerson.Message, ok); } catch { }
                    }
                }
            }
            catch (Exception exProxy)
            {
                ProxyIdValue.Text = "—";
                var title = LocalizationService.Get("Error_Title") ?? "Chyba";
                var ok = LocalizationService.Get("Button_OK") ?? "OK";
                try { await DisplayAlertAsync(title, exProxy.Message, ok); } catch { }
            }
        }
        catch (Exception ex)
        {
            ErrorLabel.IsVisible = true;
            ErrorLabel.Text = ex.Message;
        }
        finally
        {
            // LoadingIndicator removed - RefreshView handles loading UI
        }
    }

    private static string FormatDt(DateTime? dt)
        => dt.HasValue ? dt.Value.ToLocalTime().ToString("dd.MM.yyyy HH:mm") : "";

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? "—" : s;

    private async void OnLogoutClicked(object? sender, EventArgs e)
    {
        try
        {
            await _authService.LogoutAsync();

            try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { try { await Shell.Current.GoToAsync($"//{nameof(LoginPage)}"); } catch { } }
        }
        catch
        {
            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var msg = LocalizationService.Get("Error_OperationFailed_Message") ?? "Operace selhala.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            try { await DisplayAlertAsync(title, msg, ok); } catch { }
            try { await Shell.Current.GoToAsync(nameof(LoginPage)); } catch { /* ignore */ }
        }
    }

    private async void OnChangePasswordClicked(object? sender, EventArgs e)
    {
        try
        {
            var page = _services.GetRequiredService<ChangePasswordPage>();
            await Shell.Current.Navigation.PushModalAsync(page);
        }
        catch (Exception ex)
        {
            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var msg = LocalizationService.Get("Error_OperationFailed_Message") ?? ex.Message ?? "Operace selhala.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            try { await DisplayAlertAsync(title, msg, ok); } catch { }
        }
    }

    private async void OnEditSelfClicked(object? sender, EventArgs e)
    {
        try
        {
            var page = _services.GetRequiredService<EditSelfPage>();
            await Shell.Current.Navigation.PushModalAsync(page);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Navigation to PersonPage failed: {ex}");
            var title = LocalizationService.Get("Error_Title") ?? "Chyba";
            var msg = LocalizationService.Get("Error_OperationFailed_Message") ?? ex.Message ?? "Operace selhala.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            try { await DisplayAlertAsync(title, msg, ok); } catch { }
        }
    }

    // Password change moved to modal ChangePasswordPage

    private sealed class GraphQlResp<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public GraphQlError[]? Errors { get; set; }
    }
    
    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
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

    private static string FormatDtString(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return "—";
        if (DateTime.TryParse(s, out var dt)) return dt.ToLocalTime().ToString("dd.MM.yyyy");
        return s;
    }

    private static string ComposeAddress(Address? a)
    {
        if (a == null) return "—";
        var street = a.Street?.Trim();
        var conscription = a.ConscriptionNumber?.Trim(); // popisné
        var orientation = a.OrientationNumber?.Trim(); // orientační

        string numberPart;
        if (!string.IsNullOrWhiteSpace(conscription) && !string.IsNullOrWhiteSpace(orientation))
            numberPart = conscription + "/" + orientation; // pop/ori
        else if (!string.IsNullOrWhiteSpace(conscription))
            numberPart = conscription;
        else if (!string.IsNullOrWhiteSpace(orientation))
            numberPart = orientation;
        else
            numberPart = string.Empty;

        // First segment: street + (optional) numberPart (no comma between them)
        var first = string.IsNullOrWhiteSpace(street)
            ? (string.IsNullOrWhiteSpace(numberPart) ? string.Empty : numberPart)
            : (string.IsNullOrWhiteSpace(numberPart) ? street : street + " " + numberPart);

        var postal = PostalCodeHelpers.Format(a.PostalCode?.Trim());
        var city = a.City?.Trim();
        var region = a.Region?.Trim();

        var postalCity = string.Empty;
        if (!string.IsNullOrWhiteSpace(postal) && !string.IsNullOrWhiteSpace(city)) postalCity = postal + " " + city;
        else if (!string.IsNullOrWhiteSpace(postal)) postalCity = postal;
        else if (!string.IsNullOrWhiteSpace(city)) postalCity = city;

        var parts = new[] { first, postalCity, region }.Where(p => !string.IsNullOrWhiteSpace(p)).ToArray();
        return parts.Length == 0 ? "—" : string.Join(", ", parts);
    }

    private static string MapGender(string? gender)
    {
        if (string.IsNullOrWhiteSpace(gender)) return "—";
        return gender.Trim().ToUpperInvariant() switch
        {
            "MAN" => LocalizationService.Get("About_Gender_Male"),
            "WOMAN" => LocalizationService.Get("About_Gender_Female"),
            _ => LocalizationService.Get("About_Gender_Other"),
        } ?? "—";
    }

    private sealed class PersonRespData
    {
        [JsonPropertyName("person")] public PersonDetail? Person { get; set; }
    }

    private sealed class PersonDetail
    {
        [JsonPropertyName("address")] public Address? Address { get; set; }
        [JsonPropertyName("bio")] public string? Bio { get; set; }
        [JsonPropertyName("birthDate")] public string? BirthDate { get; set; }
        [JsonPropertyName("createdAt")] public string? CreatedAt { get; set; }
        [JsonPropertyName("cstsId")] public string? CstsId { get; set; }
        [JsonPropertyName("email")] public string? Email { get; set; }
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("prefixTitle")] public string? PrefixTitle { get; set; }
        [JsonPropertyName("suffixTitle")] public string? SuffixTitle { get; set; }
        [JsonPropertyName("gender")] public string? Gender { get; set; }
        [JsonPropertyName("isTrainer")] public bool? IsTrainer { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
        [JsonPropertyName("nationalIdNumber")] public string? NationalIdNumber { get; set; }
        [JsonPropertyName("nationality")] public string? Nationality { get; set; }
        [JsonPropertyName("phone")] public string? Phone { get; set; }
        [JsonPropertyName("wdsfId")] public string? WdsfId { get; set; }
        [JsonPropertyName("cohortMembershipsList")] public List<CohortMembership>? CohortMembershipsList { get; set; }
    }

    private sealed class CohortMembership
    {
        [JsonPropertyName("cohort")] public Cohort? Cohort { get; set; }
    }

    private sealed class Cohort
    {
        [JsonPropertyName("colorRgb")] public string? ColorRgb { get; set; }
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("name")] public string? Name { get; set; }
        [JsonPropertyName("ordering")] public int? Ordering { get; set; }
        [JsonPropertyName("isVisible")] public bool? IsVisible { get; set; }
    }

    private sealed class Address
    {
        [JsonPropertyName("city")] public string? City { get; set; }
        [JsonPropertyName("conscriptionNumber")] public string? ConscriptionNumber { get; set; }
        [JsonPropertyName("district")] public string? District { get; set; }
        [JsonPropertyName("orientationNumber")] public string? OrientationNumber { get; set; }
        [JsonPropertyName("postalCode")] public string? PostalCode { get; set; }
        [JsonPropertyName("region")] public string? Region { get; set; }
        [JsonPropertyName("street")] public string? Street { get; set; }
    }
}
