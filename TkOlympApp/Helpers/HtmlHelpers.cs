using System;
using System.Linq;
using System.Threading.Tasks;
using System.Net;
using HtmlAgilityPack;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.ApplicationModel;
using TkOlympApp.Services;

namespace TkOlympApp.Helpers;

// Suppress nullable dereference warnings in this helper where MAUI types
// may report nullable collections to the analyzer even though they are present at runtime.
#pragma warning disable CS8602
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
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning(nameof(HtmlHelpers), "Failed to resolve primary color: {0}", new object[] { ex.Message });
        }

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
                                try { await Launcher.OpenAsync(new Uri(url)); }
                                catch (Exception ex)
                                {
                                    LoggerService.SafeLogWarning(nameof(HtmlHelpers), "Failed to open link: {0}", new object[] { ex.Message });
                                }
                            };
                            if (s.GestureRecognizers != null) s.GestureRecognizers.Add(tap);
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

        var hasImages = doc.DocumentNode.Descendants("img").Any();
        if (!hasImages)
        {
            var label = new Label { LineBreakMode = LineBreakMode.WordWrap };
            label.FormattedText = ToFormattedString(html);
            views.Add(label);
            return views;
        }

        if (string.IsNullOrWhiteSpace(doc.DocumentNode.InnerText))
        {
            foreach (var img in doc.DocumentNode.Descendants("img"))
            {
                AddImageView(img, views);
            }
            return views;
        }

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
                continue;
            }

            if (node.Name.Equals("img", StringComparison.OrdinalIgnoreCase))
            {
                AddImageView(node, views);
                continue;
            }

            var trimmed = WebUtility.HtmlDecode(HtmlEntity.DeEntitize(node.InnerText ?? string.Empty)).Trim();
            if (string.IsNullOrWhiteSpace(trimmed) && !node.Descendants("img").Any())
            {
                continue;
            }

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
                    AddImageView(child, views);
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

        return views;
    }

    private static void AddImageView(HtmlNode node, System.Collections.Generic.List<View> views)
    {
        var src = ResolveImageSource(node);
        if (string.IsNullOrWhiteSpace(src)) return;

        try
        {
            var img = new Image
            {
                Aspect = Aspect.AspectFit,
                HorizontalOptions = LayoutOptions.Fill,
                VerticalOptions = LayoutOptions.Center,
                HeightRequest = 240,
                MinimumHeightRequest = 120
            };
            if (Uri.IsWellFormedUriString(src, UriKind.Absolute))
            {
                img.Source = new UriImageSource { Uri = new Uri(src), CachingEnabled = true };
            }
            else
            {
                img.Source = src;
            }

            var srcCopy = src;
            var tap = new TapGestureRecognizer();
            tap.Tapped += async (_, __) =>
            {
                try
                {
                    ImageSource fsSource = Uri.IsWellFormedUriString(srcCopy, UriKind.Absolute)
                        ? ImageSource.FromUri(new Uri(srcCopy))
                        : img.Source;
                    var page = new TkOlympApp.Pages.FullscreenImagePage(fsSource);
                    var nav = Shell.Current?.Navigation;
                    if (nav != null) await nav.PushAsync(page);
                }
                catch (Exception ex)
                {
                    LoggerService.SafeLogWarning(nameof(HtmlHelpers), "Failed to open fullscreen image: {0}", new object[] { ex.Message });
                }
            };

            if (img.GestureRecognizers != null) img.GestureRecognizers.Add(tap);
            views.Add(img);
        }
        catch (Exception ex)
        {
            LoggerService.SafeLogWarning(nameof(HtmlHelpers), "Failed to build image view: {0}", new object[] { ex.Message });
        }
    }

    private static string? ResolveImageSource(HtmlNode node)
    {
        var src = node.GetAttributeValue("src", string.Empty);
        if (string.IsNullOrWhiteSpace(src))
        {
            src = node.GetAttributeValue("data-src", string.Empty);
        }
        if (string.IsNullOrWhiteSpace(src))
        {
            src = node.GetAttributeValue("data-original", string.Empty);
        }
        if (string.IsNullOrWhiteSpace(src))
        {
            var srcset = node.GetAttributeValue("srcset", string.Empty);
            if (!string.IsNullOrWhiteSpace(srcset))
            {
                var first = srcset.Split(',').FirstOrDefault()?.Trim();
                if (!string.IsNullOrWhiteSpace(first))
                {
                    src = first.Split(' ').FirstOrDefault() ?? string.Empty;
                }
            }
        }

        if (string.IsNullOrWhiteSpace(src)) return null;

        if (src.StartsWith("//", StringComparison.Ordinal))
        {
            src = "https:" + src;
        }

        if (!Uri.IsWellFormedUriString(src, UriKind.Absolute))
        {
            var baseUri = new Uri(AppConstants.BaseApiUrl).GetLeftPart(UriPartial.Authority);
            if (src.StartsWith("/", StringComparison.Ordinal))
            {
                src = baseUri + src;
            }
            else
            {
                src = baseUri + "/" + src;
            }
        }

        return src;
    }

    public static string BuildImagesOnlyHtml(string? html)
    {
        if (string.IsNullOrWhiteSpace(html)) return string.Empty;

        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        var firstImg = doc.DocumentNode.Descendants("img").FirstOrDefault();
        if (firstImg == null) return string.Empty;

        var src = ResolveImageSource(firstImg);
        if (string.IsNullOrWhiteSpace(src)) return string.Empty;

        var safeSrc = WebUtility.HtmlEncode(src);
        return "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0' />" +
               "<style>html,body{margin:0;padding:0;height:100%;overflow:hidden;}" +
               "img{max-width:90%;max-height:100%;height:auto;width:auto;display:block;margin:0 auto;}</style></head>" +
               "<body><img src='" + safeSrc + "' /></body></html>";
    }

    private record TextDecorationState(bool Bold = false, bool Italic = false, bool Underline = false, bool Strike = false);
}
