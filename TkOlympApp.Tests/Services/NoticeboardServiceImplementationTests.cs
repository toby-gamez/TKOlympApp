using System;
using System.Net;
using System.Threading.Tasks;
using FluentAssertions;
using TkOlympApp.Exceptions;
using TkOlympApp.Services;
using TkOlympApp.Tests.Mocks;
using Xunit;

namespace TkOlympApp.Tests.Services;

public sealed class NoticeboardServiceImplementationTests
{
    [Fact]
    public async Task GetMyAnnouncementsAsync_whenNodesPresent_returnsList()
    {
        var body = """
                   {
                     "data": {
                       "myAnnouncements": {
                         "nodes": [
                           {
                             "id": 1,
                             "title": "Hello",
                             "body": "World",
                             "createdAt": "2026-01-01T12:00:00Z",
                             "updatedAt": null,
                             "isSticky": false,
                             "isVisible": true,
                             "author": { "id": 9, "uJmeno": "A", "uPrijmeni": "B" }
                           }
                         ]
                       }
                     }
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new NoticeboardServiceImplementation(gql);

        var result = await sut.GetMyAnnouncementsAsync();

        result.Should().HaveCount(1);
        result[0].Id.Should().Be(1);
        result[0].Title.Should().Be("Hello");
        result[0].Author.Should().NotBeNull();
        result[0].Author!.Id.Should().Be(9);
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_whenStickyTrue_sendsVariable()
    {
        string? capturedRequestJson = null;

        var body = """
                   {
                     "data": {
                       "myAnnouncements": {
                         "nodes": []
                       }
                     }
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(async (req, ct) =>
        {
            capturedRequestJson = await req.Content!.ReadAsStringAsync(ct);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(body)
            };
        });

        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new NoticeboardServiceImplementation(gql);

        _ = await sut.GetMyAnnouncementsAsync(sticky: true);

        capturedRequestJson.Should().NotBeNull();
        capturedRequestJson!.Should().Contain("\"sticky\":true");
    }

    [Fact]
    public async Task GetMyAnnouncementsAsync_whenNodesNull_returnsEmptyList()
    {
        var body = """
                   {
                     "data": {
                       "myAnnouncements": {
                         "nodes": null
                       }
                     }
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new NoticeboardServiceImplementation(gql);

        var result = await sut.GetMyAnnouncementsAsync();

        result.Should().NotBeNull();
        result.Should().BeEmpty();
    }

    [Fact]
    public async Task GetAnnouncementAsync_whenPresent_returnsDetails()
    {
        var body = """
                   {
                     "data": {
                       "announcement": {
                         "id": 7,
                         "title": "T",
                         "body": "B",
                         "createdAt": "2026-01-01T12:00:00Z",
                         "updatedAt": "2026-01-01T13:00:00Z",
                         "isVisible": true,
                         "author": { "uJmeno": "A", "uPrijmeni": "B", "id": 2 }
                       }
                     }
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new NoticeboardServiceImplementation(gql);

        var result = await sut.GetAnnouncementAsync(7);

        result.Should().NotBeNull();
        result!.Id.Should().Be(7);
        result.Title.Should().Be("T");
        result.Author.Should().NotBeNull();
        result.Author!.FirstName.Should().Be("A");
    }

    [Fact]
    public async Task GetAnnouncementAsync_whenGraphQlErrors_throwsGraphQLException()
    {
        var body = """
                   {
                     "data": { "announcement": null },
                     "errors": [ { "message": "No access" } ]
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new NoticeboardServiceImplementation(gql);

        var act = async () => await sut.GetAnnouncementAsync(1);

        await act.Should().ThrowAsync<GraphQLException>().WithMessage("No access");
    }
}
