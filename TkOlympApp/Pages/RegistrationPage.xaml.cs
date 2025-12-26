using Microsoft.Maui.Controls;
using System;
using System.Threading.Tasks;
using TkOlympApp.Services;
using TkOlympApp.Helpers;
using System.Collections.ObjectModel;
using System.Linq;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class RegistrationPage : ContentPage
{
    private long _eventId;
    

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

            // Load and show current user info
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
            ConfirmButton.IsEnabled = true;
            // Load couples (global list for now) and show under user info
            try
            {
                var couples = await UserService.GetActiveCouplesFromUsersAsync();
                if (couples != null && couples.Count > 0)
                {
                    CouplesHeader.IsVisible = true;
                    MyCouplesCollection.IsVisible = true;
                    MyCouplesCollection.ItemsSource = couples.Select(c =>
                        {
                            var man = string.IsNullOrWhiteSpace(c.ManName) ? "" : c.ManName;
                            var woman = string.IsNullOrWhiteSpace(c.WomanName) ? "" : c.WomanName;
                            return (man + " - " + woman).Trim();
                        }).ToList();
                }
                else
                {
                    CouplesHeader.IsVisible = false;
                    MyCouplesCollection.IsVisible = false;
                }
            }
            catch { }
        }
        catch { }
    }

    private async void OnConfirmClicked(object? sender, EventArgs e)
    {
        try
        {
            // Placeholder action: show confirmation and go back
            await DisplayAlert(LocalizationService.Get("Registration_Confirm_Title") ?? "Registrace", LocalizationService.Get("Registration_Confirm_Message") ?? "Registrace odeslána (ukázka)", LocalizationService.Get("Button_OK") ?? "OK");
            try { await Shell.Current.GoToAsync(".."); } catch { }
        }
        catch { }
    }
}

