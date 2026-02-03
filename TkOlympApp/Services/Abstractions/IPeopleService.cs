using TkOlympApp.Models.People;

namespace TkOlympApp.Services.Abstractions;

public interface IPeopleService
{
    Task<List<Person>> GetPeopleAsync(CancellationToken ct = default);

    Task<PersonDetail?> GetPersonBasicAsync(string personId, CancellationToken ct = default);

    Task<PersonDetail?> GetPersonExtrasAsync(string personId, CancellationToken ct = default);

    Task<PersonDetail?> GetPersonFullAsync(string personId, CancellationToken ct = default);

    Task UpdatePersonAsync(string personId, PersonUpdateRequest request, CancellationToken ct = default);
}

