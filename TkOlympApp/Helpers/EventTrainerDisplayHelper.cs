using TkOlympApp.Models.Events;

namespace TkOlympApp.Helpers;

public static class EventTrainerDisplayHelper
{
    public static string GetTrainerDisplayName(EventTrainer? trainer)
    {
        if (trainer == null) return string.Empty;
        try
        {
            if (trainer.Person != null)
            {
                var firstName = trainer.Person.FirstName?.Trim();
                var lastName = trainer.Person.LastName?.Trim();
                var combined = string.Join(' ', new[] { firstName, lastName }.Where(s => !string.IsNullOrWhiteSpace(s)));
                if (!string.IsNullOrWhiteSpace(combined)) return combined;
            }

            if (!string.IsNullOrWhiteSpace(trainer.FirstName) || !string.IsNullOrWhiteSpace(trainer.LastName))
            {
                var combined = string.Join(' ', new[] { trainer.FirstName?.Trim(), trainer.LastName?.Trim() }.Where(s => !string.IsNullOrWhiteSpace(s)));
                if (!string.IsNullOrWhiteSpace(combined)) return combined;
            }

            if (!string.IsNullOrWhiteSpace(trainer.Name)) return trainer.Name.Trim();
        }
        catch
        {
            // ignore
        }

        return string.Empty;
    }

    public static string GetTrainerDisplayWithPrefix(EventTrainer? trainer)
    {
        if (trainer == null) return string.Empty;
        try
        {
            var title = trainer.Person?.PrefixTitle ?? trainer.PrefixTitle;
            var name = GetTrainerDisplayName(trainer);
            if (string.IsNullOrWhiteSpace(name)) return string.Empty;
            if (!string.IsNullOrWhiteSpace(title)) return (title.Trim() + " " + name).Trim();
            return name;
        }
        catch
        {
            return GetTrainerDisplayName(trainer);
        }
    }
}
