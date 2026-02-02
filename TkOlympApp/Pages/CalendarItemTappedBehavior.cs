using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;
using TkOlympApp.Pages;

namespace TkOlympApp.Pages;

/// <summary>
/// Behavior to handle tap events in CalendarPage CollectionView.
/// Routes taps to appropriate ViewModel commands based on row type.
/// </summary>
public class CalendarItemTappedBehavior : Behavior<CollectionView>
{
    private CollectionView? _collectionView;

    protected override void OnAttachedTo(CollectionView bindable)
    {
        base.OnAttachedTo(bindable);
        _collectionView = bindable;
        
        if (_collectionView != null)
        {
            // Use SelectionChanged as a workaround since CollectionView doesn't support ItemTapped
            _collectionView.SelectionMode = SelectionMode.Single;
            _collectionView.SelectionChanged += OnSelectionChanged;
        }
    }

    protected override void OnDetachingFrom(CollectionView bindable)
    {
        if (_collectionView != null)
        {
            _collectionView.SelectionChanged -= OnSelectionChanged;
        }
        
        _collectionView = null;
        base.OnDetachingFrom(bindable);
    }

    private void OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (sender is not CollectionView cv) return;
        
        // Clear selection immediately to allow re-tapping same item
        cv.SelectedItem = null;
        
        if (e.CurrentSelection?.Count > 0)
        {
            var item = e.CurrentSelection[0];
            var viewModel = cv.BindingContext as CalendarViewModel;
            
            if (viewModel == null) return;
            
            // Route to appropriate command based on type
            switch (item)
            {
                case SingleEventRow singleEvent:
                    viewModel.NavigateToEventCommand?.Execute(singleEvent.Instance);
                    break;
                    
                case TrainerDetailRow trainerDetail:
                    viewModel.NavigateToEventFromTrainerRowCommand?.Execute(trainerDetail);
                    break;
                
                // Week headers, day headers, and trainer group headers are not tappable
                case WeekHeaderRow:
                case DayHeaderRow:
                case TrainerGroupHeaderRow:
                default:
                    break;
            }
        }
    }
}
