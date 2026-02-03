using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;

namespace TkOlympApp.ViewModels;

public sealed class DeleteRegistrationsRegGroup : ObservableCollection<DeleteRegistrationsRegItem>
{
    private readonly List<DeleteRegistrationsRegItem> _all = new();
    public string Key { get; }

    public DeleteRegistrationsRegGroup(string key)
    {
        Key = key;
    }

    public void AddToGroup(DeleteRegistrationsRegItem item)
    {
        _all.Add(item);
        if (Count == 0)
        {
            base.Add(item);
        }
    }

    public IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

    public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
}
