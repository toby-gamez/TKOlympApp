using System.Text.Json.Serialization;

namespace TkOlympApp.Models.Couples;

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
