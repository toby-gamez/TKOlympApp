using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Controls.Shapes;
using Microsoft.Maui.Graphics;

using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class CohortGroupsPage : ContentPage
{
    private bool _loaded;

    public CohortGroupsPage()
    {
        InitializeComponent();
    }

    

    protected override void OnAppearing()
    {
        base.OnAppearing();
        if (!_loaded)
        {
            _ = LoadAsync();
            _loaded = true;
        }
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
        try
        {
            var groups = await CohortService.GetCohortGroupsAsync();
            var cohorts = groups
                .SelectMany(g => g.CohortsList ?? new System.Collections.Generic.List<CohortService.CohortItem>())
                .Where(ci => ci != null && !string.IsNullOrWhiteSpace(ci.Name))
                .ToList();

            GroupsStack.Children.Clear();
            foreach (var c in cohorts)
            {
                var itemStack = new VerticalStackLayout { Spacing = 8 };

                // Title row with colored circle on the right implemented with Grid
                var titleRow = new Grid { VerticalOptions = LayoutOptions.Center, HorizontalOptions = LayoutOptions.Fill };
                titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });
                titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var titleLabel = new Label { Text = c.Name ?? string.Empty, FontAttributes = FontAttributes.Bold, FontSize = 18, HorizontalOptions = LayoutOptions.Start };
                titleRow.Add(titleLabel);

                if (!string.IsNullOrWhiteSpace(c.ColorRgb))
                {
                    var brush = TryParseColorBrush(c.ColorRgb) ?? new SolidColorBrush(Colors.LightGray);
                    var colorDot = new Border
                    {
                        Background = brush,
                        Padding = 0,
                        WidthRequest = 20,
                        HeightRequest = 20,
                        Margin = new Thickness(0),
                        HorizontalOptions = LayoutOptions.End,
                        VerticalOptions = LayoutOptions.Center,
                        Stroke = null,
                        StrokeShape = new RoundRectangle { CornerRadius = 10 }
                    };
                    // place the dot in the second (auto) column
                    titleRow.Add(colorDot, 1, 0);
                }

                itemStack.Children.Add(titleRow);

                // Body formatted like NoticePage.xaml: show only when description exists
                if (!string.IsNullOrWhiteSpace(c.Description))
                {
                    var bodyVBox = new VerticalStackLayout { Spacing = 6 };

                    var bodyLabel = new Label();
                    var formatted = TkOlympApp.Helpers.HtmlHelpers.ToFormattedString(c.Description);
                    bodyLabel.FormattedText = formatted;
                    bodyVBox.Children.Add(bodyLabel);

                    // Add the content directly (no inner Border) — outer wrapper provides card framing and padding
                    itemStack.Children.Add(bodyVBox);
                }

                // Wrap title and body inside a bordered card so the name is inside the Border
                // Use symmetric padding so the dot isn't flush with the card edge
                var wrapper = new Border { Padding = new Thickness(12) };
                wrapper.StrokeShape = new RoundRectangle { CornerRadius = 8 };
                wrapper.Content = itemStack;

                GroupsStack.Children.Add(wrapper);
            }

            // Manually add the "Základní členství" group below all others
            try
            {
                var manualStack = new VerticalStackLayout { Spacing = 8 };

                var manualTitleRow = new Grid { VerticalOptions = LayoutOptions.Center, HorizontalOptions = LayoutOptions.Fill };
                manualTitleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Star });
                manualTitleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var manualTitle = new Label { Text = "Základní členství", FontAttributes = FontAttributes.Bold, FontSize = 18, HorizontalOptions = LayoutOptions.Start };
                manualTitleRow.Add(manualTitle);

                var manualBrush = TryParseColorBrush("#ffffff") ?? new SolidColorBrush(Colors.LightGray);
                var manualDot = new Border
                {
                    Background = manualBrush,
                    Padding = 0,
                    WidthRequest = 20,
                    HeightRequest = 20,
                    Margin = new Thickness(0),
                    HorizontalOptions = LayoutOptions.End,
                    VerticalOptions = LayoutOptions.Center,
                    Stroke = null,
                    StrokeShape = new RoundRectangle { CornerRadius = 10 }
                };
                manualTitleRow.Add(manualDot, 1, 0);

                manualStack.Children.Add(manualTitleRow);

                var manualBodyVBox = new VerticalStackLayout { Spacing = 6 };
                var manualBodyLabel = new Label();
                var manualHtml = "<p>Pro členy bez příslušnosti ke skupině</p><ul><li>přístup na sály pro volný trénink</li><li>Středa 18:00 Practise, Hýža, SGO</li></ul>";
                var manualFormatted = TkOlympApp.Helpers.HtmlHelpers.ToFormattedString(manualHtml);
                manualBodyLabel.FormattedText = manualFormatted;
                manualBodyVBox.Children.Add(manualBodyLabel);

                manualStack.Children.Add(manualBodyVBox);

                var manualWrapper = new Border { Padding = new Thickness(12) };
                manualWrapper.StrokeShape = new RoundRectangle { CornerRadius = 8 };
                manualWrapper.Content = manualStack;

                GroupsStack.Children.Add(manualWrapper);
            }
            catch { }
        }
        catch (Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
    }

    private Brush? TryParseColorBrush(string? colorRgb)
    {
        if (string.IsNullOrWhiteSpace(colorRgb)) return null;
        var s = colorRgb.Trim();
        try
        {
            // Accept #RRGGBB, RRGGBB and rgb(r,g,b)
            if (s.StartsWith("#"))
                return new SolidColorBrush(Color.FromArgb(s));

            if (s.Length == 6)
                return new SolidColorBrush(Color.FromArgb("#" + s));

            if (s.StartsWith("rgb", StringComparison.OrdinalIgnoreCase))
            {
                var digits = System.Text.RegularExpressions.Regex.Matches(s, "\\d+");
                if (digits.Count >= 3)
                {
                    var r = int.Parse(digits[0].Value);
                    var g = int.Parse(digits[1].Value);
                    var b = int.Parse(digits[2].Value);
                    return new SolidColorBrush(Color.FromRgb(r, g, b));
                }
            }
        }
        catch { }
        return null;
    }
}
