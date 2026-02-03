using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;

namespace TkOlympApp.ViewModels;

public sealed class EditRegistrationsRegGroup : ObservableCollection<EditRegistrationsRegItem>
{
    private readonly List<EditRegistrationsRegItem> _all = new();
    public string Key { get; }
    public int AllCount => _all.Count;

    public EditRegistrationsRegGroup(string key)
    {
        Key = key;
    }

    public void AddToGroup(EditRegistrationsRegItem item)
    {
        _all.Add(item);
        if (Count == 0)
        {
            base.Add(item);
        }
    }

    public void RefreshVisible()
    {
        Clear();
        var first = _all.FirstOrDefault();
        if (first != null) base.Add(first);
    }

    public void RevealMore(int maxToAdd)
    {
        if (maxToAdd <= 0) return;
        int added = 0;
        int start = Count;
        for (int i = start; i < _all.Count && added < maxToAdd; i++)
        {
            base.Add(_all[i]);
            added++;
        }
    }

    public IEnumerable<string> GetAllIds() => _all.Select(i => i.Id);

    public string FirstSecondary => _all.FirstOrDefault()?.Secondary ?? string.Empty;
}
