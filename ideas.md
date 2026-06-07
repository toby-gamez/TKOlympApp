# Feature Ideas

All items below require no API changes — UI/client-side only.

## Calendar & Training

### 1. Calendar filters (trainer / location / cohort) ✅
Add a filter pill bar on `CalendarScreen` / `CalendarViewScreen`. The data is already loaded — filter client-side by trainer ID, location ID, or cohort. High value for users with multiple trainers.

### 3. Trainer availability heatmap ✅
A grid (columns = weekdays, rows = trainers) showing which trainers teach on which days, built from cached weekly events. Could live as a new tab in `TrainersLocationsScreen`.

## Stats & Personal

### 4. Training streak tracker ✅
`StatsViewModel` already has weekly attendance data. Count consecutive weeks with ≥1 session, display a streak counter + milestone badge on `StatsScreen`.

### 5. Weekly training goal + progress bar ✅
Let the user set a target (e.g. "3 lessons/week") in local storage. Show a progress bar in `StatsScreen` and a subtle indicator on `OverviewScreen`.

### 6. Mini stats widget on OverviewScreen ✅
Add a compact row — "This week: X sessions · Y hours" — pulled from `StatsViewModel`. One composable, no new data.

## People

### 7. "Trainers" quick filter on PeopleScreen ✅
A chip that filters the people list to trainers only. `PersonDetails.isTrainer` already exists.

### 8. Person → filter calendar by trainer ✅
From `PersonScreen`, if the person is a trainer, add a "See schedule" button that opens the calendar pre-filtered to that trainer's lessons. Pure navigation + filter state.

## UI / UX Polish

### 11. Leaderboard: pin your own position ✅
Always show the current user's row pinned at the bottom of `LeaderboardScreen` even when scrolled past it. Cross-reference `ScoreboardEntry` with `UserStorage`.

### 12. Payments due-date countdown on OverviewScreen
`OfflineDataStorage` caches payments. Show a small banner "Payment due in X days" or "Overdue payment" on the overview without any new API call.

### 13. Announcement unread badge
Track the last-seen announcement timestamp in local storage. Show a badge on the Board bottom-bar tab when new posts have appeared since the last visit.
