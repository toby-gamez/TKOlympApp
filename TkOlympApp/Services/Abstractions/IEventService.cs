using static TkOlympApp.Services.EventService;

namespace TkOlympApp.Services.Abstractions;

/// <summary>
/// Abstraction for event-related operations.
/// Provides methods to retrieve event instances, event details, and manage event data.
/// </summary>
public interface IEventService
{
    /// <summary>
    /// Gets the last raw JSON response from eventInstancesForRangeList query.
    /// Useful for debugging and UI display purposes.
    /// </summary>
    string? LastEventInstancesForRangeRawJson { get; }

    /// <summary>
    /// Retrieves detailed information about a specific event instance.
    /// Includes event details, trainers, registrations, and target cohorts.
    /// </summary>
    /// <param name="instanceId">The unique identifier of the event instance.</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>Event instance details, or null if not found.</returns>
    /// <exception cref="InvalidOperationException">Thrown if GraphQL request fails.</exception>
    Task<EventInstanceDetails?> GetEventInstanceAsync(long instanceId, CancellationToken ct = default);

    /// <summary>
    /// Retrieves comprehensive details about a specific event.
    /// Includes all event instances, registrations, attendance summary, and trainers.
    /// </summary>
    /// <param name="id">The unique identifier of the event.</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>Event details, or null if not found.</returns>
    /// <exception cref="InvalidOperationException">Thrown if GraphQL request fails.</exception>
    Task<EventDetails?> GetEventAsync(long id, CancellationToken ct = default);

    /// <summary>
    /// Retrieves the authenticated user's event instances within a specified time range.
    /// Supports pagination and filtering by event type.
    /// Includes comprehensive structured logging and error handling.
    /// </summary>
    /// <param name="startRange">Start of the time range.</param>
    /// <param name="endRange">End of the time range.</param>
    /// <param name="first">Maximum number of results to return (optional).</param>
    /// <param name="offset">Offset for pagination (optional).</param>
    /// <param name="onlyType">Filter by event type (optional).</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>List of event instances, or empty list on error.</returns>
    /// <exception cref="Exceptions.ServiceException">Thrown if API communication fails.</exception>
    /// <exception cref="Exceptions.GraphQLException">Thrown if GraphQL returns errors.</exception>
    /// <exception cref="OperationCanceledException">Thrown if operation is cancelled.</exception>
    Task<List<EventInstance>> GetMyEventInstancesForRangeAsync(
        DateTime startRange,
        DateTime endRange,
        int? first = null,
        int? offset = null,
        string? onlyType = null,
        CancellationToken ct = default);

    /// <summary>
    /// Retrieves all event instances (not just user's) within a specified time range.
    /// Supports pagination and filtering by event type.
    /// </summary>
    /// <param name="startRange">Start of the time range.</param>
    /// <param name="endRange">End of the time range.</param>
    /// <param name="first">Maximum number of results to return (optional).</param>
    /// <param name="offset">Offset for pagination (optional).</param>
    /// <param name="onlyType">Filter by event type (optional).</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>List of event instances.</returns>
    /// <exception cref="InvalidOperationException">Thrown if GraphQL request fails.</exception>
    Task<List<EventInstance>> GetEventInstancesForRangeListAsync(
        DateTime startRange,
        DateTime endRange,
        int? first = null,
        int? offset = null,
        string? onlyType = null,
        CancellationToken ct = default);

    /// <summary>
    /// Loads all event instances for a date range with automatic pagination.
    /// Retrieves data in batches of 15 until all instances are loaded.
    /// </summary>
    /// <param name="startRange">Start of the time range.</param>
    /// <param name="endRange">End of the time range.</param>
    /// <param name="onlyType">Filter by event type (optional).</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>Complete list of event instances.</returns>
    /// <exception cref="InvalidOperationException">Thrown if GraphQL request fails.</exception>
    Task<List<EventInstance>> GetAllEventInstancesPagedAsync(
        DateTime startRange,
        DateTime endRange,
        string? onlyType = null,
        CancellationToken ct = default);
}
