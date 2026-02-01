using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Maui.Controls;
using Microsoft.Extensions.DependencyInjection;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public static class CoupleService
{
    public static async Task<CoupleRecord?> GetCoupleAsync(string id, CancellationToken ct = default)
    {
        return await Instance.GetCoupleAsync(id, ct);
    }

    private static ICoupleService? _instance;

    private static ICoupleService Instance
    {
        get
        {
            if (_instance != null) return _instance;
            var services = Application.Current?.Handler?.MauiContext?.Services;
            if (services == null)
                throw new InvalidOperationException("MauiContext.Services not available. Ensure Application is initialized.");
            _instance = services.GetRequiredService<ICoupleService>();
            return _instance;
        }
    }

    internal static void SetInstance(ICoupleService instance)
    {
        _instance = instance ?? throw new ArgumentNullException(nameof(instance));
    }

    

    private sealed class CoupleData
    {
        [JsonPropertyName("couple")] public CoupleRecord? Couple { get; set; }
    }

    public sealed class CoupleRecord
    {
        [JsonPropertyName("createdAt")] public string? CreatedAt { get; set; }
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("man")] public Person? Man { get; set; }
        [JsonPropertyName("woman")] public Person? Woman { get; set; }
    }

    public sealed class Person
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        [JsonPropertyName("firstName")] public string? FirstName { get; set; }
        [JsonPropertyName("lastName")] public string? LastName { get; set; }
        [JsonPropertyName("phone")] public string? Phone { get; set; }
    }
}
