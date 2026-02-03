using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading;
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

public partial class PersonViewModel : ViewModelBase
{
    private static readonly ConcurrentDictionary<string, (PersonDetail Person, DateTime FetchedAt)> PersonCache = new();
    private static readonly TimeSpan CacheTtl = TimeSpan.FromMinutes(5);

    private readonly IPeopleService _peopleService;
    private readonly INavigationService _navigationService;

    private bool _appeared;
    private bool _loadRequested;
    private CancellationTokenSource? _cts;

    public ObservableCollection<ActiveCoupleDisplay> ActiveCouples { get; } = new();
    public ObservableCollection<CohortItem> Cohorts { get; } = new();

    [ObservableProperty]
    private string? _personId;

    [ObservableProperty]
    private string _errorText = string.Empty;

    [ObservableProperty]
    private bool _isErrorVisible;

    [ObservableProperty]
    private bool _isRefreshing;

    [ObservableProperty]
    private string _nameText = "—";

    [ObservableProperty]
    private string _surnameText = "—";

    [ObservableProperty]
    private string _emailText = "—";

    [ObservableProperty]
    private string _bioText = "—";

    [ObservableProperty]
    private string _birthDateText = "—";

    [ObservableProperty]
    private string _phoneText = "—";

    [ObservableProperty]
    private string _genderText = "—";

    [ObservableProperty]
    private string _isTrainerText = "—";

    [ObservableProperty]
    private string _wdsfIdText = "—";

    [ObservableProperty]
    private string _cstsIdText = "—";

    [ObservableProperty]
    private string _proxyIdText = "—";

    [ObservableProperty]
    private bool _emailVisible;

    [ObservableProperty]
    private bool _bioVisible;

    [ObservableProperty]
    private bool _birthDateVisible;

    [ObservableProperty]
    private bool _phoneVisible;

    [ObservableProperty]
    private bool _genderVisible;

    [ObservableProperty]
    private bool _isTrainerVisible;

    [ObservableProperty]
    private bool _wdsfVisible;

    [ObservableProperty]
    private bool _cstsVisible;

    [ObservableProperty]
    private bool _proxyIdVisible;

    [ObservableProperty]
    private bool _contactBorderVisible;

    [ObservableProperty]
    private bool _personalBorderVisible;

    [ObservableProperty]
    private bool _idsBorderVisible;

    [ObservableProperty]
    private bool _cohortsVisible;

    [ObservableProperty]
    private bool _activeCouplesVisible;

    [ObservableProperty]
    private ActiveCoupleDisplay? _selectedActiveCouple;

    public PersonViewModel(IPeopleService peopleService, INavigationService navigationService)
    {
        _peopleService = peopleService ?? throw new ArgumentNullException(nameof(peopleService));
        _navigationService = navigationService ?? throw new ArgumentNullException(nameof(navigationService));
    }

    partial void OnPersonIdChanged(string? value)
    {
        _loadRequested = true;
        if (_appeared)
        {
            _ = LoadAsync();
        }
    }

    partial void OnSelectedActiveCoupleChanged(ActiveCoupleDisplay? value)
    {
        if (value == null) return;
        _ = OpenActiveCoupleAsync(value);
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        _appeared = true;
        if (_loadRequested)
        {
            await LoadAsync();
        }
    }

    public override Task OnDisappearingAsync()
    {
        _appeared = false;
        try { _cts?.Cancel(); }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<PersonViewModel>("Failed to cancel CTS: {0}", new object[] { ex.Message });
        }
        return base.OnDisappearingAsync();
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        if (!string.IsNullOrWhiteSpace(PersonId)) PersonCache.TryRemove(PersonId, out _);
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        IsErrorVisible = false;
        ErrorText = string.Empty;

        try
        {
            try { _cts?.Cancel(); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<PersonViewModel>("Failed to cancel CTS on reload: {0}", new object[] { ex.Message });
            }
            _cts = new CancellationTokenSource();
            var ct = _cts.Token;

            if (string.IsNullOrWhiteSpace(PersonId))
            {
                IsErrorVisible = true;
                ErrorText = "Missing personId";
                return;
            }

            if (PersonCache.TryGetValue(PersonId, out var cached) && DateTime.UtcNow - cached.FetchedAt < CacheTtl)
            {
                ApplyBasicPerson(cached.Person);
                _ = LoadExtrasAsync(PersonId, ct);
                return;
            }

            var person = await _peopleService.GetPersonBasicAsync(PersonId, ct);
            if (person == null)
            {
                IsErrorVisible = true;
                ErrorText = LocalizationService.Get("NotFound_Person") ?? "Person not found";
                return;
            }

            PersonCache[PersonId] = (person, DateTime.UtcNow);
            ApplyBasicPerson(person);

            _ = LoadExtrasAsync(PersonId, ct);
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            IsErrorVisible = true;
            ErrorText = ex.Message;
        }
        finally
        {
            _loadRequested = false;
        }
    }

    private void ApplyBasicPerson(PersonDetail person)
    {
        NameText = FormatPrefixName(person.PrefixTitle, person.FirstName);
        SurnameText = FormatNameSuffix(person.LastName, person.SuffixTitle);
        BioText = NonEmpty(person.Bio?.Trim());
        BirthDateText = FormatDtString(person.BirthDate);
        PhoneText = NonEmpty(PhoneHelpers.Format(person.Phone?.Trim()));
        GenderText = MapGender(person.Gender);
        IsTrainerText = person.IsTrainer.HasValue
            ? (person.IsTrainer.Value ? LocalizationService.Get("About_Yes") : LocalizationService.Get("About_No"))
            : "—";
        WdsfIdText = NonEmpty(person.WdsfId?.Trim());
        CstsIdText = NonEmpty(person.CstsId?.Trim());
        EmailText = NonEmpty(person.Email?.Trim());

        EmailVisible = !string.IsNullOrWhiteSpace(person.Email);
        BioVisible = !string.IsNullOrWhiteSpace(person.Bio);
        BirthDateVisible = !string.IsNullOrWhiteSpace(person.BirthDate);
        PhoneVisible = !string.IsNullOrWhiteSpace(person.Phone);
        GenderVisible = !string.IsNullOrWhiteSpace(person.Gender);
        IsTrainerVisible = person.IsTrainer.HasValue;
        WdsfVisible = !string.IsNullOrWhiteSpace(person.WdsfId);
        CstsVisible = !string.IsNullOrWhiteSpace(person.CstsId);
        ProxyIdVisible = false;
        ProxyIdText = "—";

        UpdateBorderVisibility();
    }

    private async Task LoadExtrasAsync(string personId, CancellationToken ct)
    {
        try
        {
            var person = await _peopleService.GetPersonExtrasAsync(personId, ct);
            if (person == null) return;

            if (PersonCache.TryGetValue(personId, out var existing))
            {
                try
                {
                    var merged = existing.Person with
                    {
                        ActiveCouplesList = person.ActiveCouplesList,
                        CohortMembershipsList = person.CohortMembershipsList
                    };
                    PersonCache[personId] = (merged, DateTime.UtcNow);
                }
                catch (Exception ex)
                {
                    LoggerService.SafeLogWarning<PersonViewModel>("Failed to merge person extras: {0}", new object[] { ex.Message });
                }
            }

            MainThread.BeginInvokeOnMainThread(() =>
            {
                UpdateActiveCouples(person.ActiveCouplesList);
                UpdateCohorts(person.CohortMembershipsList);
            });
        }
        catch (OperationCanceledException)
        {
            // ignored
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<PersonViewModel>("Failed to load person extras: {0}", new object[] { ex.Message });
        }
    }

    private void UpdateActiveCouples(List<PersonActiveCouple>? couples)
    {
        ActiveCouples.Clear();
        foreach (var c in couples ?? new List<PersonActiveCouple>())
        {
            var manFirst = c.Man?.FirstName?.Trim() ?? string.Empty;
            var manLast = c.Man?.LastName?.Trim() ?? string.Empty;
            var womanFirst = c.Woman?.FirstName?.Trim() ?? string.Empty;
            var womanLast = c.Woman?.LastName?.Trim() ?? string.Empty;

            var man = string.IsNullOrWhiteSpace(manFirst) ? manLast : (string.IsNullOrWhiteSpace(manLast) ? manFirst : (manFirst + " " + manLast).Trim());
            var woman = string.IsNullOrWhiteSpace(womanFirst) ? womanLast : (string.IsNullOrWhiteSpace(womanLast) ? womanFirst : (womanFirst + " " + womanLast).Trim());

            string entry;
            if (!string.IsNullOrWhiteSpace(man) && !string.IsNullOrWhiteSpace(woman)) entry = man + " – " + woman;
            else if (!string.IsNullOrWhiteSpace(man)) entry = man;
            else if (!string.IsNullOrWhiteSpace(woman)) entry = woman;
            else entry = "(–)";

            ActiveCouples.Add(new ActiveCoupleDisplay { Id = c.Id, Text = entry });
        }

        ActiveCouplesVisible = ActiveCouples.Count > 0;
    }

    private void UpdateCohorts(List<PersonCohortMembership>? memberships)
    {
        Cohorts.Clear();
        var cohortsList = (memberships ?? new List<PersonCohortMembership>())
            .Where(m => m?.Cohort?.IsVisible != false)
            .OrderBy(m => m?.Cohort?.Ordering ?? int.MaxValue)
            .ToList();

        foreach (var membership in cohortsList)
        {
            var c = membership?.Cohort;
            if (c == null) continue;
            var name = c.Name ?? string.Empty;
            var colorBrush = CohortColorHelper.ParseColorBrush(c.ColorRgb)
                             ?? new SolidColorBrush(Colors.LightGray);

            Cohorts.Add(new CohortItem { Name = name, Color = colorBrush });
        }

        CohortsVisible = Cohorts.Count > 0;
    }

    private async Task OpenActiveCoupleAsync(ActiveCoupleDisplay item)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(item.Id)) return;
            await _navigationService.NavigateToAsync(nameof(Pages.CouplePage), new Dictionary<string, object>
            {
                ["id"] = item.Id
            });
        }
        catch
        {
        }
        finally
        {
            SelectedActiveCouple = null;
        }
    }

    private void UpdateBorderVisibility()
    {
        ContactBorderVisible = EmailVisible || PhoneVisible;
        PersonalBorderVisible = BioVisible || BirthDateVisible || GenderVisible || IsTrainerVisible;
        IdsBorderVisible = ProxyIdVisible || WdsfVisible || CstsVisible;
    }

    private static string FormatDtString(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return "—";
        if (DateTime.TryParse(s, out var dt)) return dt.ToLocalTime().ToString("dd.MM.yyyy");
        return s;
    }

    private static string NonEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? "—" : s;

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

    private static string FormatPrefixName(string? prefix, string? name)
    {
        prefix = prefix?.Trim();
        name = name?.Trim();
        if (string.IsNullOrWhiteSpace(prefix) && string.IsNullOrWhiteSpace(name)) return "—";
        if (string.IsNullOrWhiteSpace(name)) return prefix ?? "—";
        if (string.IsNullOrWhiteSpace(prefix)) return name!;
        return prefix + " " + name;
    }

    private static string FormatNameSuffix(string? name, string? suffix)
    {
        name = name?.Trim();
        suffix = suffix?.Trim();
        if (string.IsNullOrWhiteSpace(name) && string.IsNullOrWhiteSpace(suffix)) return "—";
        if (string.IsNullOrWhiteSpace(name)) return suffix ?? "—";
        if (string.IsNullOrWhiteSpace(suffix)) return name!;
        return name + " " + suffix;
    }

    public sealed class ActiveCoupleDisplay
    {
        public string? Id { get; set; }
        public string Text { get; set; } = string.Empty;
    }

    public sealed class CohortItem
    {
        public string Name { get; set; } = string.Empty;
        public Brush Color { get; set; } = new SolidColorBrush(Colors.LightGray);
    }
}
