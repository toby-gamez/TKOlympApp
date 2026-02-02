using Microsoft.Maui.Storage;
using Moq;

namespace TkOlympApp.Tests.Mocks;

/// <summary>
/// Helper class for creating mock ISecureStorage implementations.
/// </summary>
public static class MockSecureStorage
{
    /// <summary>
    /// Creates a mock ISecureStorage with an in-memory dictionary backend.
    /// </summary>
    public static Mock<ISecureStorage> Create()
    {
        var storage = new Dictionary<string, string>();
        var mock = new Mock<ISecureStorage>();

        mock.Setup(s => s.GetAsync(It.IsAny<string>()))
            .ReturnsAsync((string key) => storage.TryGetValue(key, out var value) ? value : null);

        mock.Setup(s => s.SetAsync(It.IsAny<string>(), It.IsAny<string>()))
            .Returns((string key, string value) =>
            {
                storage[key] = value;
                return Task.CompletedTask;
            });

        mock.Setup(s => s.Remove(It.IsAny<string>()))
            .Returns((string key) =>
            {
                storage.Remove(key);
                return true;
            });

        mock.Setup(s => s.RemoveAll())
            .Callback(() => storage.Clear());

        return mock;
    }

    /// <summary>
    /// Creates a mock ISecureStorage with predefined values.
    /// </summary>
    public static Mock<ISecureStorage> CreateWithValues(Dictionary<string, string> initialValues)
    {
        var storage = new Dictionary<string, string>(initialValues);
        var mock = new Mock<ISecureStorage>();

        mock.Setup(s => s.GetAsync(It.IsAny<string>()))
            .ReturnsAsync((string key) => storage.TryGetValue(key, out var value) ? value : null);

        mock.Setup(s => s.SetAsync(It.IsAny<string>(), It.IsAny<string>()))
            .Returns((string key, string value) =>
            {
                storage[key] = value;
                return Task.CompletedTask;
            });

        mock.Setup(s => s.Remove(It.IsAny<string>()))
            .Returns((string key) =>
            {
                storage.Remove(key);
                return true;
            });

        mock.Setup(s => s.RemoveAll())
            .Callback(() => storage.Clear());

        return mock;
    }
}
