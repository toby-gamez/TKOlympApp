using TkOlympApp.Models.People;

namespace TkOlympApp.Services.Abstractions;

public interface IPeopleService
{
    Task<List<Person>> GetPeopleAsync(CancellationToken ct = default);
}

