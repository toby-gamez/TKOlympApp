using System.Xml.Linq;

namespace TkOlympApp.Tests.Resources;

public class LocalizationResxConsistencyTests
{
    [Fact]
    public void All_Strings_resx_files_have_same_keys()
    {
        var repoRoot = FindRepoRoot();
        var resourcesDir = Path.Combine(repoRoot, "TkOlympApp", "Resources");

        Directory.Exists(resourcesDir).Should().BeTrue($"Expected Resources directory at '{resourcesDir}'.");

        var resxFiles = Directory
            .GetFiles(resourcesDir, "Strings*.resx", SearchOption.TopDirectoryOnly)
            .OrderBy(Path.GetFileName, StringComparer.Ordinal)
            .ToArray();

        resxFiles.Length.Should().BeGreaterThan(0, $"Expected at least one Strings*.resx file in '{resourcesDir}'.");

        var keysByFile = resxFiles
            .ToDictionary(
                file => Path.GetFileName(file),
                file => ReadResxKeys(file),
                StringComparer.Ordinal);

        const string baselineFileName = "Strings.resx";
        keysByFile.Should().ContainKey(baselineFileName, "Baseline resource file should be present.");

        var baselineKeys = keysByFile[baselineFileName];

        var problems = new List<string>();

        foreach (var (fileName, keys) in keysByFile.OrderBy(kvp => kvp.Key, StringComparer.Ordinal))
        {
            if (string.Equals(fileName, baselineFileName, StringComparison.Ordinal))
            {
                continue;
            }

            var missing = baselineKeys.Except(keys).OrderBy(k => k, StringComparer.Ordinal).ToArray();
            var extra = keys.Except(baselineKeys).OrderBy(k => k, StringComparer.Ordinal).ToArray();

            if (missing.Length > 0)
            {
                problems.Add($"{fileName}: missing {missing.Length} key(s): {string.Join(", ", missing)}");
            }

            if (extra.Length > 0)
            {
                problems.Add($"{fileName}: extra {extra.Length} key(s): {string.Join(", ", extra)}");
            }
        }

        problems.Should().BeEmpty(
            "All localized Strings*.resx files must contain exactly the same set of keys as Strings.resx.\n{0}",
            string.Join("\n", problems));
    }

    private static HashSet<string> ReadResxKeys(string filePath)
    {
        var doc = XDocument.Load(filePath);
        var dataElements = doc.Root?
            .Elements()
            .Where(e => string.Equals(e.Name.LocalName, "data", StringComparison.Ordinal))
            .ToArray() ?? Array.Empty<XElement>();

        var names = dataElements
            .Select(e => (string?)e.Attribute("name"))
            .Where(n => !string.IsNullOrWhiteSpace(n))
            .Select(n => n!)
            .ToList();

        var duplicates = names
            .GroupBy(n => n, StringComparer.Ordinal)
            .Where(g => g.Count() > 1)
            .Select(g => g.Key)
            .OrderBy(k => k, StringComparer.Ordinal)
            .ToArray();

        duplicates.Should().BeEmpty($"Duplicate keys found in '{Path.GetFileName(filePath)}': {string.Join(", ", duplicates)}");

        return names.ToHashSet(StringComparer.Ordinal);
    }

    private static string FindRepoRoot()
    {
        // We climb up from the test output directory until we find TkOlympApp.sln.
        var dir = new DirectoryInfo(AppContext.BaseDirectory);

        while (dir is not null)
        {
            var slnPath = Path.Combine(dir.FullName, "TkOlympApp.sln");
            if (File.Exists(slnPath))
            {
                return dir.FullName;
            }

            dir = dir.Parent;
        }

        throw new DirectoryNotFoundException(
            $"Could not locate repository root. Started search from '{AppContext.BaseDirectory}'.");
    }
}
