using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public static class CoupleService
{
    public static async Task<CoupleRecord?> GetCoupleAsync(string id, CancellationToken ct = default)
    {
        var query = "query MyQuery($id: BigInt!) { couple(id: $id) { createdAt id man { id firstName lastName phone } woman { id firstName lastName phone } } }";
        var variables = new Dictionary<string, object> { { "id", id } };

        var data = await GraphQlClient.PostAsync<CoupleData>(query, variables, ct);
        return data?.Couple;
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
