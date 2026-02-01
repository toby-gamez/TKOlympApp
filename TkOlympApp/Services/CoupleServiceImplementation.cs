using System.Text.Json.Serialization;
using TkOlympApp.Models.Couples;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public sealed class CoupleServiceImplementation : ICoupleService
{
    private readonly IGraphQlClient _graphQlClient;

    public CoupleServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
    }

    public async Task<CoupleRecord?> GetCoupleAsync(string id, CancellationToken ct = default)
    {
        var query =
            "query MyQuery($id: BigInt!) { couple(id: $id) { createdAt id man { id firstName lastName phone } woman { id firstName lastName phone } } }";
        var variables = new Dictionary<string, object> { { "id", id } };

        var data = await _graphQlClient.PostAsync<CoupleData>(query, variables, ct);
        return data?.Couple;
    }

    private sealed class CoupleData
    {
        [JsonPropertyName("couple")] public CoupleRecord? Couple { get; set; }
    }
}
