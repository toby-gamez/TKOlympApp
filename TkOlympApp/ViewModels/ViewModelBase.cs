using CommunityToolkit.Mvvm.ComponentModel;

namespace TkOlympApp.ViewModels;

/// <summary>
/// Base class for all ViewModels in the application.
/// Inherits from ObservableObject to provide INotifyPropertyChanged implementation.
/// </summary>
public abstract partial class ViewModelBase : ObservableObject
{
    private bool _isBusy;
    private string? _title;

    /// <summary>
    /// Indicates whether the ViewModel is currently performing a long-running operation.
    /// </summary>
    public bool IsBusy
    {
        get => _isBusy;
        set
        {
            if (SetProperty(ref _isBusy, value))
            {
                OnPropertyChanged(nameof(IsNotBusy));
            }
        }
    }

    /// <summary>
    /// Inverse of IsBusy, useful for binding to UI elements that should be enabled when not busy.
    /// </summary>
    public bool IsNotBusy => !IsBusy;

    /// <summary>
    /// Optional title for the ViewModel, typically displayed in the UI.
    /// </summary>
    public string? Title
    {
        get => _title;
        set => SetProperty(ref _title, value);
    }

    /// <summary>
    /// Called when the ViewModel is first created and initialized.
    /// Override in derived classes to perform initialization logic.
    /// </summary>
    public virtual Task InitializeAsync()
    {
        return Task.CompletedTask;
    }

    /// <summary>
    /// Called when the ViewModel's view appears on screen.
    /// Override in derived classes to refresh data or perform actions when the view appears.
    /// </summary>
    public virtual Task OnAppearingAsync()
    {
        return Task.CompletedTask;
    }

    /// <summary>
    /// Called when the ViewModel's view disappears from screen.
    /// Override in derived classes to clean up resources or cancel operations.
    /// </summary>
    public virtual Task OnDisappearingAsync()
    {
        return Task.CompletedTask;
    }
}
