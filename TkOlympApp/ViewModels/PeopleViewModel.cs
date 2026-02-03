using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Helpers;
using TkOlympApp.Models.People;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class PeopleViewModel : ViewModelBase
{
    private readonly IPeopleService _peopleService;
    private readonly INavigationService _navigationService;
    private readonly IUserNotifier _notifier;

    private List<Person> _allPeople = new();
    private bool _isLoading;

    private enum SortMode { Alphabetical = 0, UpcomingBirthday = 1 }
    private SortMode _sortMode = SortMode.Alphabetical;
    private readonly List<CohortFilter> _cohortFilters = new();

    public ObservableCollection<PersonItem> People { get; } = new();
    public ObservableCollection<string> SortOptions { get; } = new();

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private bool _isFilterVisible;

    [ObservableProperty]
    private int _selectedSortIndex;

    [ObservableProperty]
    private PersonItem? _selectedPerson;

    public PeopleViewModel(IPeopleService peopleService, INavigationService navigationService, IUserNotifier notifier)
    {
        _peopleService = peopleService ?? throw new ArgumentNullException(nameof(peopleService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));

        SortOptions.Add(LocalizationService.Get("People_Sort_Alphabetical") ?? "Alphabetical");
        SortOptions.Add(LocalizationService.Get("People_Sort_ByBirthdays") ?? "By birthdays");
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        await LoadPeopleAsync();
        SelectedSortIndex = (int)_sortMode;
    }

    partial void OnSelectedSortIndexChanged(int value)
    {
        _sortMode = value == 1 ? SortMode.UpcomingBirthday : SortMode.Alphabetical;
        ApplySortAndFilter();
    }

    partial void OnSelectedPersonChanged(PersonItem? value)
    {
        if (value == null) return;
        _ = OpenPersonAsync(value);
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadPeopleAsync();
    }

    [RelayCommand]
    private async Task FilterAsync()
    {
        try
        {
            if (_cohortFilters.Count == 0) return;

            var selectAllText = LocalizationService.Get("People_SelectAll") ?? "Select All";
            var deselectAllText = LocalizationService.Get("People_DeselectAll") ?? "Deselect All";
            const string separator = "────────────────";

            var actionsList = new List<string>
            {
                selectAllText,
                deselectAllText,
                separator
            };
            actionsList.AddRange(_cohortFilters.Select(f => $"{(f.IsChecked ? "✓" : "  ")} {f.CohortName}"));

            var result = await MainThread.InvokeOnMainThreadAsync(() =>
                Application.Current?.MainPage?.DisplayActionSheet(
                    LocalizationService.Get("People_FilterByCohorts"),
                    LocalizationService.Get("Button_Cancel"),
                    null,
                    actionsList.ToArray()));

            if (result == null || result == LocalizationService.Get("Button_Cancel")) return;

            if (result == selectAllText)
            {
                foreach (var filter in _cohortFilters)
                    filter.IsChecked = true;
                ApplySortAndFilter();
                _ = FilterAsync();
                return;
            }

            if (result == deselectAllText)
            {
                foreach (var filter in _cohortFilters)
                    filter.IsChecked = false;
                ApplySortAndFilter();
                _ = FilterAsync();
                return;
            }

            if (result == separator) return;

            for (int i = 0; i < _cohortFilters.Count; i++)
            {
                var expectedText = $"{(_cohortFilters[i].IsChecked ? "✓" : "  ")} {_cohortFilters[i].CohortName}";
                if (result == expectedText)
                {
                    _cohortFilters[i].IsChecked = !_cohortFilters[i].IsChecked;
                    ApplySortAndFilter();
                    _ = FilterAsync();
                    break;
                }
            }
        }
        catch
        {
        }
    }

    private async Task LoadPeopleAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        IsRefreshing = true;

        try
        {
            var list = await _peopleService.GetPeopleAsync();
            _allPeople = list ?? new List<Person>();
            BuildCohortFilterData();
            ApplySortAndFilter();
        }
        catch (Exception ex)
        {
            await _notifier.ShowAsync(LocalizationService.Get("Error_Loading_Title") ?? "Chyba", ex.Message,
                LocalizationService.Get("Button_OK") ?? "OK");
        }
        finally
        {
            IsRefreshing = false;
            _isLoading = false;
        }
    }

    private void BuildCohortFilterData()
    {
        _cohortFilters.Clear();
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

        _cohortFilters.AddRange(cohortSet.Values.OrderBy(c => c.CohortName));
        IsFilterVisible = _cohortFilters.Count > 0;
    }

    private void ApplySortAndFilter()
    {
        if (_allPeople == null) return;

        var filtered = _allPeople.AsEnumerable();
        var selectedCohortIds = _cohortFilters.Where(f => f.IsChecked).Select(f => f.CohortId).ToHashSet();
        var hasActiveFilter = selectedCohortIds.Count > 0 && selectedCohortIds.Count < _cohortFilters.Count;

        if (hasActiveFilter)
        {
            filtered = filtered.Where(p =>
            {
                if (p.CohortMembershipsList == null || p.CohortMembershipsList.Count == 0)
                    return false;
                return p.CohortMembershipsList.Any(m => m.Cohort?.Id != null && selectedCohortIds.Contains(m.Cohort.Id));
            });
        }

        IEnumerable<Person> ordered = _sortMode == SortMode.Alphabetical
            ? filtered.OrderBy(p => p.FullName, StringComparer.CurrentCultureIgnoreCase)
            : filtered.OrderBy(p => GetDaysUntilNextBirthday(p, DateTime.Today));

        People.Clear();
        foreach (var person in ordered)
        {
            People.Add(CreatePersonItem(person));
        }
    }

    private PersonItem CreatePersonItem(Person person)
    {
        var primaryColor = GetPrimaryColor();
        var item = new PersonItem
        {
            Id = person.Id,
            FullName = person.FullName,
            DisplayBirthDate = person.DisplayBirthDate ?? string.Empty,
            HasBirthDate = !string.IsNullOrWhiteSpace(person.DisplayBirthDate),
            IsBirthdayToday = person.IsBirthdayToday,
            StrokeThickness = person.IsBirthdayToday ? 2 : 1,
            StrokeBrush = person.IsBirthdayToday ? new SolidColorBrush(primaryColor) : new SolidColorBrush(Colors.LightGray),
            BirthdayIconColor = primaryColor
        };

        if (person.CohortMembershipsList != null)
        {
            foreach (var membership in person.CohortMembershipsList)
            {
                var cohort = membership.Cohort;
                if (cohort?.IsVisible != true) continue;
                var brush = CohortColorHelper.ParseColorBrush(cohort.ColorRgb) ?? new SolidColorBrush(Colors.LightGray);
                item.CohortDots.Add(new CohortDot { Color = brush });
            }
        }

        return item;
    }

    private static Color GetPrimaryColor()
    {
        try
        {
            var color = Application.Current?.Resources["Primary"] as Color;
            if (color != null) return color;
        }
        catch
        {
        }
        return Colors.Blue;
    }

    private async Task OpenPersonAsync(PersonItem item)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(item.Id)) return;
            await _navigationService.NavigateToAsync(nameof(Pages.PersonPage), new Dictionary<string, object>
            {
                ["personId"] = item.Id
            });
        }
        catch
        {
        }
        finally
        {
            SelectedPerson = null;
        }
    }

    private static int GetDaysUntilNextBirthday(Person p, DateTime today)
    {
        if (string.IsNullOrWhiteSpace(p.BirthDate)) return int.MaxValue;
        if (!DateTime.TryParse(p.BirthDate, out var bd)) return int.MaxValue;

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

    private sealed class CohortFilter
    {
        public string? CohortId { get; set; }
        public string? CohortName { get; set; }
        public string? CohortColor { get; set; }
        public bool IsChecked { get; set; } = true;
    }

    public sealed class CohortDot
    {
        public Brush Color { get; set; } = new SolidColorBrush(Colors.LightGray);
    }

    public sealed class PersonItem
    {
        public string? Id { get; set; }
        public string FullName { get; set; } = string.Empty;
        public string DisplayBirthDate { get; set; } = string.Empty;
        public bool HasBirthDate { get; set; }
        public bool IsBirthdayToday { get; set; }
        public Brush StrokeBrush { get; set; } = new SolidColorBrush(Colors.LightGray);
        public double StrokeThickness { get; set; } = 1;
        public Color BirthdayIconColor { get; set; } = Colors.Blue;
        public ObservableCollection<CohortDot> CohortDots { get; } = new();
    }
}
