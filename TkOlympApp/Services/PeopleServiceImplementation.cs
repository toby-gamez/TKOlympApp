using System.Text.Json.Serialization;
using TkOlympApp.Services.Abstractions;
using static TkOlympApp.Services.PeopleService;

namespace TkOlympApp.Services;

public sealed class PeopleServiceImplementation : IPeopleService
{
    private readonly IGraphQlClient _graphQlClient;

    public PeopleServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
    }

    public async Task<List<Person>> GetPeopleAsync(CancellationToken ct = default)
    {
        var query = @"query MyQuery {
    people {
        nodes {
            id
            firstName
            lastName
            birthDate
            cohortMembershipsList {
                cohort {
                    name
                    id
                    colorRgb
                    isVisible
                }
            }
        }
    }
}";

        var data = await _graphQlClient.PostAsync<PeopleData>(query, null, ct);
        return data?.People?.Nodes ?? new List<Person>();
    }

    private sealed class PeopleData
    {
        [JsonPropertyName("people")] public People? People { get; set; }
    }

    private sealed class People
    {
        [JsonPropertyName("nodes")] public List<Person>? Nodes { get; set; }
    }
}
