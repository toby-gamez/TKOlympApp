using System.Text.Json.Serialization;
using TkOlympApp.Models.People;
using TkOlympApp.Services.Abstractions;

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

    public async Task<PersonDetail?> GetPersonBasicAsync(string personId, CancellationToken ct = default)
    {
        var query = "query PersonBasic($id: BigInt!) { person(id: $id) { bio birthDate cstsId email firstName prefixTitle suffixTitle gender isTrainer lastName phone wdsfId } }";
        var variables = new Dictionary<string, object> { ["id"] = ParseId(personId) };
        var data = await _graphQlClient.PostAsync<PersonDetailData>(query, variables, ct);
        return data?.Person;
    }

    public async Task<PersonDetail?> GetPersonExtrasAsync(string personId, CancellationToken ct = default)
    {
        var query = "query PersonExtras($id: BigInt!) { person(id: $id) { activeCouplesList { id man { firstName lastName } woman { firstName lastName } } cohortMembershipsList { cohort { colorRgb id name ordering isVisible } } } }";
        var variables = new Dictionary<string, object> { ["id"] = ParseId(personId) };
        var data = await _graphQlClient.PostAsync<PersonDetailData>(query, variables, ct);
        return data?.Person;
    }

    public async Task<PersonDetail?> GetPersonFullAsync(string personId, CancellationToken ct = default)
    {
        var query = "query PersonFull($id: BigInt!) { person(id: $id) { address { city conscriptionNumber district orientationNumber postalCode region street } bio birthDate cstsId email firstName prefixTitle suffixTitle gender isTrainer lastName nationalIdNumber nationality phone wdsfId cohortMembershipsList { cohort { colorRgb id name ordering isVisible } } activeCouplesList { id man { firstName lastName } woman { firstName lastName } } } }";
        var variables = new Dictionary<string, object> { ["id"] = ParseId(personId) };
        var data = await _graphQlClient.PostAsync<PersonDetailData>(query, variables, ct);
        return data?.Person;
    }

    public async Task UpdatePersonAsync(string personId, PersonUpdateRequest request, CancellationToken ct = default)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));

        var patch = new Dictionary<string, object>();

        void AddStringField(string name, string? value)
        {
            if (string.IsNullOrWhiteSpace(value)) return;
            patch[name] = value;
        }

        AddStringField("bio", request.Bio);
        AddStringField("cstsId", request.CstsId);
        AddStringField("email", request.Email);
        AddStringField("firstName", request.FirstName);
        AddStringField("lastName", request.LastName);
        AddStringField("nationalIdNumber", request.NationalIdNumber);
        AddStringField("nationality", request.Nationality);
        AddStringField("phone", request.Phone);
        AddStringField("wdsfId", request.WdsfId);
        AddStringField("prefixTitle", request.PrefixTitle);
        AddStringField("suffixTitle", request.SuffixTitle);
        AddStringField("gender", request.Gender);

        if (request.BirthDateSet && request.BirthDate.HasValue)
        {
            patch["birthDate"] = request.BirthDate.Value.ToString("yyyy-MM-dd");
        }

        if (request.Address != null)
        {
            var address = new Dictionary<string, object>();
            void AddAddressField(string name, string? value)
            {
                if (string.IsNullOrWhiteSpace(value)) return;
                address[name] = value;
            }

            AddAddressField("street", request.Address.Street);
            AddAddressField("city", request.Address.City);
            AddAddressField("postalCode", request.Address.PostalCode);
            AddAddressField("region", request.Address.Region);
            AddAddressField("district", request.Address.District);
            AddAddressField("conscriptionNumber", request.Address.ConscriptionNumber);
            AddAddressField("orientationNumber", request.Address.OrientationNumber);

            if (address.Count > 0)
            {
                patch["address"] = address;
            }
        }

        var variables = new Dictionary<string, object>
        {
            ["id"] = ParseId(personId),
            ["patch"] = patch
        };

        var query = "mutation UpdatePerson($id: BigInt!, $patch: PersonPatch!) { updatePerson(input: {id: $id, patch: $patch}) { clientMutationId } }";
        await _graphQlClient.PostAsync<UpdatePersonData>(query, variables, ct);
    }

    private sealed class PeopleData
    {
        [JsonPropertyName("people")] public People? People { get; set; }
    }

    private sealed class People
    {
        [JsonPropertyName("nodes")] public List<Person>? Nodes { get; set; }
    }

    private sealed class PersonDetailData
    {
        [JsonPropertyName("person")] public PersonDetail? Person { get; set; }
    }

    private sealed class UpdatePersonData
    {
        [JsonPropertyName("updatePerson")] public UpdatePersonPayload? UpdatePerson { get; set; }
    }

    private sealed class UpdatePersonPayload
    {
        [JsonPropertyName("clientMutationId")] public string? ClientMutationId { get; set; }
    }

    private static object ParseId(string personId)
    {
        return long.TryParse(personId, out var id) ? id : personId;
    }
}
