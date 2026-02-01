namespace TkOlympApp.Helpers;

/// <summary>
/// Application-wide constants for configuration, API endpoints, storage keys.
/// Prefer using these constants instead of hardcoded strings throughout the codebase.
/// </summary>
public static class AppConstants
{
    // API Configuration
    public const string BaseApiUrl = "https://api.rozpisovnik.cz/graphql";
    public const string TenantHeader = "x-tenant-id";
    public const string TenantId = "1";

    // Secure Storage Keys
    public const string JwtStorageKey = "jwt";
    public const string RefreshTokenStorageKey = "refresh_token";

    // Preferences Keys
    public const string AppLanguageKey = "app_language";
    public const string FirstRunSeenKey = "first_run_seen";

    // HTTP Timeouts (seconds)
    public const int DefaultTimeoutSeconds = 30;
    public const int AuthTimeoutSeconds = 15;

    // Polling Intervals
    public const int AnnouncementPollMinutes = 5;
    public const int EventCheckMinutes = 15;

    // Retry Configuration
    public const int MaxRetryAttempts = 3;
    public const int InitialRetryDelaySeconds = 2;
}
