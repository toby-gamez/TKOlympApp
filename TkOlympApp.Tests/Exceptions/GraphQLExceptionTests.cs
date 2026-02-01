using TkOlympApp.Exceptions;

namespace TkOlympApp.Tests.Exceptions;

public class GraphQLExceptionTests
{
    [Fact]
    public void GraphQLException_WithSingleError_CreatesListWithOneError()
    {
        // Arrange & Act
        var exception = new GraphQLException(
            "GraphQL request failed",
            "Invalid query syntax",
            rawResponse: "{\"errors\":[...]}");

        // Assert
        Assert.Single(exception.Errors);
        Assert.Equal("Invalid query syntax", exception.Errors[0]);
        Assert.Equal("{\"errors\":[...]}", exception.RawResponse);
        Assert.False(exception.IsTransient); // GraphQL errors are not transient
    }

    [Fact]
    public void GraphQLException_WithMultipleErrors_StoresAllErrors()
    {
        // Arrange
        var errors = new List<string>
        {
            "Field 'id' not found",
            "Required argument 'name' missing",
            "Type mismatch on 'age'"
        };

        // Act
        var exception = new GraphQLException("Multiple errors", errors);

        // Assert
        Assert.Equal(3, exception.Errors.Count);
        Assert.Contains("Field 'id' not found", exception.Errors);
        Assert.Contains("Required argument 'name' missing", exception.Errors);
        Assert.Contains("Type mismatch on 'age'", exception.Errors);
    }

    [Fact]
    public void GraphQLException_WithRawResponse_StoresRawResponse()
    {
        // Arrange
        var rawJson = "{\"data\":null,\"errors\":[{\"message\":\"Auth failed\"}]}";

        // Act
        var exception = new GraphQLException("Auth error", "Not authenticated", rawJson);

        // Assert
        Assert.Equal(rawJson, exception.RawResponse);
    }

    [Fact]
    public void GraphQLException_InheritsFromServiceException()
    {
        // Arrange & Act
        var exception = new GraphQLException("Test", "Error");

        // Assert
        Assert.IsAssignableFrom<ServiceException>(exception);
    }
}
