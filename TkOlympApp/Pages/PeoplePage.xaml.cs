using Microsoft.Maui.Controls;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using TkOlympApp.Services;
using System;
using System.Linq;
using System.Collections.Generic;
using Microsoft.Maui.Controls.Shapes;
using Microsoft.Maui.Graphics;
using MauiIcons.Material;

namespace TkOlympApp.Pages;

public partial class PeoplePage : ContentPage
{
    private List<PeopleService.Person> _allPeople = new();
    private bool _isLoading;
    private enum SortMode { Alphabetical = 0, UpcomingBirthday = 1 }
    private SortMode _sortMode = SortMode.Alphabetical;
    private List<CohortFilter> _cohortFilters = new();

    private class CohortFilter
    {
        public string? CohortId { get; set; }
        public string? CohortName { get; set; }
        public string? CohortColor { get; set; }
        public bool IsChecked { get; set; } = true;
    }

    public PeoplePage()
    {
        InitializeComponent();
        try
        {
            SortPicker.Items.Clear();
            SortPicker.Items.Add(LocalizationService.Get("People_Sort_Alphabetical") ?? "Alphabetical");
            SortPicker.Items.Add(LocalizationService.Get("People_Sort_ByBirthdays") ?? "By birthdays");
        }
        catch { }
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadPeopleAsync();
        // default picker index
        try { SortPicker.SelectedIndex = (int)_sortMode; } catch { }
    }

    private async Task LoadPeopleAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        PeopleStack.IsVisible = false;
        try
        {
            var list = await PeopleService.GetPeopleAsync();
            _allPeople = list ?? new List<PeopleService.Person>();
            BuildCohortFilterData();
            ApplySortAndFilter();
        }
        catch (System.Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            PeopleStack.IsVisible = true;
            try
            {
                if (PeopleRefresh != null)
                    PeopleRefresh.IsRefreshing = false;
            }
            catch { }
            _isLoading = false;
        }
    }

    private async void OnPeopleRefresh(object? sender, System.EventArgs e)
    {
        await LoadPeopleAsync();
        try
        {
            if (PeopleRefresh != null)
                PeopleRefresh.IsRefreshing = false;
        }
        catch { }
    }

    private void BuildCohortFilterData()
    {
        var cohortSet = new Dictionary<string, CohortFilter>();
        foreach (var person in _allPeople)
        {
            if (person.CohortMembershipsList == null) continue;
            foreach (var membership in person.CohortMembershipsList)
            {
                var cohort = membership.Cohort;
                if (cohort?.Id == null || cohort.IsVisible != true) continue;
                if (!cohortSet.ContainsKey(cohort.Id))
                {
                    cohortSet[cohort.Id] = new CohortFilter
                    {
                        CohortId = cohort.Id,
                        CohortName = cohort.Name,
                        CohortColor = cohort.ColorRgb,
                        IsChecked = true
                    };
                }
            }
        }

        _cohortFilters = cohortSet.Values.OrderBy(c => c.CohortName).ToList();
        UpdateFilterButtonVisibility();
    }

    private void UpdateFilterButtonVisibility()
    {
        try
        {
            // Hide filter button if no cohorts
            if (FilterButton != null)
            {
                FilterButton.IsVisible = _cohortFilters.Count > 0;
            }
        }
        catch { }
    }

    private async void OnFilterClicked(object? sender, EventArgs e)
    {
        try
        {
            if (_cohortFilters.Count == 0) return;

            var selectAllText = LocalizationService.Get("People_SelectAll");
            var deselectAllText = LocalizationService.Get("People_DeselectAll");
            var separator = "────────────────";

            var actionsList = new List<string>();
            actionsList.Add(selectAllText ?? "Select All");
            actionsList.Add(deselectAllText ?? "Deselect All");
            actionsList.Add(separator);
            actionsList.AddRange(_cohortFilters.Select(f => $"{(f.IsChecked ? "✓" : "  ")} {f.CohortName}"));

            var actions = actionsList.ToArray();
            var result = await DisplayActionSheetAsync(
                LocalizationService.Get("People_FilterByCohorts"),
                LocalizationService.Get("Button_Cancel"),
                null,
                actions
            );

            if (result == null || result == LocalizationService.Get("Button_Cancel")) return;

            // Handle select/deselect all
            if (result == selectAllText)
            {
                foreach (var filter in _cohortFilters)
                    filter.IsChecked = true;
                ApplySortAndFilter();
                Dispatcher.Dispatch(() => OnFilterClicked(sender, e));
                return;
            }

            if (result == deselectAllText)
            {
                foreach (var filter in _cohortFilters)
                    filter.IsChecked = false;
                ApplySortAndFilter();
                Dispatcher.Dispatch(() => OnFilterClicked(sender, e));
                return;
            }

            if (result == separator) return;

            // Find which cohort was clicked and toggle it
            for (int i = 0; i < _cohortFilters.Count; i++)
            {
                var expectedText = $"{(_cohortFilters[i].IsChecked ? "✓" : "  ")} {_cohortFilters[i].CohortName}";
                if (result == expectedText)
                {
                    _cohortFilters[i].IsChecked = !_cohortFilters[i].IsChecked;
                    ApplySortAndFilter();
                    // Re-open the filter dialog
                    Dispatcher.Dispatch(() => OnFilterClicked(sender, e));
                    break;
                }
            }
        }
        catch { }
    }

    private void ApplySortAndFilter()
    {
        if (_allPeople == null) return;

        // Filter by cohorts - only if some filters are unchecked
        var filtered = _allPeople.AsEnumerable();
        var selectedCohortIds = _cohortFilters.Where(f => f.IsChecked).Select(f => f.CohortId).ToHashSet();
        var hasActiveFilter = selectedCohortIds.Count > 0 && selectedCohortIds.Count < _cohortFilters.Count;
        
        if (hasActiveFilter)
        {
            filtered = filtered.Where(p =>
            {
                if (p.CohortMembershipsList == null || p.CohortMembershipsList.Count == 0)
                    return false; // Hide people without cohorts if filters are active
                return p.CohortMembershipsList.Any(m => m.Cohort?.Id != null && selectedCohortIds.Contains(m.Cohort.Id));
            });
        }

        // Sort
        IEnumerable<PeopleService.Person> ordered;
        if (_sortMode == SortMode.Alphabetical)
        {
            ordered = filtered.OrderBy(p => p.FullName, StringComparer.CurrentCultureIgnoreCase);
        }
        else
        {
            var today = DateTime.Today;
            ordered = filtered.OrderBy(p => GetDaysUntilNextBirthday(p, today));
        }

        PeopleStack.Children.Clear();
        foreach (var person in ordered)
        {
            PeopleStack.Children.Add(CreatePersonCard(person));
        }
    }

    private View CreatePersonCard(PeopleService.Person person)
    {
        var border = new Border
        {
            StrokeShape = new RoundRectangle { CornerRadius = 8 },
            StrokeThickness = 1,
            Padding = new Thickness(12),
            Margin = new Thickness(0, 6)
        };

        if (person.IsBirthdayToday)
        {
            try
            {
                border.Stroke = Application.Current?.Resources["Primary"] as Color ?? Colors.Blue;
                border.StrokeThickness = 2;
            }
            catch { }
        }

        var grid = new Grid
        {
            ColumnDefinitions =
            {
                new ColumnDefinition { Width = GridLength.Star },
                new ColumnDefinition { Width = GridLength.Auto }
            },
            ColumnSpacing = 8,
            HorizontalOptions = LayoutOptions.Fill,
            VerticalOptions = LayoutOptions.Center
        };

        var leftStack = new VerticalStackLayout
        {
            HorizontalOptions = LayoutOptions.Start,
            VerticalOptions = LayoutOptions.Center
        };

        leftStack.Children.Add(new Label
        {
            Text = person.FullName,
            FontAttributes = FontAttributes.Bold
        });

        if (!string.IsNullOrWhiteSpace(person.DisplayBirthDate))
        {
            var dateLabel = new Label
            {
                Text = person.DisplayBirthDate,
                FontSize = 12
            };
            try
            {
                dateLabel.Style = Application.Current?.Resources["MutedLabelStyle"] as Style;
            }
            catch { }
            leftStack.Children.Add(dateLabel);
        }

        grid.Add(leftStack, 0, 0);

        var rightStack = new HorizontalStackLayout
        {
            Spacing = 4,
            HorizontalOptions = LayoutOptions.End,
            VerticalOptions = LayoutOptions.Center
        };

        if (person.IsBirthdayToday)
        {
            var cakeIcon = new Label
            {
                Text = "\ue7e9", // Material Icons Cake
                FontFamily = "MaterialIcons",
                FontSize = 26
            };
            try
            {
                cakeIcon.TextColor = Application.Current?.Resources["Primary"] as Color ?? Colors.Blue;
            }
            catch { }
            rightStack.Children.Add(cakeIcon);
        }

        // Add cohort circles
        if (person.CohortMembershipsList != null)
        {
            foreach (var membership in person.CohortMembershipsList)
            {
                var cohort = membership.Cohort;
                if (cohort?.IsVisible != true) continue;

                var brush = TryParseColorBrush(cohort.ColorRgb) ?? new SolidColorBrush(Colors.LightGray);
                var circle = new Border
                {
                    Background = brush,
                    Padding = 0,
                    WidthRequest = 20,
                    HeightRequest = 20,
                    Margin = new Thickness(0),
                    HorizontalOptions = LayoutOptions.Center,
                    VerticalOptions = LayoutOptions.Center,
                    Stroke = null,
                    StrokeShape = new RoundRectangle { CornerRadius = 10 }
                };
                rightStack.Children.Add(circle);
            }
        }

        grid.Add(rightStack, 1, 0);
        border.Content = grid;

        // Add tap gesture
        var tapGesture = new TapGestureRecognizer();
        tapGesture.Tapped += async (s, e) =>
        {
            try
            {
                var id = person.Id;
                if (!string.IsNullOrWhiteSpace(id))
                {
                    await Shell.Current.GoToAsync($"{nameof(PersonPage)}?personId={Uri.EscapeDataString(id)}");
                }
            }
            catch { }
        };
        border.GestureRecognizers.Add(tapGesture);

        return border;
    }

    private Brush? TryParseColorBrush(string? colorRgb)
    {
        if (string.IsNullOrWhiteSpace(colorRgb)) return null;
        var s = colorRgb.Trim();
        try
        {
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

    // Returns int.MaxValue if no valid birth date (so they appear last)
    private int GetDaysUntilNextBirthday(PeopleService.Person p, DateTime today)
    {
        if (string.IsNullOrWhiteSpace(p.BirthDate)) return int.MaxValue;
        if (!DateTime.TryParse(p.BirthDate, out var bd)) return int.MaxValue;

        // Find next occurrence (handle Feb 29 and month/day edge cases)
        DateTime? next = null;
        for (int year = today.Year; year <= today.Year + 1; year++)
        {
            var daysInMonth = DateTime.DaysInMonth(year, bd.Month);
            var day = Math.Min(bd.Day, daysInMonth);
            var candidate = new DateTime(year, bd.Month, day);
            if (candidate >= today)
            {
                next = candidate;
                break;
            }
        }
        if (!next.HasValue) return int.MaxValue;
        return (int)(next.Value - today).TotalDays;
    }

    private void OnSortChanged(object? sender, EventArgs e)
    {
        try
        {
            if (SortPicker.SelectedIndex == 1) _sortMode = SortMode.UpcomingBirthday;
            else _sortMode = SortMode.Alphabetical;
            ApplySortAndFilter();
        }
        catch { }
    }
}
