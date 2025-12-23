using System;
using Microsoft.Maui.Controls;
using System.Globalization;

namespace TkOlympApp.Services
{
    [ContentProperty("Text")]
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
