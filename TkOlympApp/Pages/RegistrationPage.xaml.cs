using Microsoft.Maui.Controls;
using System;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Helpers;
using System.Collections.ObjectModel;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Net.Http;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class RegistrationPage : ContentPage
{
    private long _eventId;
    private RegistrationOption? _selectedOption;
    

    public long EventId
    {
        get => _eventId;
        set
        {
            _eventId = value;
            _ = LoadAsync();
        }
    }

    public RegistrationPage()
    {
        InitializeComponent();
    }

    private sealed record RegistrationOption(string DisplayText, string Kind, string? Id);


    private async Task LoadAsync()
    {
        try
        {
            if (EventId == 0) return;
            var ev = await EventService.GetEventAsync(EventId);
            if (ev == null)
            {
                TitleLabel.Text = LocalizationService.Get("NotFound_Event");
                EventInfoLabel.Text = string.Empty;
                return;
            }

            TitleLabel.Text = string.IsNullOrWhiteSpace(ev.Name) ? LocalizationService.Get("EventPage_Title") : ev.Name;
            var loc = string.IsNullOrWhiteSpace(ev.LocationText) ? string.Empty : (LocalizationService.Get("Event_Location_Prefix") ?? "Místo konání: ") + ev.LocationText;
            var dates = DateHelpers.ToFriendlyDateTimeString(ev.Since);
            if (!string.IsNullOrWhiteSpace(DateHelpers.ToFriendlyDateTimeString(ev.Until)))
                dates = dates + " – " + DateHelpers.ToFriendlyDateTimeString(ev.Until);
            EventInfoLabel.Text = string.Join("\n", new[] { loc, dates }.Where(s => !string.IsNullOrWhiteSpace(s)));

            // Show EventId for debugging / user info
            try
            {
                EventIdLabel.Text = EventId != 0 ? EventId.ToString() : string.Empty;
                EventIdLabel.IsVisible = !string.IsNullOrWhiteSpace(EventIdLabel.Text);
            }
            catch { }

            // Load and show current user info and selection list
            await LoadMyCouplesAsync();
        }
        catch (Exception)
        {
            // ignore for now
        }
    }

    private async Task LoadMyCouplesAsync()
    {
        try
        {
            var me = await UserService.GetCurrentUserAsync();
            if (me == null)
            {
                CurrentUserLabel.Text = string.Empty;
                CurrentUserLabel.IsVisible = false;
                ConfirmButton.IsEnabled = false;
                return;
            }
            var name = string.IsNullOrWhiteSpace(me.UJmeno) ? me.ULogin : me.UJmeno;
            var surname = string.IsNullOrWhiteSpace(me.UPrijmeni) ? string.Empty : me.UPrijmeni;
            CurrentUserLabel.Text = string.IsNullOrWhiteSpace(surname) ? name : $"{name} {surname}";
            CurrentUserLabel.IsVisible = true;
            // Show current user's personId
            try
            {
                PersonIdLabel.Text = me.Id.ToString();
                PersonIdLabel.IsVisible = !string.IsNullOrWhiteSpace(PersonIdLabel.Text);
            }
            catch { PersonIdLabel.IsVisible = false; }
            CoupleIdLabel.Text = string.Empty;
            CoupleIdLabel.IsVisible = false;
            ConfirmButton.IsEnabled = false;
            // Load couples (global list for now) and show under user info
            try
            {
                var couples = await UserService.GetActiveCouplesFromUsersAsync();
                // Build selection: self + optional couples
                var options = new List<RegistrationOption>();
                options.Add(new RegistrationOption($"Já: {CurrentUserLabel.Text}", "self", me.Id.ToString()));
                if (couples != null && couples.Count > 0)
                {
                    foreach (var c in couples)
                    {
                        var man = string.IsNullOrWhiteSpace(c.ManName) ? "" : c.ManName;
                        var woman = string.IsNullOrWhiteSpace(c.WomanName) ? "" : c.WomanName;
                        var text = (man + " - " + woman).Trim();
                        if (!string.IsNullOrEmpty(text)) options.Add(new RegistrationOption(text, "couple", c.Id));
                    }
                }

                SelectionCollection.IsVisible = true;
                SelectionCollection.ItemsSource = options;
            }
            catch { }
        }
        catch { }
    }

    private async void OnConfirmClicked(object? sender, EventArgs e)
    {
        try
        {
            if (EventId == 0) return;
            if (_selectedOption == null)
            {
                await DisplayAlert(LocalizationService.Get("Registration_Error_Title") ?? "Chyba", LocalizationService.Get("Registration_NoSelection") ?? "Vyberte, koho chcete registrovat.", LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            string? personId = null;
            string? coupleId = null;
            if (_selectedOption.Kind == "self") personId = _selectedOption.Id;
            else if (_selectedOption.Kind == "couple") coupleId = _selectedOption.Id;

            var success = false;
            try
            {
                success = await CreateRegistrationAsync(personId, coupleId);
            }
            catch (Exception ex)
            {
                await DisplayAlert(LocalizationService.Get("Registration_Error_Title") ?? "Chyba", ex.Message, LocalizationService.Get("Button_OK") ?? "OK");
                return;
            }

            if (success)
            {
                var msg = LocalizationService.Get("Registration_Confirm_Message") ?? "Registrace odeslána";
                await DisplayAlert(LocalizationService.Get("Registration_Confirm_Title") ?? "Registrace", msg, LocalizationService.Get("Button_OK") ?? "OK");
                try { await Shell.Current.GoToAsync(".."); } catch { }
            }
        }
        catch { }
    }

    private void OnSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var sel = e.CurrentSelection?.FirstOrDefault();
        if (sel is RegistrationOption ro)
        {
            _selectedOption = ro;
            ConfirmButton.IsEnabled = true;
            // Show chosen ids
            try
            {
                if (ro.Kind == "self")
                {
                    PersonIdLabel.Text = ro.Id ?? string.Empty;
                    PersonIdLabel.IsVisible = !string.IsNullOrWhiteSpace(PersonIdLabel.Text);
                    CoupleIdLabel.Text = string.Empty;
                    CoupleIdLabel.IsVisible = false;
                }
                else if (ro.Kind == "couple")
                {
                    CoupleIdLabel.Text = ro.Id ?? string.Empty;
                    CoupleIdLabel.IsVisible = !string.IsNullOrWhiteSpace(CoupleIdLabel.Text);
                    PersonIdLabel.Text = string.Empty;
                    PersonIdLabel.IsVisible = false;
                }
            }
            catch { }
        }
        else
        {
            _selectedOption = null;
            ConfirmButton.IsEnabled = false;
            PersonIdLabel.Text = string.Empty;
            PersonIdLabel.IsVisible = false;
            CoupleIdLabel.Text = string.Empty;
            CoupleIdLabel.IsVisible = false;
        }
    }

    private async Task<bool> CreateRegistrationAsync(string? personId, string? coupleId)
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };

        // Ensure we have either a personId or coupleId to send
        if (string.IsNullOrWhiteSpace(personId) && string.IsNullOrWhiteSpace(coupleId))
        {
            throw new InvalidOperationException(LocalizationService.Get("Registration_NoSelection") ?? "Vyberte, koho chcete registrovat.");
        }

        // Build variables payload with IDs as strings to avoid escaping issues
        var reg = new Dictionary<string, object>
        {
            ["eventId"] = EventId.ToString()
        };

        if (!string.IsNullOrWhiteSpace(personId))
        {
            if (long.TryParse(personId, out var pid)) reg["personId"] = pid.ToString();
            else throw new InvalidOperationException(LocalizationService.Get("Registration_IdNumeric") ?? "PersonId must be numeric.");
        }
        if (!string.IsNullOrWhiteSpace(coupleId))
        {
            if (long.TryParse(coupleId, out var cid)) reg["coupleId"] = cid.ToString();
            else throw new InvalidOperationException(LocalizationService.Get("Registration_IdNumeric") ?? "CoupleId must be numeric.");
        }

        var clientMutationId = Guid.NewGuid().ToString();
        var variables = new Dictionary<string, object>
        {
            ["input"] = new Dictionary<string, object>
            {
                ["registrations"] = new List<Dictionary<string, object>> { reg },
                ["clientMutationId"] = clientMutationId
            }
        };

        var gql = new Dictionary<string, object>
        {
            ["query"] = "mutation RegisterToEvent($input: RegisterToEventManyInput!) { registerToEventMany(input: $input) { eventRegistrations { id } } }",
            ["variables"] = variables
        };

        var json = JsonSerializer.Serialize(gql, options);
        try
        {
            // show mutation/variables to user before sending
            MutationPreview.Text = json;
        }
        catch { }

        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await AuthService.Http.PostAsync("", content);
        var body = await resp.Content.ReadAsStringAsync();

        try
        {
            var data = JsonSerializer.Deserialize<GraphQlResponse<RegisterToEventManyData>>(body, options);
            if (data?.Errors != null && data.Errors.Count > 0)
            {
                var msg = data.Errors[0].Message ?? LocalizationService.Get("GraphQL_UnknownError");
                throw new InvalidOperationException(msg);
            }
            // success if we have at least one returned registration id
            return data?.Data?.RegisterToEventMany?.EventRegistrations != null && data.Data.RegisterToEventMany.EventRegistrations.Count > 0;
        }
        catch (JsonException)
        {
            if (!resp.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"HTTP {(int)resp.StatusCode}: {body}");
            }
            return resp.IsSuccessStatusCode;
        }
    }

    private sealed class GraphQlResponse<T>
    {
        [JsonPropertyName("data")] public T? Data { get; set; }
        [JsonPropertyName("errors")] public List<GraphQlError>? Errors { get; set; }
    }

    private sealed class GraphQlError
    {
        [JsonPropertyName("message")] public string? Message { get; set; }
    }

    private sealed class RegisterToEventManyData
    {
        [JsonPropertyName("registerToEventMany")] public RegisterToEventManyPayload? RegisterToEventMany { get; set; }
    }

    private sealed class RegisterToEventManyPayload
    {
        [JsonPropertyName("eventRegistrations")] public List<EventRegistrationResult>? EventRegistrations { get; set; }
    }

    private sealed class EventRegistrationResult
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
    }
}

