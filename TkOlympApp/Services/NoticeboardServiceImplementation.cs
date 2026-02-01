using System.Text.Json.Serialization;
using TkOlympApp.Models.Noticeboard;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services;

public sealed class NoticeboardServiceImplementation : INoticeboardService
{
    private readonly IGraphQlClient _graphQlClient;

    public NoticeboardServiceImplementation(IGraphQlClient graphQlClient)
    {
        _graphQlClient = graphQlClient ?? throw new ArgumentNullException(nameof(graphQlClient));
    }

    public async Task<List<Announcement>> GetMyAnnouncementsAsync(bool? sticky = null, CancellationToken ct = default)
    {
        var query =
            "query MyQuery($sticky: Boolean) { myAnnouncements(sticky: $sticky) { nodes { body createdAt id isSticky isVisible title author { id uJmeno uPrijmeni } updatedAt } } }";

        Dictionary<string, object>? variables = null;
        if (sticky.HasValue)
        {
            variables = new Dictionary<string, object> { { "sticky", sticky.Value } };
        }

        var data = await _graphQlClient.PostAsync<MyAnnouncementsData>(query, variables, ct);
        return data?.MyAnnouncements?.Nodes ?? new List<Announcement>();
    }

    public async Task<AnnouncementDetails?> GetAnnouncementAsync(long id, CancellationToken ct = default)
    {
        var query =
            "query MyQuery($id: BigInt!) { announcement(id: $id) { id title body createdAt updatedAt isVisible author { uJmeno uPrijmeni } } }";
        var variables = new Dictionary<string, object> { { "id", id } };

        var data = await _graphQlClient.PostAsync<AnnouncementData>(query, variables, ct);
        return data?.Announcement;
    }

    public async Task<List<Announcement>> GetStickyAnnouncementsAsync(CancellationToken ct = default)
    {
        return await GetMyAnnouncementsAsync(sticky: true, ct);
    }

    private sealed class MyAnnouncementsData
    {
        [JsonPropertyName("myAnnouncements")] public AnnouncementsWrapper? MyAnnouncements { get; set; }
    }

    private sealed class AnnouncementsWrapper
    {
        [JsonPropertyName("nodes")] public List<Announcement>? Nodes { get; set; }
    }

    private sealed class AnnouncementData
    {
        [JsonPropertyName("announcement")] public AnnouncementDetails? Announcement { get; set; }
    }
}
