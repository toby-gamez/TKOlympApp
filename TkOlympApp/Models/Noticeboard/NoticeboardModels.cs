using System.Text.Json.Serialization;

namespace TkOlympApp.Models.Noticeboard;

public sealed record Announcement(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("title")] string? Title,
    [property: JsonPropertyName("body")] string? Body,
    [property: JsonPropertyName("createdAt")] DateTime CreatedAt,
    [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
    [property: JsonPropertyName("isSticky")] bool IsSticky,
    [property: JsonPropertyName("isVisible")] bool IsVisible,
    [property: JsonPropertyName("author")] Author? Author
);

public sealed record AnnouncementDetails(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("title")] string? Title,
    [property: JsonPropertyName("body")] string? Body,
    [property: JsonPropertyName("createdAt")] DateTime CreatedAt,
    [property: JsonPropertyName("updatedAt")] DateTime? UpdatedAt,
    [property: JsonPropertyName("isVisible")] bool IsVisible,
    [property: JsonPropertyName("author")] Author? Author
);

public sealed record Author(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("uJmeno")] string? FirstName,
    [property: JsonPropertyName("uPrijmeni")] string? LastName
);
