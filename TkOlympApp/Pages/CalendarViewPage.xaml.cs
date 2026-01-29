using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Layouts;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

public partial class CalendarViewPage : ContentPage
{
    private bool _isLoading;
    private bool _onlyMine = true;
    private DateTime _date;
    private readonly int _startHour = 6;
    private readonly int _endHour = 22;
    private readonly double _hourHeight = 60; // pixels per hour
    private CancellationTokenSource? _timerCts;
    private BoxView? _nowLine;

    public CalendarViewPage()
    {
        InitializeComponent();
        _date = DateTime.Now.Date;
        UpdateDateLabel();
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _ = LoadEventsAsync();
        StartNowTimer();
    }

    protected override void OnDisappearing()
    {
        base.OnDisappearing();
        StopNowTimer();
    }

    private void UpdateDateLabel()
    {
        DateLabel.Text = _date.ToString("dddd, dd.MM.yyyy");
    }

    private async Task LoadEventsAsync()
    {
        if (_isLoading) return;
        _isLoading = true;
        try
        {
            var start = _date.Date;
            var end = start.AddDays(1);
            List<EventService.EventInstance> events;
            if (_onlyMine)
                events = await EventService.GetMyEventInstancesForRangeAsync(start, end);
            else
                events = await EventService.GetEventInstancesForRangeListAsync(start, end);

            RenderTimeline(events ?? new List<EventService.EventInstance>());
        }
        catch (Exception ex)
        {
            try { await DisplayAlert(LocalizationService.Get("Error_Loading_Title") ?? "Chyba", ex.Message, LocalizationService.Get("Button_OK") ?? "OK"); } catch { }
        }
        finally
        {
            _isLoading = false;
        }
    }

    private void RenderTimeline(List<EventService.EventInstance> events)
    {
        TimelineLayout.Children.Clear();
        TimeLabelsStack.Children.Clear();

        var totalHours = Math.Max(1, _endHour - _startHour);
        var totalHeight = totalHours * _hourHeight;

        for (int h = _startHour; h <= _endHour; h++)
        {
            var y = (h - _startHour) * _hourHeight;
            var label = new Label { Text = h.ToString("D2") + ":00", FontSize = 12, HeightRequest = _hourHeight, VerticalTextAlignment = TextAlignment.Start };
            TimeLabelsStack.Children.Add(label);

            var line = new BoxView { HeightRequest = 1, BackgroundColor = Colors.LightGray };
            AbsoluteLayout.SetLayoutBounds(line, new Rect(0, y, 1, 1));
            AbsoluteLayout.SetLayoutFlags(line, AbsoluteLayoutFlags.WidthProportional);
            TimelineLayout.Children.Add(line);
        }

        foreach (var inst in events.OrderBy(e => e.Since))
        {
            var since = inst.Since ?? inst.UpdatedAt;
            var until = inst.Until ?? since.AddMinutes(30);
            var startMinutes = (since.Hour * 60 + since.Minute) - (_startHour * 60);
            var durationMinutes = Math.Max(15, (int)(until - since).TotalMinutes);

            startMinutes = Math.Max(0, startMinutes);
            var top = startMinutes / 60.0 * _hourHeight;
            var height = durationMinutes / 60.0 * _hourHeight;
            if (top + height < 0 || top > totalHeight) continue;

            var frame = new Frame
            {
                CornerRadius = 6,
                Padding = new Thickness(6),
                HasShadow = false,
                BackgroundColor = Colors.LightBlue,
                BindingContext = inst
            };
            string titleText = inst.Event?.Name ?? string.Empty;
            if (string.Equals(inst.Event?.Type, "lesson", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var first = TkOlympApp.MainPage.GroupedEventRow.ComputeFirstRegistrantPublic(inst);
                    var left = !string.IsNullOrEmpty(first) ? first : inst.Event?.Name ?? LocalizationService.Get("Lesson_Short") ?? "Lekce";
                    var trainerFull = inst.Event?.EventTrainersList?.FirstOrDefault()?.Name;
                    if (!string.IsNullOrWhiteSpace(trainerFull))
                    {
                        var trainerShort = FormatTrainerShort(trainerFull);
                        titleText = left + ": " + trainerShort;
                    }
                    else
                    {
                        titleText = left;
                    }
                }
                catch { titleText = inst.Event?.Name ?? LocalizationService.Get("Lesson_Short") ?? "Lekce"; }
            }
            var title = new Label { Text = titleText, FontAttributes = FontAttributes.Bold, FontSize = 12 };
            var time = new Label { Text = ((inst.Since.HasValue ? inst.Since.Value.ToString("HH:mm") : "--:--") + " – " + (inst.Until.HasValue ? inst.Until.Value.ToString("HH:mm") : "--:--")), FontSize = 11, TextColor = Colors.Gray };
            var stack = new VerticalStackLayout { Spacing = 2 };
            stack.Add(title);
            stack.Add(time);
            frame.Content = stack;

            var tap = new TapGestureRecognizer();
            tap.Tapped += async (s, e) => {
                if (frame.BindingContext is EventService.EventInstance ev && ev.Event?.Id is long id)
                {
                    var page = new EventPage();
                    if (id != 0) page.EventId = id;
                    if (ev.Id > 0) page.EventInstanceId = ev.Id;
                    await Navigation.PushAsync(page);
                }
            };
            frame.GestureRecognizers.Add(tap);

            AbsoluteLayout.SetLayoutBounds(frame, new Rect(0.02, top, 0.96, Math.Max(20, height)));
            AbsoluteLayout.SetLayoutFlags(frame, AbsoluteLayoutFlags.WidthProportional);
            TimelineLayout.Children.Add(frame);
        }

        _nowLine = new BoxView { HeightRequest = 2, BackgroundColor = Colors.Red, Opacity = 0.9 };
        TimelineLayout.Children.Add(_nowLine);
        UpdateNowLinePosition();
        TimelineLayout.HeightRequest = totalHeight;
    }

    private static string FormatTrainerShort(string? fullName)
    {
        if (string.IsNullOrWhiteSpace(fullName)) return string.Empty;
        var parts = fullName.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length == 1) return parts[0];
        var surname = parts[^1];
        var given = parts[0];
        var initial = !string.IsNullOrEmpty(given) ? given[0].ToString() : string.Empty;
        return (!string.IsNullOrEmpty(initial) ? initial + ". " : string.Empty) + surname;
    }

    private void UpdateNowLinePosition()
    {
        if (_nowLine == null) return;
        var now = DateTime.Now;
        var minutes = (now.Hour * 60 + now.Minute) - (_startHour * 60) + now.Second / 60.0;
        var top = minutes / 60.0 * _hourHeight;
        var totalHours = Math.Max(1, _endHour - _startHour);
        var totalHeight = totalHours * _hourHeight;
        if (top < 0 || top > totalHeight) { _nowLine.IsVisible = false; return; }
        _nowLine.IsVisible = true;
        AbsoluteLayout.SetLayoutBounds(_nowLine, new Rect(0, top, 1, 2));
        AbsoluteLayout.SetLayoutFlags(_nowLine, AbsoluteLayoutFlags.WidthProportional);
    }

    private void StartNowTimer()
    {
        StopNowTimer();
        _timerCts = new CancellationTokenSource();
        var ct = _timerCts.Token;
        _ = Task.Run(async () => {
            while (!ct.IsCancellationRequested)
            {
                try { MainThread.BeginInvokeOnMainThread(UpdateNowLinePosition); } catch { }
                try { await Task.Delay(TimeSpan.FromSeconds(30), ct); } catch (TaskCanceledException) { break; }
            }
        }, ct);
    }

    private void StopNowTimer()
    {
        try { _timerCts?.Cancel(); _timerCts = null; } catch { }
    }

    private void OnPrevDayClicked(object? sender, EventArgs e)
    {
        _date = _date.AddDays(-1);
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnNextDayClicked(object? sender, EventArgs e)
    {
        _date = _date.AddDays(1);
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnTodayClicked(object? sender, EventArgs e)
    {
        _date = DateTime.Now.Date;
        UpdateDateLabel();
        _ = LoadEventsAsync();
    }

    private void OnTabMineClicked(object? sender, EventArgs e)
    {
        _onlyMine = true;
        _ = LoadEventsAsync();
    }

    private void OnTabAllClicked(object? sender, EventArgs e)
    {
        _onlyMine = false;
        _ = LoadEventsAsync();
    }
}

