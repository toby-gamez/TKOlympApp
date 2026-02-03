using System.ComponentModel;

namespace TkOlympApp.ViewModels;

public sealed class EditRegistrationsTrainerOption : INotifyPropertyChanged
{
    private int _count;
    public string Name { get; set; }
    public string? Id { get; set; }
    public int OriginalCount { get; set; }
    public int Count
    {
        get => _count;
        set
        {
            if (_count != value)
            {
                _count = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Count)));
            }
        }
    }

    public EditRegistrationsTrainerOption(string name, int count, string? id)
    {
        Name = name;
        _count = count;
        OriginalCount = count;
        Id = id;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
}
