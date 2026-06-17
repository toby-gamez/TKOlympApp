package com.tkolymp.shared.language

data class TutorialStrings(
    val tutorialRowLabel: String = "App Tutorial",
    val tutorialRowSubtitle: String = "A guided tour of the app",

    // Intro step
    val introTitle: String = "Let's introduce you",
    val introDesc: String = "We'll walk you through the key features of the app. Tap Next to begin, or Skip to explore on your own.",

    // Overview sections
    val overviewUpcomingTitle: String = "Upcoming Trainings",
    val overviewUpcomingDesc: String = "Your upcoming training sessions. Tap a session card to see the location, time and who else is attending.",
    val overviewBoardTitle: String = "Board Snippet",
    val overviewBoardDesc: String = "A preview of the three most recent club announcements. Tap a post for the full text, or head to the Board tab to see all posts.",
    val overviewCampsTitle: String = "Upcoming Events",
    val overviewCampsDesc: String = "Club events and activities coming up soon. Tap any item to see details or sign up.",
    val overviewCompetitionsTitle: String = "Nearest Competition",
    val overviewCompetitionsDesc: String = "Your next registered competition at a glance — name, location, and date. Tap to open the full competition list.",
    val overviewBirthdaysTitle: String = "Birthdays",
    val overviewBirthdaysDesc: String = "Upcoming birthdays of club members — so you never miss a chance to celebrate.",
    val overviewStatsTitle: String = "Weekly Stats",
    val overviewStatsDesc: String = "Dancers see their session count and training minutes for the week, progress toward a weekly goal, and a fun weekly persona card based on their schedule.",

    // Calendar sections
    val calendarMineTitle: String = "Mine Tab",
    val calendarMineDesc: String = "Shows only trainings from your own training groups and individual lessons — your quickest view of the week ahead.",
    val calendarAllTitle: String = "All Tab",
    val calendarAllDesc: String = "Shows every session across all groups. Useful for finding sessions you could join and sign up for.",
    val calendarFilterTitle: String = "Filter & Week Navigation",
    val calendarFilterDesc: String = "Tap ⋮ to filter sessions by trainer or location. Use the arrows in the app bar to jump forward or back by week.",

    // Board sections
    val boardListTitle: String = "Announcements",
    val boardListDesc: String = "A scrollable list of every club post from trainers. Tap any announcement to read the full text.",
    val boardStickyTitle: String = "Permanent Board",
    val boardStickyDesc: String = "Pinned posts from club trainers — always visible and separate from the regular news feed.",

    // Events sections
    val eventsPlannedTitle: String = "Planned Events",
    val eventsPlannedDesc: String = "Upcoming camps and club events, sorted by date. Tap any event to see details and sign up.",
    val eventsPastTitle: String = "Past Events",
    val eventsPastDesc: String = "Events that have already taken place. You can still view all the details and browse who attended.",

    // Other sections
    val otherAccountTitle: String = "Your Account",
    val otherAccountDesc: String = "Your profile card — tap it to view or edit your profile. Quick buttons below let you jump to Payments, Stats, training overview or create personal trainings.",
    val otherQrTitle: String = "ČSTS Competition Code",
    val otherQrDesc: String = "Your personal code containing your ČSTS dancer ID. Show it at ČSTS competitions to verify your attendance at check-in.",
    val otherPeopleTitle: String = "Members & Club",
    val otherPeopleDesc: String = "Browse club members, trainers, training groups, and the leaderboard. Great for getting to know your fellow dancers and seeing how the club is organised.",

    // Closing step
    val otherTitle: String = "That's it!",
    val otherDesc: String = "You're all set. Feel free to explore the app and discover everything it has to offer.",

    // Navigation labels
    val next: String = "Next",
    val previous: String = "Back",
    val skip: String = "Skip",
    val done: String = "Done",
    val stepOf: String = "of",
)
