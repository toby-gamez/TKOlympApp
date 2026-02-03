using System.ComponentModel;

namespace TkOlympApp.ViewModels;

public sealed class RegistrationTrainerOption : INotifyPropertyChanged
{
    private int _count;
    public string DisplayText { get; set; }
    public string Name { get; set; }
    public string? Id { get; set; }
    public int Count
    {
        get => _count;
        set
        {
            if (_count != value)
            {
                _count = value;
                OnPropertyChanged(nameof(Count));
            }
        }
    }

    public RegistrationTrainerOption(string displayText, string name, int count, string? id)
    {
        DisplayText = displayText;
        Name = name;
        _count = count;
        Id = id;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnPropertyChanged(string propertyName) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
