using System;
using Microsoft.Maui.Controls;
using TkOlympApp.ViewModels;

namespace TkOlympApp.Pages
{
    [QueryProperty(nameof(PersonId), "personId")]
    public partial class PersonPage : ContentPage
    {
        private readonly PersonViewModel _viewModel;

        public string? PersonId
        {
            get => _viewModel?.PersonId;
            set
            {
                if (_viewModel != null)
                    _viewModel.PersonId = value;
            }
        }

        public PersonPage(PersonViewModel viewModel)
        {
            _viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
            InitializeComponent();
            BindingContext = _viewModel;
        }

        protected override async void OnAppearing()
        {
            base.OnAppearing();
            await _viewModel.OnAppearingAsync();
        }

        protected override async void OnDisappearing()
        {
            await _viewModel.OnDisappearingAsync();
            base.OnDisappearing();
        }
    }
}
