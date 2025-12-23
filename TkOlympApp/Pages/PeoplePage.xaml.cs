using Microsoft.Maui.Controls;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using TkOlympApp.Services;
using System;
using System.Linq;
using System.Collections.Generic;

namespace TkOlympApp.Pages;

public partial class PeoplePage : ContentPage
{
    private readonly ObservableCollection<PeopleService.Person> _people = new();
    private List<PeopleService.Person> _allPeople = new();
    private bool _isLoading;
    private enum SortMode { Alphabetical = 0, UpcomingBirthday = 1 }
    private SortMode _sortMode = SortMode.Alphabetical;

    public PeoplePage()
    {
        InitializeComponent();
        PeopleCollection.ItemsSource = _people;
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
        Loading.IsVisible = true;
        Loading.IsRunning = true;
        PeopleCollection.IsVisible = false;
        try
        {
            var list = await PeopleService.GetPeopleAsync();
            _allPeople = list ?? new List<PeopleService.Person>();
            ApplySort();
        }
        catch (System.Exception ex)
        {
            await DisplayAlertAsync(LocalizationService.Get("Error_Loading_Title"), ex.Message, LocalizationService.Get("Button_OK"));
        }
        finally
        {
            Loading.IsRunning = false;
            Loading.IsVisible = false;
            PeopleCollection.IsVisible = true;
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

    private void ApplySort()
    {
        if (_allPeople == null) return;
        IEnumerable<PeopleService.Person> ordered;
        if (_sortMode == SortMode.Alphabetical)
        {
            ordered = _allPeople.OrderBy(p => p.FullName, StringComparer.CurrentCultureIgnoreCase);
        }
        else
        {
            var today = DateTime.Today;
            ordered = _allPeople.OrderBy(p => GetDaysUntilNextBirthday(p, today));
        }

        _people.Clear();
        foreach (var p in ordered)
            _people.Add(p);
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
            ApplySort();
        }
        catch { }
    }
}
