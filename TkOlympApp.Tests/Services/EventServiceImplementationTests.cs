using System;
using System.Net;
using System.Threading.Tasks;
using FluentAssertions;
using TkOlympApp.Exceptions;
using TkOlympApp.Services;
using TkOlympApp.Tests.Mocks;
using Xunit;

namespace TkOlympApp.Tests.Services;

public sealed class EventServiceImplementationTests
{
    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_whenResponseOk_returnsList()
    {
        var body = """
                   {
                     "data": {
                       "eventInstancesForRangeList": [
                         {
                           "id": 123,
                           "isCancelled": false,
                           "locationId": 5,
                           "since": "2026-01-02T10:00:00Z",
                           "until": "2026-01-02T11:00:00Z",
                           "updatedAt": "2026-01-02T09:00:00Z",
                           "event": {
                             "id": 10,
                             "description": null,
                             "name": "Camp A",
                             "type": "CAMP",
                             "locationText": "Somewhere",
                             "isRegistrationOpen": true,
                             "isPublic": true,
                             "eventTrainersList": [ { "name": "Trainer" } ],
                             "eventTargetCohortsList": [ { "cohortId": 1, "cohort": { "id": 1, "name": "A", "colorRgb": "#ffffff" } } ],
                             "eventRegistrationsList": [],
                             "location": { "id": 5, "name": "Loc" }
                           }
                         }
                       ]
                     }
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new EventServiceImplementation(gql);

        var result = await sut.GetMyEventInstancesForRangeAsync(
            new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc),
            new DateTime(2026, 1, 31, 0, 0, 0, DateTimeKind.Utc));

        result.Should().HaveCount(1);
        result[0].Id.Should().Be(123);
        result[0].Event.Should().NotBeNull();
        result[0].Event!.Name.Should().Be("Camp A");
        result[0].Event!.Id.Should().Be(10);
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_whenGraphQlErrors_throwsGraphQLException()
    {
        var body = """
                   {
                     "data": {
                       "eventInstancesForRangeList": []
                     },
                     "errors": [
                       { "message": "Boom" }
                     ]
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new EventServiceImplementation(gql);

        var act = async () => await sut.GetMyEventInstancesForRangeAsync(DateTime.UtcNow, DateTime.UtcNow.AddDays(1));

        await act.Should().ThrowAsync<GraphQLException>().WithMessage("Boom");
    }

    [Fact]
    public async Task GetMyEventInstancesForRangeAsync_whenInvalidJson_wrapsIntoServiceException()
    {
        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, "not json", "text/plain");
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new EventServiceImplementation(gql);

        var act = async () => await sut.GetMyEventInstancesForRangeAsync(DateTime.UtcNow, DateTime.UtcNow.AddDays(1));

        var ex = await act.Should().ThrowAsync<ServiceException>();
        ex.Which.Message.Should().Be("Neočekávaná chyba při načítání událostí");
        ex.Which.InnerException.Should().NotBeNull();
    }

    [Fact]
    public async Task GetEventInstancesForRangeListAsync_setsLastRawJson_andConvertsDateTimesToLocal()
    {
        var since = new DateTimeOffset(2026, 1, 2, 10, 0, 0, TimeSpan.Zero);
        var until = new DateTimeOffset(2026, 1, 2, 11, 0, 0, TimeSpan.Zero);
        var updatedAt = new DateTimeOffset(2026, 1, 2, 9, 0, 0, TimeSpan.Zero);

        var body = $$"""
                   {
                     "data": {
                       "eventInstancesForRangeList": [
                         {
                           "id": 1,
                           "isCancelled": false,
                           "locationId": 7,
                           "since": "{{since:O}}",
                           "until": "{{until:O}}",
                           "updatedAt": "{{updatedAt:O}}",
                           "event": { "id": 10, "name": "Camp", "isPublic": true }
                         }
                       ]
                     }
                   }
                   """;

        var handler = MockHelpers.CreateMockHttpMessageHandler(HttpStatusCode.OK, body);
        var http = MockHelpers.CreateMockHttpClient(handler);
        var gql = new GraphQlClientImplementation(http);
        var sut = new EventServiceImplementation(gql);

        var result = await sut.GetEventInstancesForRangeListAsync(DateTime.UtcNow, DateTime.UtcNow.AddDays(1));

        result.Should().HaveCount(1);
        sut.LastEventInstancesForRangeRawJson.Should().Be(body);

        var expectedSince = DateTime.SpecifyKind(since.ToLocalTime().DateTime, DateTimeKind.Local);
        var expectedUntil = DateTime.SpecifyKind(until.ToLocalTime().DateTime, DateTimeKind.Local);

        result[0].Since.Should().Be(expectedSince);
        result[0].Until.Should().Be(expectedUntil);
        result[0].Since!.Value.Kind.Should().Be(DateTimeKind.Local);
        result[0].Until!.Value.Kind.Should().Be(DateTimeKind.Local);
    }
}
