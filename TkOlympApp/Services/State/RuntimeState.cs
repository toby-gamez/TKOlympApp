using System;
using System.Threading.Tasks;
using Microsoft.Maui.Storage;
using TkOlympApp.Services.Abstractions;

namespace TkOlympApp.Services.State
{
    public sealed class RuntimeState : IRuntimeState
    {
        private readonly ISecureStorage _secureStorage;
        private string? _currentPersonId;
        private string? _currentCohortId;

        public RuntimeState(ISecureStorage secureStorage)
        {
            _secureStorage = secureStorage ?? throw new ArgumentNullException(nameof(secureStorage));
            _ = InitializeAsync();
        }

        public string? CurrentPersonId => _currentPersonId;
        public string? CurrentCohortId => _currentCohortId;

        public async Task InitializeAsync()
        {
            try
            {
                var p = await _secureStorage.GetAsync("currentPersonId");
                if (!string.IsNullOrWhiteSpace(p)) _currentPersonId = p;

                var c = await _secureStorage.GetAsync("currentCohortId");
                if (!string.IsNullOrWhiteSpace(c)) _currentCohortId = c;
            }
            catch
            {
                // best effort
            }
        }

        public async Task SetCurrentPersonIdAsync(string? personId)
        {
            _currentPersonId = personId;
            try
            {
                await _secureStorage.SetAsync("currentPersonId", string.IsNullOrEmpty(personId) ? string.Empty : personId);
            }
            catch
            {
                // best-effort
            }
        }

        public async Task SetCurrentCohortIdAsync(string? cohortId)
        {
            _currentCohortId = cohortId;
            try
            {
                await _secureStorage.SetAsync("currentCohortId", string.IsNullOrEmpty(cohortId) ? string.Empty : cohortId);
            }
            catch
            {
                // best-effort
            }
        }
    }
}
