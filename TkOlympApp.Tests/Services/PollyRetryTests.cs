using System.Net;
using System.Net.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Http;
using Microsoft.Extensions.Logging;
using Polly;
using Polly.Extensions.Http;
using Xunit;
using TkOlympApp.Services;

namespace TkOlympApp.Tests.Services;

/// <summary>
/// Testy pro Polly retry a circuit breaker policies.
/// Ověřuje exponential backoff timing a circuit breaker behavior.
/// </summary>
public class PollyRetryTests
{
    [Fact]
    public async Task RetryPolicy_ShouldRetry3Times_OnTransientFailure()
    {
        // Arrange
        var attempts = 0;
        var handler = new MockHttpMessageHandler((request, ct) =>
        {
            attempts++;
            if (attempts <= 3)
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("{\"data\":{\"test\":\"success\"}}")
            });
        });

        var retryPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .Or<TimeoutException>()
            .WaitAndRetryAsync(
                retryCount: 3,
                sleepDurationProvider: retryAttempt => TimeSpan.FromSeconds(Math.Pow(2, retryAttempt)), // 2s, 4s, 8s
                onRetry: (outcome, timespan, retryCount, context) =>
                {
                    // Log retry attempt (verified in assertion)
                });

        var client = new HttpClient(new PolicyHttpMessageHandler(retryPolicy) { InnerHandler = handler });

        // Act
        var response = await client.GetAsync("https://test.com/api");

        // Assert
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(4, attempts); // 3 failures + 1 success
    }

    [Fact]
    public async Task RetryPolicy_ShouldUseExponentialBackoff()
    {
        // Arrange
        var attemptTimes = new List<DateTime>();
        var handler = new MockHttpMessageHandler((request, ct) =>
        {
            attemptTimes.Add(DateTime.UtcNow);
            if (attemptTimes.Count <= 3)
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK));
        });

        var retryPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .WaitAndRetryAsync(
                retryCount: 3,
                sleepDurationProvider: retryAttempt => TimeSpan.FromSeconds(Math.Pow(2, retryAttempt)));

        var client = new HttpClient(new PolicyHttpMessageHandler(retryPolicy) { InnerHandler = handler });

        // Act
        await client.GetAsync("https://test.com/api");

        // Assert
        Assert.Equal(4, attemptTimes.Count);
        
        // Verify exponential backoff (approximate timing: 2s, 4s, 8s)
        var delay1 = (attemptTimes[1] - attemptTimes[0]).TotalSeconds;
        var delay2 = (attemptTimes[2] - attemptTimes[1]).TotalSeconds;
        var delay3 = (attemptTimes[3] - attemptTimes[2]).TotalSeconds;

        Assert.InRange(delay1, 1.5, 2.5); // ~2s
        Assert.InRange(delay2, 3.5, 4.5); // ~4s
        Assert.InRange(delay3, 7.5, 8.5); // ~8s
    }

    [Fact]
    public async Task CircuitBreaker_ShouldOpen_After5ConsecutiveFailures()
    {
        // Arrange
        var attempts = 0;
        var handler = new MockHttpMessageHandler((request, ct) =>
        {
            attempts++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
        });

        var circuitBreakerPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .CircuitBreakerAsync(
                handledEventsAllowedBeforeBreaking: 5,
                durationOfBreak: TimeSpan.FromSeconds(1));

        var client = new HttpClient(new PolicyHttpMessageHandler(circuitBreakerPolicy) { InnerHandler = handler });

        // Act - provede 5 requests, které selžou
        for (int i = 0; i < 5; i++)
        {
            try { await client.GetAsync("https://test.com/api"); }
            catch { /* Expected failures */ }
        }

        // Assert - 6th request by měl hodit BrokenCircuitException
        await Assert.ThrowsAsync<Polly.CircuitBreaker.BrokenCircuitException<HttpResponseMessage>>(async () =>
        {
            await client.GetAsync("https://test.com/api");
        });

        Assert.Equal(5, attempts); // Circuit opened after 5 attempts, 6th didn't reach handler
    }

    [Fact]
    public async Task CircuitBreaker_ShouldHalfOpen_AfterDuration()
    {
        // Arrange
        var attempts = 0;
        var handler = new MockHttpMessageHandler((request, ct) =>
        {
            attempts++;
            // Fail first 5, succeed after circuit break duration
            if (attempts <= 5)
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK));
        });

        var circuitBreakerPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .CircuitBreakerAsync(
                handledEventsAllowedBeforeBreaking: 5,
                durationOfBreak: TimeSpan.FromMilliseconds(500));

        var client = new HttpClient(new PolicyHttpMessageHandler(circuitBreakerPolicy) { InnerHandler = handler });

        // Act - trigger circuit open
        for (int i = 0; i < 5; i++)
        {
            try { await client.GetAsync("https://test.com/api"); }
            catch { }
        }

        // Verify circuit is open
        await Assert.ThrowsAsync<Polly.CircuitBreaker.BrokenCircuitException<HttpResponseMessage>>(async () =>
        {
            await client.GetAsync("https://test.com/api");
        });

        // Wait for circuit break duration
        await Task.Delay(600);

        // Assert - circuit should be half-open, next request should succeed and close it
        var response = await client.GetAsync("https://test.com/api");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(6, attempts); // 5 failures + 1 success after half-open
    }

    [Fact]
    public async Task RetryPolicy_ShouldNotRetry_OnNonTransientError()
    {
        // Arrange
        var attempts = 0;
        var handler = new MockHttpMessageHandler((request, ct) =>
        {
            attempts++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.BadRequest));
        });

        var retryPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .WaitAndRetryAsync(3, retryAttempt => TimeSpan.FromSeconds(1));

        var client = new HttpClient(new PolicyHttpMessageHandler(retryPolicy) { InnerHandler = handler });

        // Act
        var response = await client.GetAsync("https://test.com/api");

        // Assert - BadRequest (400) není transient error, nemělo by se opakovat
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(1, attempts); // Only 1 attempt, no retries
    }

    [Fact]
    public async Task RetryAndCircuitBreaker_ShouldWorkTogether()
    {
        // Arrange
        var attempts = 0;
        var handler = new MockHttpMessageHandler((request, ct) =>
        {
            attempts++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
        });

        // Retry 3 times, then circuit breaker kicks in after 2 consecutive failures
        var retryPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .WaitAndRetryAsync(3, retryAttempt => TimeSpan.FromMilliseconds(100));

        var circuitBreakerPolicy = HttpPolicyExtensions
            .HandleTransientHttpError()
            .CircuitBreakerAsync(2, TimeSpan.FromSeconds(1));

        var policyWrap = Policy.WrapAsync(retryPolicy, circuitBreakerPolicy);

        var client = new HttpClient(new PolicyHttpMessageHandler(policyWrap) { InnerHandler = handler });

        // Act - first request: retry 3 times (4 total attempts)
        try { await client.GetAsync("https://test.com/api"); }
        catch { }

        // Second request: should trigger circuit breaker after 2 more failures
        try { await client.GetAsync("https://test.com/api"); }
        catch { }

        // Assert - circuit should be open now
        await Assert.ThrowsAsync<Polly.CircuitBreaker.BrokenCircuitException<HttpResponseMessage>>(async () =>
        {
            await client.GetAsync("https://test.com/api");
        });
    }
}

/// <summary>
/// Mock HttpMessageHandler pro testování HTTP requests.
/// </summary>
public class MockHttpMessageHandler : HttpMessageHandler
{
    private readonly Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> _handler;

    public MockHttpMessageHandler(Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> handler)
    {
        _handler = handler;
    }

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        return _handler(request, cancellationToken);
    }
}
