using System;
using System.Linq;
using System.Threading.Tasks;
using System.Net;
using HtmlAgilityPack;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.ApplicationModel;

namespace TkOlympApp.Helpers;

public static class HtmlHelpers
{
    // Convert HTML to a FormattedString with basic styling: <b>/<strong>, <i>/<em>, <u>, <del>, <a>, <br>, <p>, <li>
    public static FormattedString ToFormattedString(string? html)
    {
        var fs = new FormattedString();
        if (string.IsNullOrWhiteSpace(html)) return fs;

        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        // Resolve primary color from resources if available
        Color? primaryColor = null;
        try
        {
            if (Application.Current?.Resources != null && Application.Current.Resources.ContainsKey("Primary"))
            {
                var res = Application.Current.Resources["Primary"];
                if (res is Color c) primaryColor = c;
                else if (res is SolidColorBrush b) primaryColor = b.Color;
            }
        }
        catch { }

        void Walk(HtmlNode node, TextDecorationState state)
        {
            foreach (var child in node.ChildNodes)
            {
                if (child.NodeType == HtmlNodeType.Text)
                {
                    var text = WebUtility.HtmlDecode(child.InnerText);
                    if (string.IsNullOrWhiteSpace(text)) continue;
                    var span = new Span { Text = text };
                    ApplyState(span, state);
                    fs.Spans.Add(span);
                }
                else if (child.Name.Equals("br", StringComparison.OrdinalIgnoreCase))
                {
                    fs.Spans.Add(new Span { Text = "\n" });
                }
                else if (child.Name.Equals("p", StringComparison.OrdinalIgnoreCase))
                {
                    Walk(child, state);
                    fs.Spans.Add(new Span { Text = "\n\n" });
                }
                else if (child.Name.Equals("li", StringComparison.OrdinalIgnoreCase))
                {
                    var span = new Span { Text = "• " };
                    ApplyState(span, state);
                    fs.Spans.Add(span);
                    Walk(child, state);
                    fs.Spans.Add(new Span { Text = "\n" });
                }
                else if (child.Name.Equals("strong", StringComparison.OrdinalIgnoreCase) || child.Name.Equals("b", StringComparison.OrdinalIgnoreCase))
                {
                    Walk(child, state with { Bold = true });
                }
                else if (child.Name.Equals("em", StringComparison.OrdinalIgnoreCase) || child.Name.Equals("i", StringComparison.OrdinalIgnoreCase))
                {
                    Walk(child, state with { Italic = true });
                }
                else if (child.Name.Equals("u", StringComparison.OrdinalIgnoreCase))
                {
                    Walk(child, state with { Underline = true });
                }
                else if (child.Name.Equals("del", StringComparison.OrdinalIgnoreCase) || child.Name.Equals("s", StringComparison.OrdinalIgnoreCase))
                {
                    Walk(child, state with { Strike = true });
                }
                else if (child.Name.Equals("a", StringComparison.OrdinalIgnoreCase))
                {
                    var href = child.GetAttributeValue("href", string.Empty);
                    // Create a span for link text with underline and blue color and tap to open
                    var linkState = state with { Underline = true };
                    var beforeCount = fs.Spans.Count;
                    Walk(child, linkState);
                    // Attach gesture recognizer to spans added for this link
                    if (!string.IsNullOrWhiteSpace(href))
                    {
                        for (int i = beforeCount; i < fs.Spans.Count; i++)
                        {
                            var s = fs.Spans[i];
                            // Apply primary color if available, otherwise leave default
                            if (primaryColor is Color pc) s.TextColor = pc;
                            // Make links bold
                            s.FontAttributes = s.FontAttributes | FontAttributes.Bold;
                            var tap = new TapGestureRecognizer();
                            var url = href;
                            tap.Tapped += async (_, __) =>
                            {
                                try { await Launcher.OpenAsync(new Uri(url)); } catch { }
                            };
                            s.GestureRecognizers.Add(tap);
                        }
                    }
                }
                else
                {
                    // Generic element: recurse preserving state
                    Walk(child, state);
                }
            }
        }

        static void ApplyState(Span span, TextDecorationState s)
        {
            if (s.Bold) span.FontAttributes = FontAttributes.Bold;
            if (s.Italic) span.FontAttributes = span.FontAttributes | FontAttributes.Italic;
            if (s.Underline) span.TextDecorations = TextDecorations.Underline;
            if (s.Strike) span.TextDecorations = span.TextDecorations | TextDecorations.Strikethrough;
        }

        Walk(doc.DocumentNode, new TextDecorationState());
        return fs;
    }

    private record TextDecorationState(bool Bold = false, bool Italic = false, bool Underline = false, bool Strike = false);
}
