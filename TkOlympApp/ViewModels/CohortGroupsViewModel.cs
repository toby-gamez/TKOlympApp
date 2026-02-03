using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using TkOlympApp.Models.Cohorts;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.ViewModels;

public partial class CohortGroupsViewModel : ViewModelBase
{
    private readonly ICohortService _cohortService;
    private readonly IUserNotifier _notifier;
    private bool _loaded;

    public ObservableCollection<CohortGroupItem> Groups { get; } = new();

    [ObservableProperty]
    private bool _isRefreshing;

    public CohortGroupsViewModel(ICohortService cohortService, IUserNotifier notifier)
    {
        _cohortService = cohortService ?? throw new ArgumentNullException(nameof(cohortService));
        _notifier = notifier ?? throw new ArgumentNullException(nameof(notifier));
    }

    public override async Task OnAppearingAsync()
    {
        await base.OnAppearingAsync();
        if (!_loaded)
        {
            _loaded = true;
            await LoadAsync();
        }
    }

    [RelayCommand]
    private async Task RefreshAsync()
    {
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            IsRefreshing = true;
            var groups = await _cohortService.GetCohortGroupsAsync();
            var cohorts = groups
                .SelectMany(g => g.CohortsList ?? new System.Collections.Generic.List<CohortItem>())
                .Where(ci => ci != null && !string.IsNullOrWhiteSpace(ci.Name))
                .ToList();

            Groups.Clear();
            foreach (var c in cohorts)
            {
                var formatted = string.IsNullOrWhiteSpace(c.Description)
                    ? null
                    : TkOlympApp.Helpers.HtmlHelpers.ToFormattedString(c.Description);

                Groups.Add(new CohortGroupItem(
                    c.Name ?? string.Empty,
                    formatted,
                    TryParseColorBrush(c.ColorRgb)));
            }

            // Add the manual "Základní členství" group
            try
            {
                var manualHtml = "<p>Pro členy bez příslušnosti ke skupině</p><ul><li>přístup na sály pro volný trénink</li><li>Středa 18:00 Practise, Hýža, SGO</li></ul>";
                var manualFormatted = TkOlympApp.Helpers.HtmlHelpers.ToFormattedString(manualHtml);
                Groups.Add(new CohortGroupItem("Základní členství", manualFormatted, TryParseColorBrush("#ffffff")));
            }
            catch
            {
                LoggerService.SafeLogWarning<CohortGroupsViewModel>("Failed to add manual cohort group", Array.Empty<object>());
            }
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<CohortGroupsViewModel>("Failed to load cohort groups: {0}", new object[] { ex.Message });
            try
            {
                await _notifier.ShowAsync(
                    LocalizationService.Get("Error_Title") ?? "Error",
                    ex.Message,
                    LocalizationService.Get("Button_OK") ?? "OK");
            }
            catch (Exception notifyEx)
            {
                LoggerService.SafeLogWarning<CohortGroupsViewModel>("Failed to show error: {0}", new object[] { notifyEx.Message });
            }
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private static Brush? TryParseColorBrush(string? colorRgb)
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
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning<CohortGroupsViewModel>("Failed to parse cohort color: {0}", new object[] { ex.Message });
        }
        return null;
    }

    public sealed class CohortGroupItem
    {
        public CohortGroupItem(string name, FormattedString? descriptionFormatted, Brush? colorBrush)
        {
            Name = name;
            DescriptionFormatted = descriptionFormatted;
            ColorBrush = colorBrush;
        }

        public string Name { get; }
        public FormattedString? DescriptionFormatted { get; }
        public bool HasDescription => DescriptionFormatted != null && DescriptionFormatted.Spans.Count > 0;
        public Brush? ColorBrush { get; }
        public bool HasColor => ColorBrush != null;
    }
}
