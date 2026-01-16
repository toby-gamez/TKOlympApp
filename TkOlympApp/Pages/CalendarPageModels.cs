using System;
using System.ComponentModel;
using TkOlympApp.Services;

namespace TkOlympApp.Pages;

/// <summary>
/// Flat model for high-performance single-CollectionView rendering in CalendarPage.
/// Each row type represents a visual line in the calendar.
/// </summary>
public abstract class EventRow { }

public sealed class WeekHeaderRow : EventRow
{
    public string WeekLabel { get; }
    public WeekHeaderRow(string weekLabel) => WeekLabel = weekLabel;
}

public sealed class DayHeaderRow : EventRow
{
    public string DayLabel { get; }
    public DateTime Date { get; }
    public DayHeaderRow(string dayLabel, DateTime date)
    {
        DayLabel = dayLabel;
        Date = date;
    }
}

public sealed class SingleEventRow : EventRow
{
    public EventService.EventInstance Instance { get; }
    public string TimeRange { get; }
    public string LocationOrTrainers { get; }
    public string EventName { get; }
    public string EventTypeLabel { get; }
    public bool IsCancelled { get; }

    public SingleEventRow(
        EventService.EventInstance instance,
        string timeRange,
        string locationOrTrainers,
        string eventName,
        string eventTypeLabel,
        bool isCancelled)
    {
        Instance = instance;
        TimeRange = timeRange;
        LocationOrTrainers = locationOrTrainers;
        EventName = eventName;
        EventTypeLabel = eventTypeLabel;
        IsCancelled = isCancelled;
    }
}

public sealed class TrainerGroupHeaderRow : EventRow
{
    public string TrainerTitle { get; }
    public TrainerGroupHeaderRow(string trainerTitle) => TrainerTitle = trainerTitle;
}

public sealed class TrainerDetailRow : EventRow, INotifyPropertyChanged
{
    public EventService.EventInstance Instance { get; }
    public string TimeRange { get; }
    public bool IsCancelled => Instance?.IsCancelled ?? false;
    public string DurationText { get; }

    private string _firstRegistrant;
    private bool _isLoaded;
    private bool _isHighlighted;

    public bool IsLoaded
    {
        get => _isLoaded;
        set
        {
            if (_isLoaded != value)
            {
                _isLoaded = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsLoaded)));
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsFree)));
            }
        }
    }

    public string FirstRegistrant
    {
        get => _firstRegistrant;
        set
        {
            if (_firstRegistrant != value)
            {
                _firstRegistrant = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FirstRegistrant)));
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsFree)));
            }
        }
    }

    public bool IsFree => IsLoaded && string.IsNullOrEmpty(FirstRegistrant);

    public bool IsHighlighted
    {
        get => _isHighlighted;
        set
        {
            if (_isHighlighted != value)
            {
                _isHighlighted = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsHighlighted)));
            }
        }
    }

    public TrainerDetailRow(EventService.EventInstance instance, string firstRegistrant, string durationText)
    {
        Instance = instance;
        var since = instance.Since;
        var until = instance.Until;
        TimeRange = (since.HasValue ? since.Value.ToString("HH:mm") : "--:--") + " - " + (until.HasValue ? until.Value.ToString("HH:mm") : "--:--");
        _firstRegistrant = firstRegistrant;
        IsLoaded = !string.IsNullOrEmpty(_firstRegistrant);
        DurationText = durationText;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
}
