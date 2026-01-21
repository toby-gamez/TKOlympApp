using System;
using Microsoft.Maui.Controls;
using System.Globalization;
using Microsoft.Maui.Controls.Xaml;

namespace TkOlympApp.Services
{
    [ContentProperty("Text")]
    [AcceptEmptyServiceProvider]
    public class TranslateExtension : IMarkupExtension
    {
        public string? Text { get; set; }

        public object ProvideValue(IServiceProvider serviceProvider)
        {
            if (string.IsNullOrEmpty(Text)) return string.Empty;
            try
            {
                return LocalizationService.Get(Text) ?? Text;
            }
            catch
            {
                return Text;
            }
        }
    }
}
