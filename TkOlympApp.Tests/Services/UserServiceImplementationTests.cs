using System.Text.Json;
using Microsoft.Maui.Storage;
using Moq;
using TkOlympApp.Models.Users;
using TkOlympApp.Services;
using TkOlympApp.Services.Abstractions;
using Xunit;

namespace TkOlympApp.Tests.Services;

public class UserServiceImplementationTests
{
    [Fact]
    public async Task SetCurrentPersonIdAsync_PersistsToSecureStorage_AndUpdatesProperty()
    {
        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Strict);
        secureStorage.Setup(s => s.SetAsync("currentPersonId", "123")).Returns(Task.CompletedTask);

        var gql = new FakeGraphQlClient(_ => throw new InvalidOperationException("not used"));
        var svc = new UserServiceImplementation(gql, secureStorage.Object);

        await svc.SetCurrentPersonIdAsync("123");

        Assert.Equal("123", svc.CurrentPersonId);
        secureStorage.VerifyAll();
    }

    [Fact]
    public async Task InitializeAsync_LoadsStoredPersonAndCohortIds()
    {
        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Strict);
        secureStorage.Setup(s => s.GetAsync("currentPersonId")).ReturnsAsync("p1");
        secureStorage.Setup(s => s.GetAsync("currentCohortId")).ReturnsAsync("c1");

        var gql = new FakeGraphQlClient(_ => "{}");
        var svc = new UserServiceImplementation(gql, secureStorage.Object);

        await svc.InitializeAsync();

        Assert.Equal("p1", svc.CurrentPersonId);
        Assert.Equal("c1", svc.CurrentCohortId);
        secureStorage.VerifyAll();
    }

    [Fact]
    public async Task GetCurrentUserAsync_ParsesGraphQlResponse()
    {
      var json = "{\"data\":{\"getCurrentUser\":{\"uEmail\":\"j@x.cz\",\"uJmeno\":null,\"uLogin\":\"john\",\"createdAt\":\"2026-02-02T00:00:00Z\",\"id\":1,\"lastActiveAt\":null,\"lastLogin\":null,\"tenantId\":1,\"uPrijmeni\":null,\"updatedAt\":\"2026-02-02T00:00:00Z\"}}}";

        var gql = new FakeGraphQlClient(query =>
            query.Contains("getCurrentUser", StringComparison.OrdinalIgnoreCase) ? json : "{}");

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Loose);
        var svc = new UserServiceImplementation(gql, secureStorage.Object);

        var user = await svc.GetCurrentUserAsync();

        Assert.NotNull(user);
        Assert.Equal(1, user!.Id);
        Assert.Equal("john", user.ULogin);
        Assert.Equal("j@x.cz", user.UEmail);
    }

    [Fact]
    public async Task GetActiveCouplesFromUsersAsync_ReturnsCouples()
    {
        var json = """
                   {
                     "data": {
                       "users": {
                         "nodes": [
                           {
                             "userProxiesList": [
                               {
                                 "person": {
                                   "activeCouplesList": [
                                     {
                                       "id": "c1",
                                       "man": { "firstName": "A", "lastName": "Man" },
                                       "woman": { "firstName": "B", "lastName": "Woman" }
                                     }
                                   ],
                                   "cohortMembershipsList": []
                                 }
                               }
                             ]
                           }
                         ]
                       }
                     }
                   }
                   """;

        var gql = new FakeGraphQlClient(query =>
            query.Contains("activeCouplesList", StringComparison.OrdinalIgnoreCase) ? json : "{}");

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Loose);
        var svc = new UserServiceImplementation(gql, secureStorage.Object);

        var couples = await svc.GetActiveCouplesFromUsersAsync();

        Assert.Single(couples);
        Assert.Equal("A Man", couples[0].ManName);
        Assert.Equal("B Woman", couples[0].WomanName);
        Assert.Equal("c1", couples[0].Id);
    }

    [Fact]
    public async Task GetCohortsFromUsersAsync_DeduplicatesById()
    {
        var json = """
                   {
                     "data": {
                       "users": {
                         "nodes": [
                           {
                             "userProxiesList": [
                               {
                                 "person": {
                                   "activeCouplesList": [],
                                   "cohortMembershipsList": [
                                     { "cohort": { "id": "k1", "colorRgb": "#ff0000", "name": "A" } },
                                     { "cohort": { "id": "k1", "colorRgb": "#ff0000", "name": "A" } },
                                     { "cohort": { "id": "k2", "colorRgb": "#00ff00", "name": "B" } }
                                   ]
                                 }
                               }
                             ]
                           }
                         ]
                       }
                     }
                   }
                   """;

        var gql = new FakeGraphQlClient(query =>
            query.Contains("cohortMembershipsList", StringComparison.OrdinalIgnoreCase) ? json : "{}");

        var secureStorage = new Mock<ISecureStorage>(MockBehavior.Loose);
        var svc = new UserServiceImplementation(gql, secureStorage.Object);

        var cohorts = await svc.GetCohortsFromUsersAsync();

        Assert.Equal(2, cohorts.Count);
        Assert.Contains(cohorts, c => c.Id == "k1" && c.Name == "A");
        Assert.Contains(cohorts, c => c.Id == "k2" && c.Name == "B");
    }

    private sealed class FakeGraphQlClient : IGraphQlClient
    {
        private readonly Func<string, string> _responseByQuery;

        public FakeGraphQlClient(Func<string, string> responseByQuery)
        {
            _responseByQuery = responseByQuery;
        }

        public Task<T> PostAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
        {
            var raw = _responseByQuery(query);
            var data = DeserializeData<T>(raw);
            return Task.FromResult(data);
        }

        public Task<(T Data, string Raw)> PostWithRawAsync<T>(string query, Dictionary<string, object>? variables = null, CancellationToken ct = default)
        {
            var raw = _responseByQuery(query);
            var data = DeserializeData<T>(raw);
            return Task.FromResult((data, raw));
        }

        private static T DeserializeData<T>(string raw)
        {
            var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
            {
                PropertyNameCaseInsensitive = true
            };

            // GraphQlClientImplementation returns the "data" object only;
            // UserServiceImplementation expects the same shape.
            using var doc = JsonDocument.Parse(raw);
            if (doc.RootElement.TryGetProperty("data", out var dataEl))
            {
                var dataJson = dataEl.GetRawText();
                var parsed = JsonSerializer.Deserialize<T>(dataJson, options);
                return parsed!;
            }

            // Some tests might provide already-unwrapped JSON.
            var fallback = JsonSerializer.Deserialize<T>(raw, options);
            return fallback!;
        }
    }
}
