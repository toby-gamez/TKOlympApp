using Microsoft.Maui.Controls;
using System.Collections.ObjectModel;
using System.Net;
using System.Text.RegularExpressions;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

[QueryProperty(nameof(EventId), "id")]
public partial class EventPage : ContentPage
{
    public long EventId { get; set; }

    private class RegistrationRow
    {
        public string Text { get; set; } = string.Empty;
        public string? Secondary { get; set; }
        public bool HasSecondary => !string.IsNullOrWhiteSpace(Secondary);
    }

    private readonly ObservableCollection<RegistrationRow> _registrations = new();

    public EventPage()
    {
        InitializeComponent();
        RegistrationsCollection.ItemsSource = _registrations;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        if (EventId == 0) return;
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        try
        {
            var ev = await EventService.GetEventAsync(EventId);
            if (ev == null)
            {
                await DisplayAlert("Nenalezeno", "Událost nebyla nalezena.", "OK");
                return;
            }

            TitleLabel.Text = string.IsNullOrWhiteSpace(ev.Name) ? ev.Location?.Name ?? "Událost" : ev.Name;
            DescLabel.Text = HtmlToPlainText(ev.Description);
            SummaryLabel.Text = HtmlToPlainText(ev.Summary);
            LocationLabel.Text = ev.Location?.Name;
            LocationLabel.IsVisible = !string.IsNullOrWhiteSpace(LocationLabel.Text);
            DescFrame.IsVisible = !string.IsNullOrWhiteSpace(DescLabel.Text);
            SummaryFrame.IsVisible = !string.IsNullOrWhiteSpace(SummaryLabel.Text);
            CreatedAtLabel.Text = $"Vytvořeno: {ev.CreatedAt:dd.MM.yyyy HH:mm}";
            RegistrationOpenLabel.IsVisible = ev.IsRegistrationOpen;
            PublicLabel.IsVisible = ev.IsPublic;
            VisibleLabel.IsVisible = ev.IsVisible;
            CapacityLabel.Text = ev.Capacity.HasValue ? $"Kapacita: {ev.Capacity}" : "Kapacita: N/A";
            RegistrationsLabel.Text = $"Registrováno: {ev.EventRegistrations?.TotalCount ?? 0}";

            _registrations.Clear();
            foreach (var n in ev.EventRegistrations?.Nodes ?? new List<EventService.EventRegistrationNode>())
            {
                var man = n.Couple?.Man?.Name?.Trim() ?? string.Empty;
                var woman = n.Couple?.Woman?.Name?.Trim() ?? string.Empty;
                if (string.IsNullOrWhiteSpace(man) && string.IsNullOrWhiteSpace(woman))
                    continue; // drop empty rows

                string text = !string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman)
                    ? $"{man} - {woman}"
                    : (string.IsNullOrWhiteSpace(man) ? woman : man);

                _registrations.Add(new RegistrationRow { Text = text });
            }

            RegistrationsFrame.IsVisible = _registrations.Count > 0;
        }
        catch (Exception ex)
        {
            await DisplayAlert("Chyba načtení", ex.Message, "OK");
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
        }
    }

    private async void OnReloadClicked(object? sender, EventArgs e)
    {
        await LoadAsync();
    }

    private static string? HtmlToPlainText(string? html)
    {
        if (string.IsNullOrWhiteSpace(html)) return html;
        // Normalize line breaks for common block/line elements
        var text = html;
        // 1) Normalize <br> variants and collapse consecutive <br><br> to a single <br>
        text = Regex.Replace(text, "<br\\s*/?>", "<br>", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "(?:<br>\\s*){2,}", "<br>", RegexOptions.IgnoreCase);
        // 2) Convert remaining single <br> to newline
        text = text.Replace("<br>", "\n");
        text = Regex.Replace(text, "</p>", "\n\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<p[^>]*>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<li[^>]*>", "\n• ", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</li>", string.Empty, RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "</h[1-6]>", "\n", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, "<h[1-6][^>]*>", string.Empty, RegexOptions.IgnoreCase);
        // Remove any remaining tags
        text = Regex.Replace(text, "<[^>]+>", string.Empty);
        // Decode HTML entities
        text = WebUtility.HtmlDecode(text);
        // Trim excess whitespace
        return text.Trim();
    }
}
