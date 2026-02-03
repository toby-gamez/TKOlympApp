using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using TkOlympApp.Helpers;
using TkOlympApp.Models.People;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class EditSelfViewModel : ViewModelBase
{
    private readonly IPeopleService _peopleService;
    private readonly IUserService _userService;
    private readonly IUserNotifier _notifier;

    private string? _personId;
    private bool _suppressBirthDateChange;

    public ObservableCollection<string> GenderOptions { get; } = new();
    public ObservableCollection<string> NationalityOptions { get; } = new();

    [ObservableProperty]
    private string _errorText = string.Empty;

    [ObservableProperty]
    private bool _isErrorVisible;

    [ObservableProperty]
    private string _firstName = string.Empty;

    [ObservableProperty]
    private string _lastName = string.Empty;

    [ObservableProperty]
    private string _prefixTitle = string.Empty;

    [ObservableProperty]
    private string _suffixTitle = string.Empty;

    [ObservableProperty]
    private int _selectedGenderIndex;

    [ObservableProperty]
    private DateTime _birthDate = DateTime.Today;

    [ObservableProperty]
    private bool _birthDateSet;

    [ObservableProperty]
    private string _email = string.Empty;

    [ObservableProperty]
    private string _phone = string.Empty;

    [ObservableProperty]
    private string _selectedNationality = string.Empty;

    [ObservableProperty]
    private string _wdsfId = string.Empty;

    [ObservableProperty]
    private string _cstsId = string.Empty;

    [ObservableProperty]
    private string _nationalId = string.Empty;

    [ObservableProperty]
    private string _street = string.Empty;

    [ObservableProperty]
    private string _city = string.Empty;

    [ObservableProperty]
    private string _postalCode = string.Empty;

    [ObservableProperty]
    private string _region = string.Empty;

    [ObservableProperty]
    private string _district = string.Empty;

    [ObservableProperty]
    private string _conscriptionNumber = string.Empty;

    [ObservableProperty]
    private string _orientationNumber = string.Empty;

    [ObservableProperty]
    private string _bio = string.Empty;

    public EditSelfViewModel(IPeopleService peopleService, IUserService userService, IUserNotifier notifier)
    {
        _peopleService = peopleService ?? throw new ArgumentNullException(nameof(peopleService));
        _userService = userService ?? throw new ArgumentNullException(nameof(userService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();

        if (string.IsNullOrWhiteSpace(_personId))
        {
            _personId = _userService.CurrentPersonId;
        }

        await LoadAsync();
    }

    partial void OnBirthDateChanged(DateTime value)
    {
        if (_suppressBirthDateChange) return;
        BirthDateSet = true;
    }

    [RelayCommand]
    private async Task CancelAsync()
    {
        try { await Shell.Current.Navigation.PopModalAsync(); }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EditSelfViewModel>("Failed to close edit modal: {0}", new object[] { ex.Message });
        }
    }

    [RelayCommand]
    private async Task SaveAsync()
    {
        try
        {
            IsErrorVisible = false;
            ErrorText = string.Empty;

            var missing = new List<string>();
            if (string.IsNullOrWhiteSpace(Street)) missing.Add(LocalizationService.Get("Label_Street") ?? "Ulice");
            if (string.IsNullOrWhiteSpace(City)) missing.Add(LocalizationService.Get("Label_City") ?? "Město");
            if (string.IsNullOrWhiteSpace(PostalCode)) missing.Add(LocalizationService.Get("Label_PostalCode") ?? "PSČ");
            if (string.IsNullOrWhiteSpace(Region)) missing.Add(LocalizationService.Get("Label_Region") ?? "Kraj");
            if (string.IsNullOrWhiteSpace(District)) missing.Add(LocalizationService.Get("Label_District") ?? "Okres");
            if (string.IsNullOrWhiteSpace(ConscriptionNumber)) missing.Add(LocalizationService.Get("Label_ConscriptionNumber") ?? "Číslo narukování");
            if (string.IsNullOrWhiteSpace(OrientationNumber)) missing.Add(LocalizationService.Get("Label_OrientationNumber") ?? "Orient. číslo");
            if (missing.Count > 0)
            {
                ErrorText = LocalizationService.Get("Error_AddressRequired") ?? "Vyplňte prosím všechna pole adresy.";
                IsErrorVisible = true;
                return;
            }

            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorText = LocalizationService.Get("Error_MissingPersonId") ?? "ID osoby není dostupné.";
                IsErrorVisible = true;
                return;
            }

            string? natValue = null;
            if (!string.IsNullOrWhiteSpace(SelectedNationality))
            {
                if (NationalityHelper.TryGetNumericCodeForName(SelectedNationality, out var code)) natValue = code;
                else natValue = SelectedNationality;
            }

            var genderValue = SelectedGenderIndex switch { 1 => "MAN", 2 => "WOMAN", _ => "UNSPECIFIED" };

            var address = new PersonAddress(
                City,
                ConscriptionNumber,
                District,
                OrientationNumber,
                PostalCode,
                Region,
                Street);

            var request = new PersonUpdateRequest(
                Bio,
                BirthDateSet ? BirthDate : null,
                BirthDateSet,
                CstsId,
                Email,
                FirstName,
                LastName,
                NationalId,
                natValue,
                Phone,
                WdsfId,
                PrefixTitle,
                SuffixTitle,
                genderValue,
                address);

            await _peopleService.UpdatePersonAsync(_personId, request);

            try { await Shell.Current.Navigation.PopModalAsync(); }
            catch (Exception ex)
            {
                LoggerService.SafeLogWarning<EditSelfViewModel>("Failed to close edit modal after save: {0}", new object[] { ex.Message });
            }
            var title = LocalizationService.Get("Save_Success_Title") ?? "Hotovo";
            var msg = LocalizationService.Get("Save_Success_Message") ?? "Údaje byly uloženy.";
            var ok = LocalizationService.Get("Button_OK") ?? "OK";
            await _notifier.ShowAsync(title, msg, ok);
        }
        catch (Exception ex)
        {
            ErrorText = ex.Message;
            IsErrorVisible = true;
        }
    }

    private async Task LoadAsync()
    {
        IsErrorVisible = false;
        ErrorText = string.Empty;

        try
        {
            GenderOptions.Clear();
            GenderOptions.Add(LocalizationService.Get("Gender_Other") ?? (LocalizationService.Get("About_Gender_Other") ?? "Other"));
            GenderOptions.Add(LocalizationService.Get("Gender_Male") ?? (LocalizationService.Get("About_Gender_Male") ?? "Male"));
            GenderOptions.Add(LocalizationService.Get("Gender_Female") ?? (LocalizationService.Get("About_Gender_Female") ?? "Female"));
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EditSelfViewModel>("Failed to load gender options: {0}", new object[] { ex.Message });
        }

        try
        {
            NationalityOptions.Clear();
            foreach (var country in NationalityHelper.GetAllCountryNamesSorted())
            {
                NationalityOptions.Add(country);
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<EditSelfViewModel>("Failed to load nationality options: {0}", new object[] { ex.Message });
        }

        try
        {
            if (string.IsNullOrWhiteSpace(_personId))
            {
                ErrorText = LocalizationService.Get("Error_MissingPersonId") ?? "ID osoby není dostupné.";
                IsErrorVisible = true;
                return;
            }

            var person = await _peopleService.GetPersonFullAsync(_personId);
            if (person == null) return;

            FirstName = person.FirstName ?? string.Empty;
            LastName = person.LastName ?? string.Empty;
            Email = person.Email ?? string.Empty;
            Phone = person.Phone ?? string.Empty;
            Bio = person.Bio ?? string.Empty;
            WdsfId = person.WdsfId ?? string.Empty;
            CstsId = person.CstsId ?? string.Empty;
            NationalId = person.NationalIdNumber ?? string.Empty;
            PrefixTitle = person.PrefixTitle ?? string.Empty;
            SuffixTitle = person.SuffixTitle ?? string.Empty;

            var countryName = NationalityHelper.GetCountryName(person.Nationality);
            SelectedNationality = countryName ?? string.Empty;

            if (person.Address != null)
            {
                Street = person.Address.Street ?? string.Empty;
                City = person.Address.City ?? string.Empty;
                PostalCode = person.Address.PostalCode ?? string.Empty;
                Region = person.Address.Region ?? string.Empty;
                District = person.Address.District ?? string.Empty;
                ConscriptionNumber = person.Address.ConscriptionNumber ?? string.Empty;
                OrientationNumber = person.Address.OrientationNumber ?? string.Empty;
            }

            if (!string.IsNullOrWhiteSpace(person.BirthDate) && DateTime.TryParse(person.BirthDate, out var bd))
            {
                _suppressBirthDateChange = true;
                BirthDate = bd;
                _suppressBirthDateChange = false;
                BirthDateSet = true;
            }

            var gender = (person.Gender ?? string.Empty).ToUpperInvariant();
            if (gender == "MAN") SelectedGenderIndex = 1;
            else if (gender == "WOMAN") SelectedGenderIndex = 2;
            else SelectedGenderIndex = 0;
        }
        catch (Exception ex)
        {
            ErrorText = ex.Message;
            IsErrorVisible = true;
        }
    }

}
