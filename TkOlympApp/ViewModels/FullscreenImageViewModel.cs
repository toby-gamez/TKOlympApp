using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.Maui.Controls;

namespace TkOlympApp.ViewModels;

public partial class FullscreenImageViewModel : ViewModelBase
{
    [ObservableProperty]
    private ImageSource? _source;

    public FullscreenImageViewModel(ImageSource source)
    {
        _source = source;
    }
}
