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

    // Convert HTML into a sequence of Views (Labels and Images) suitable for adding into a layout.
    public static System.Collections.Generic.List<View> ToViews(string? html)
    {
        var views = new System.Collections.Generic.List<View>();
        if (string.IsNullOrWhiteSpace(html)) return views;

        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        // iterate top-level nodes under document
        foreach (var node in doc.DocumentNode.ChildNodes)
        {
            if (node.NodeType == HtmlNodeType.Text)
            {
                var txt = WebUtility.HtmlDecode(node.InnerText).Trim();
                if (!string.IsNullOrWhiteSpace(txt))
                {
                    var lbl = new Label { LineBreakMode = LineBreakMode.WordWrap };
                    lbl.FormattedText = ToFormattedString(txt);
                    views.Add(lbl);
                }
            }
            else if (node.Name.Equals("img", StringComparison.OrdinalIgnoreCase))
            {
                var src = node.GetAttributeValue("src", string.Empty);
                if (!string.IsNullOrWhiteSpace(src))
                {
                    try
                    {
                        var img = new Image { Aspect = Aspect.AspectFit, HorizontalOptions = LayoutOptions.Fill, VerticalOptions = LayoutOptions.Center };
                        if (Uri.IsWellFormedUriString(src, UriKind.Absolute)) img.Source = ImageSource.FromUri(new Uri(src));
                        else img.Source = src; // try as resource or relative
                        // allow image to size naturally / fill available width
                        var srcCopy = src;
                        var tap = new TapGestureRecognizer();
                        tap.Tapped += async (_, __) =>
                        {
                            try
                            {
                                ImageSource fsSource;
                                if (Uri.IsWellFormedUriString(srcCopy, UriKind.Absolute)) fsSource = ImageSource.FromUri(new Uri(srcCopy));
                                else fsSource = img.Source;
                                var page = new TkOlympApp.Pages.FullscreenImagePage(fsSource);
                                var nav = Shell.Current?.Navigation;
                                if (nav != null) await nav.PushAsync(page);
                            }
                            catch { }
                        };
                        img.GestureRecognizers.Add(tap);
                        views.Add(img);
                    }
                    catch { }
                }
            }
            else
            {
                // For other elements, render their inner HTML as a labeled formatted string
                var inner = node.InnerHtml;
                var trimmed = WebUtility.HtmlDecode(HtmlEntity.DeEntitize(node.InnerText ?? string.Empty)).Trim();
                if (!string.IsNullOrWhiteSpace(trimmed) || node.Descendants("img").Any())
                {
                    // If node contains img children, render text parts and images sequentially
                    foreach (var child in node.ChildNodes)
                    {
                        if (child.NodeType == HtmlNodeType.Text)
                        {
                            var t = WebUtility.HtmlDecode(child.InnerText).Trim();
                            if (!string.IsNullOrWhiteSpace(t))
                            {
                                var l = new Label { LineBreakMode = LineBreakMode.WordWrap };
                                l.FormattedText = ToFormattedString(t);
                                views.Add(l);
                            }
                        }
                        else if (child.Name.Equals("img", StringComparison.OrdinalIgnoreCase))
                        {
                            var s = child.GetAttributeValue("src", string.Empty);
                            if (!string.IsNullOrWhiteSpace(s))
                            {
                                try
                                {
                                    var img = new Image { Aspect = Aspect.AspectFit, HorizontalOptions = LayoutOptions.Fill, VerticalOptions = LayoutOptions.Center };
                                    if (Uri.IsWellFormedUriString(s, UriKind.Absolute)) img.Source = ImageSource.FromUri(new Uri(s));
                                    else img.Source = s;
                                    var sCopy = s;
                                    var tap = new TapGestureRecognizer();
                                    tap.Tapped += async (_, __) =>
                                    {
                                        try
                                        {
                                            ImageSource fsSource;
                                            if (Uri.IsWellFormedUriString(sCopy, UriKind.Absolute)) fsSource = ImageSource.FromUri(new Uri(sCopy));
                                            else fsSource = img.Source;
                                            var page = new TkOlympApp.Pages.FullscreenImagePage(fsSource);
                                            var nav = Shell.Current?.Navigation;
                                            if (nav != null) await nav.PushAsync(page);
                                        }
                                        catch { }
                                    };
                                    img.GestureRecognizers.Add(tap);
                                    views.Add(img);
                                }
                                catch { }
                            }
                        }
                        else
                        {
                            var t2 = WebUtility.HtmlDecode(child.InnerText ?? string.Empty).Trim();
                            if (!string.IsNullOrWhiteSpace(t2))
                            {
                                var l2 = new Label { LineBreakMode = LineBreakMode.WordWrap };
                                l2.FormattedText = ToFormattedString(t2);
                                views.Add(l2);
                            }
                        }
                    }
                }
            }
        }

        return views;
    }

    private record TextDecorationState(bool Bold = false, bool Italic = false, bool Underline = false, bool Strike = false);
}
