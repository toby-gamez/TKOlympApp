using TkOlympApp.Exceptions;

namespace TkOlympApp.Tests.Exceptions;

public class ServiceExceptionTests
{
    [Fact]
    public void ServiceException_WithTransientError_SetsIsTransientTrue()
    {
        // Arrange & Act
        var exception = new ServiceException("Network timeout", null, isTransient: true);

        // Assert
        Assert.True(exception.IsTransient);
        Assert.Equal("Network timeout", exception.Message);
    }

    [Fact]
    public void ServiceException_WithHttpStatusCode_StoresStatusCode()
    {
        // Arrange & Act
        var exception = new ServiceException(
            "Server error",
            null,
            isTransient: false,
            httpStatusCode: 500);

        // Assert
        Assert.Equal(500, exception.HttpStatusCode);
        Assert.False(exception.IsTransient);
    }

    [Fact]
    public void ServiceException_WithContext_StoresContextData()
    {
        // Arrange
        var exception = new ServiceException("Error with context");

        // Act
        exception
            .WithContext("UserId", 123)
            .WithContext("Operation", "LoadEvents")
            .WithContext("Timestamp", DateTime.UtcNow);

        // Assert
        Assert.Equal(3, exception.Context.Count);
        Assert.Equal(123, exception.Context["UserId"]);
        Assert.Equal("LoadEvents", exception.Context["Operation"]);
        Assert.True(exception.Context.ContainsKey("Timestamp"));
    }

    [Fact]
    public void ServiceException_WithInnerException_WrapsOriginalException()
    {
        // Arrange
        var innerException = new InvalidOperationException("Inner error");

        // Act
        var exception = new ServiceException("Outer error", innerException);

        // Assert
        Assert.NotNull(exception.InnerException);
        Assert.Equal("Inner error", exception.InnerException.Message);
        Assert.IsType<InvalidOperationException>(exception.InnerException);
    }

    [Fact]
    public void ServiceException_WithContextChaining_ReturnsThis()
    {
        // Arrange
        var exception = new ServiceException("Test");

        // Act
        var result = exception
            .WithContext("Key1", "Value1")
            .WithContext("Key2", "Value2");

        // Assert
        Assert.Same(exception, result);
        Assert.Equal(2, exception.Context.Count);
    }
}
