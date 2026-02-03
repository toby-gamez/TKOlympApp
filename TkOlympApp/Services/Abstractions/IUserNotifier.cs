using System.Threading.Tasks;

namespace TkOlympApp.Services.Abstractions
{
    public interface IUserNotifier
    {
        Task ShowAsync(string title, string message, string cancel = "OK");
    }
}
