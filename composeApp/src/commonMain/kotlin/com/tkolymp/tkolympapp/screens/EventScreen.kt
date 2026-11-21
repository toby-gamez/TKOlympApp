package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.components.skeleton.SkeletonEventDetail
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.utils.formatTimes
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.utils.standardDurationMinutes
import com.tkolymp.shared.viewmodels.EventState
import com.tkolymp.tkolympapp.components.InfoBanner
import com.tkolymp.tkolympapp.components.InfoPill
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.components.SheetPrimaryButton
import com.tkolymp.tkolympapp.components.campDateRange
import com.tkolymp.tkolympapp.components.campMonth
import com.tkolymp.tkolympapp.components.campWhenLabel
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.successContainerColor
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.tkolympapp.components.PaymentDraftCard
import com.tkolymp.tkolympapp.components.PayActionsStyle
import com.tkolymp.tkolympapp.components.PaymentPayActions
import com.tkolymp.shared.utils.fileUrl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.tkolympapp.components.HeaderIconFallback
import com.tkolymp.tkolympapp.components.HeaderImageShape
import com.tkolymp.tkolympapp.components.HeaderWithImage
import com.tkolymp.tkolympapp.components.InitialsAvatar
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import com.tkolymp.tkolympapp.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.campschedule.isRozpisTabVisible
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.asJsonArrayOrNull
import com.tkolymp.shared.utils.asJsonObjectOrNull
import com.tkolymp.shared.utils.int
import com.tkolymp.shared.utils.str
import com.tkolymp.shared.utils.translateEventType
import com.tkolymp.shared.viewmodels.EventSideEffect
import com.tkolymp.shared.viewmodels.EventViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.tkolympapp.screens.campschedule.RozpisTabContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.tkolymp.tkolympapp.util.Motion
import com.tkolymp.tkolympapp.util.SkeletonCrossfade
import com.tkolymp.tkolympapp.util.cozyContentSize
import com.tkolymp.tkolympapp.util.fadeSwitch
import com.tkolymp.tkolympapp.util.rememberFadeSwitch
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import com.tkolymp.tkolympapp.platform.HtmlText
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonArray
import com.tkolymp.tkolympapp.platform.FullscreenImageViewer
import com.tkolymp.tkolympapp.components.ErrorState

private val TIME_REGEX = Regex("""\d{2}:\d{2}""")

private fun boldTimes(text: String) = buildAnnotatedString {
    if (text.any { it == '.' || it.isLetter() }) {
        var last = 0
        for (match in TIME_REGEX.findAll(text)) {
            append(text.substring(last, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.value) }
            last = match.range.last + 1
        }
        append(text.substring(last))
    } else {
        append(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EventScreen(eventId: Long, instanceId: Long? = null, onBack: (() -> Unit)? = null, onOpenRegistration: ((String, String?) -> Unit)? = null, onOpenPerson: ((String) -> Unit)? = null, onOpenCouple: ((String) -> Unit)? = null, onOpenCohort: ((String) -> Unit)? = null, onOpenReminders: (() -> Unit)? = null, initialTab: Int = 0, onEdit: ((Long) -> Unit)? = null) {
    val viewModel = viewModel<EventViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showReminderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabSwitch = rememberFadeSwitch()
    val onTabSelect: (Int) -> Unit = { i -> if (i != selectedTab) scope.launch { tabSwitch.switchTo { selectedTab = i } } }
    val showRozpisTab = isRozpisTabVisible(state.eventType, state.firstInstanceIso, kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()))

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is EventSideEffect.CalendarResult -> {
                    val msg = if (effect.success) AppStrings.current.events.addToCalendarSuccess
                              else AppStrings.current.events.addToCalendarFailed
                    snackbarHostState.showSnackbar(msg)
                }
                is EventSideEffect.RegistrationDeleteFailed -> snackbarHostState.showSnackbar(effect.message)
                is EventSideEffect.ReminderResult -> {
                    val msg = if (effect.set) AppStrings.current.notifications.reminderSet
                              else AppStrings.current.notifications.reminderRemoveAction
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val ev = state.eventJson
    val fullScreenImageUrl = remember { mutableStateOf<String?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(ev) { if (ev != null) contentVisible = true }
    // registration navigation handled by App NavHost via `onOpenRegistration`

    val today = remember { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val participants = remember(state.registrations, state.trainers, state.myPersonId, state.myCoupleIds) { buildParticipants(state) }
    val myRegistrationName = participants.firstOrNull { it.isMe }?.name
    val showRegistrationBar = (state.registerButtonVisible || state.registrationActionsRowVisible) && onOpenRegistration != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // The bar is the top of the red hero, so everything on it is white.
            TopAppBar(
                title = {},
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                        }
                    }
                },
                actions = {
                    if (!state.isPast && state.firstInstanceIso != null) {
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = AppStrings.current.notifications.remindMe)
                        }
                        if (state.reminderId != null) {
                            IconButton(onClick = { scope.launch { viewModel.removeReminder(eventId) } }) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = AppStrings.current.notifications.reminderRemoveAction)
                            }
                        }
                    }
                    IconButton(
                        onClick = { scope.launch { viewModel.addToCalendar(eventId) } },
                        enabled = !state.isAddedToCalendar
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = AppStrings.current.events.addToCalendar)
                    }
                    if (!state.isOffline) {
                        com.tkolymp.tkolympapp.components.EventManageActions(
                            eventId = eventId,
                            eventJson = ev,
                            // Acts on the event itself (eventId), not on a selected child instance.
                            isCancelled = (ev?.get("isCancelled") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true",
                            onEdit = onEdit,
                            onDeleted = { onBack?.invoke() },
                            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                            onChanged = { scope.launch { viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true) } }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandLightPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        // The registration bar applies the nav-bar inset itself; taking it here too doubled the gap under the button.
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = if (showRegistrationBar) 0.dp else padding.calculateBottomPadding())) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showRozpisTab && selectedTab == 1) {
                Column(Modifier.fillMaxSize()) {
                    EventHeader(state, today, ev != null, uriHandler)
                    DetailToggle(selectedTab, onTabSelect)
                    RozpisTabContent(
                        eventId = eventId,
                        instances = state.instances.mapNotNull { it.asJsonObjectOrNull() },
                        eventName = state.eventName,
                        onOpenReminders = onOpenReminders,
                        modifier = Modifier.weight(1f).fillMaxWidth().fadeSwitch(tabSwitch)
                    )
                }
            } else {
                SwipeToReload(
                    isRefreshing = state.isLoading,
                    onRefresh = { scope.launch { viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true) } },
                    modifier = Modifier.fillMaxSize()
                ) {
                    SkeletonCrossfade(ev == null && state.error == null) { showSkeleton ->
                    if (showSkeleton) {
                        SkeletonEventDetail()
                    } else if (ev == null) {
                        ErrorState(
                            message = state.error?.message,
                            onRetry = { scope.launch { viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true) } }
                        )
                    } else {
                        run {
                            val bodySizeSp = 15f
                            val onBankCopy: (String) -> Unit = { account ->
                                scope.launch { snackbarHostState.showSnackbar("${AppStrings.current.misc.bankAccountCopied}: $account") }
                            }
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                // The red header stays put; only the body below it settles in.
                                EventHeader(state, today, true, uriHandler)
                                StaggeredItem(index = 0, visible = contentVisible, durationMs = 250) {
                                Column(modifier = Modifier.fillMaxWidth().fadeSwitch(tabSwitch).cozyContentSize()) {

                                if (state.userRegistered) {
                                    InfoBanner(
                                        icon = Icons.Default.CheckCircle,
                                        text = listOfNotNull(AppStrings.current.registration.signedUp, myRegistrationName).joinToString(" · "),
                                        containerColor = successContainerColor(),
                                        contentColor = successColor(),
                                        modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp)
                                    )
                                }

                                val statusChips = buildList<Pair<String, Boolean>> {
                                    if (state.isCancelled) add(AppStrings.current.stats.cancelled to true)
                                    if (state.isLocked) add(AppStrings.current.events.registrationClosed to false)
                                }
                                if (statusChips.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        statusChips.forEach { (label, isError) ->
                                            InfoPill(
                                                label,
                                                if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (showRozpisTab) DetailToggle(selectedTab, onTabSelect)

                                // Shrnutí a popis
                                if (state.summary.isNotBlank() || state.eventDescription.isNotBlank()) {
                                    val headerText = when {
                                        state.summary.isNotBlank() && state.eventDescription.isNotBlank() -> AppStrings.current.events.summaryAndDescription
                                        state.summary.isNotBlank() -> AppStrings.current.events.summaryOnly
                                        else -> AppStrings.current.events.descriptionOnly
                                    }
                                    DetailSectionLabel(headerText)
                                    Column(Modifier.padding(horizontal = Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                        listOf(state.summary, state.eventDescription).filter { it.isNotBlank() }.forEach { html ->
                                            HtmlText(
                                                html = html,
                                                modifier = Modifier.fillMaxWidth(),
                                                textColor = MaterialTheme.colorScheme.onBackground,
                                                linkColor = MaterialTheme.colorScheme.primary,
                                                textSizeSp = bodySizeSp,
                                                selectable = true,
                                                onImageClick = { url -> fullScreenImageUrl.value = url },
                                                onBankAccountCopy = onBankCopy
                                            )
                                        }
                                    }
                                }

                                // Platba (QR obrázky z popisu, přegenerované lokálně i s VS)
                                state.paymentQrs.forEach { pay ->
                                    PaymentPayActions(
                                        bankAccount = pay.account,
                                        amountMinor = pay.amountMinor,
                                        currency = pay.currency,
                                        variableSymbol = pay.variableSymbol,
                                        specificSymbol = pay.specificSymbol,
                                        message = pay.message,
                                        style = PayActionsStyle.Card,
                                        modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium),
                                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    )
                                }

                                // Záložní platba: účet z volného textu trenéra, když chybí hotový QR
                                state.paymentDraft?.let { draft ->
                                    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
                                    PaymentDraftCard(
                                        draft = draft,
                                        profile = state.paymentProfile,
                                        today = today,
                                        modifier = Modifier.padding(horizontal = Spacing.default),
                                        onSaveUnpaid = { payment -> scope.launch { viewModel.saveUnpaidPayment(eventId, payment) } },
                                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    )
                                }

                                // O události
                                DetailSectionLabel(AppStrings.current.campSchedule.aboutEventTab)
                                val rows = buildList<DetailInfoRow> {
                                    if (state.eventDateText.isNotBlank()) add(DetailInfoRow(Icons.Default.Schedule, AppStrings.current.events.eventDates, boldTimes(state.eventDateText)))
                                    val locationName = state.locationName?.takeIf { it.isNotBlank() }
                                    add(DetailInfoRow(
                                        Icons.Default.Place,
                                        AppStrings.current.calendarView.placeSection,
                                        androidx.compose.ui.text.AnnotatedString(locationName ?: "—"),
                                        onClick = locationName?.let { name -> { openMaps(uriHandler, name) } }
                                    ))
                                    val displayType = translateEventType(state.eventType)
                                    add(DetailInfoRow(Icons.Default.Category, AppStrings.current.calendarView.typeSection, androidx.compose.ui.text.AnnotatedString(displayType?.takeIf { it.isNotBlank() } ?: "—")))
                                    val trainerNames = state.trainers.mapNotNull { trainerEl ->
                                        val trainer = trainerEl.asJsonObjectOrNull() ?: return@mapNotNull null
                                        trainer["person"]?.asJsonObjectOrNull()?.str("name") ?: trainer.str("name")
                                    }
                                    if (trainerNames.isNotEmpty()) {
                                        add(DetailInfoRow(
                                            Icons.Default.Person,
                                            AppStrings.current.profile.trainers,
                                            androidx.compose.ui.text.AnnotatedString(trainerNames.joinToString(", "))
                                        ))
                                    }
                                }
                                ScheduleCard(Modifier.padding(horizontal = Spacing.default)) {
                                    rows.forEachIndexed { i, row ->
                                        if (i > 0) RowDivider()
                                        DetailInfoRowView(row)
                                    }
                                }

                                // Cílové skupiny
                                val cohortChips = state.cohorts.mapNotNull { cohortEl ->
                                    val cohort = cohortEl.asJsonObjectOrNull()?.get("cohort")?.asJsonObjectOrNull() ?: return@mapNotNull null
                                    val name = cohort.str("name") ?: return@mapNotNull null
                                    CohortChip(name, try { parseColorOrDefault(cohort.str("colorRgb")) } catch (_: Exception) { Color.Gray }, cohort.str("id"))
                                }
                                if (cohortChips.isNotEmpty()) {
                                    DetailSectionLabel(AppStrings.current.events.targetGroups)
                                    FlowRow(
                                        modifier = Modifier.padding(horizontal = Spacing.default),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                                    ) {
                                        cohortChips.forEach { (name, color, id) ->
                                            val open = id?.let { cid -> onOpenCohort?.let { f -> { f(cid) } } }
                                            Row(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                                    .then(if (open != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = open) else Modifier)
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(Modifier.size(8.dp).background(color, CircleShape))
                                                Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }

                                // Přihlášení účastníci
                                DetailSectionLabel(AppStrings.current.registration.participants, trailing = participants.size.toString())
                                if (participants.isEmpty()) {
                                    Text(
                                        AppStrings.current.registration.noParticipants,
                                        modifier = Modifier.padding(horizontal = Spacing.default),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    ScheduleCard(Modifier.padding(horizontal = Spacing.default)) {
                                        participants.forEachIndexed { i, item ->
                                            if (i > 0) RowDivider()
                                            ParticipantRowView(item, onOpenPerson, onOpenCouple)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(Spacing.large))
                            }
                            }
                            }
                            }
                    }
                    }
                }
            }

            fullScreenImageUrl.value?.let { url ->
                FullscreenImageViewer(imageUrl = url) { fullScreenImageUrl.value = null }
            }
        }

        AnimatedVisibility(
            visible = showRegistrationBar && onOpenRegistration != null,
            enter = fadeIn(tween(Motion.FadeMs)) + expandVertically(Motion.settleSpring()),
            exit = fadeOut(tween(Motion.FadeOutMs)) + shrinkVertically(tween(Motion.FadeMs))
        ) {
            onOpenRegistration?.let {
                RegistrationBar(
                    state = state,
                    onRegister = { it.invoke("register", null) },
                    onEdit = { it.invoke("edit", null) },
                    onDelete = {
                        if (state.ownedRegistrationCount == 1) showDeleteConfirm = true else it.invoke("delete", null)
                    }
                )
            }
        }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(AppStrings.current.registration.deleteRegistrationConfirmTitle) },
            text = { Text(AppStrings.current.registration.deleteRegistrationConfirmText) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch { viewModel.deleteOnlyRegistration(eventId) }
                }) { Text(AppStrings.current.commonActions.delete) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(AppStrings.current.commonActions.cancel) }
            }
        )
    }

    // Reminder dialog
    if (showReminderDialog) {
        val existingMinutes = state.reminderMinutesBefore ?: 30
        val isHoursInit = existingMinutes >= 60 && existingMinutes % 60 == 0
        var reminderUnit by remember(showReminderDialog) { mutableStateOf(if (isHoursInit) "h" else "min") }
        var reminderValue by remember(showReminderDialog) { mutableIntStateOf(if (isHoursInit) existingMinutes / 60 else existingMinutes) }
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(AppStrings.current.notifications.reminderDialogTitle) },
            text = {
                QuantityInput(
                    value = reminderValue,
                    onValueChange = { v, u -> reminderValue = v; reminderUnit = u },
                    units = listOf("min", "h"),
                    defaultUnit = reminderUnit,
                    label = AppStrings.current.notifications.timeAheadLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val minutes = if (reminderUnit == "h") reminderValue * 60 else reminderValue
                    scope.launch { viewModel.setReminder(eventId, minutes) }
                    showReminderDialog = false
                }) { Text(AppStrings.current.commonActions.save) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) { Text(AppStrings.current.commonActions.cancel) }
            }
        )
    }
}  // closes EventScreen

// ---------------------------------------------------------------------------------------------
// Prototype event detail pieces
// ---------------------------------------------------------------------------------------------

private data class ParticipantItem(
    val name: String,
    val isMe: Boolean,
    val subItems: List<String>,
    val womanName: String? = null,
    val manName: String? = null,
    val imageUrl: String? = null,
    val womanImageUrl: String? = null,
    val manImageUrl: String? = null,
    val personId: String? = null,
    val coupleId: String? = null,
)

/** Registered participants, couples first, child registrations skipped (same rules as before the redesign). */
private fun buildParticipants(state: EventState): List<ParticipantItem> {
    val coupleItems = mutableListOf<ParticipantItem>()
    val singleItems = mutableListOf<ParticipantItem>()
    state.registrations.forEach { regEl ->
        val reg = regEl.asJsonObjectOrNull() ?: return@forEach
        if (reg.str("parentRegistrationId") != null) return@forEach
        val person = reg["person"].asJsonObjectOrNull()
        val couple = reg["couple"].asJsonObjectOrNull()
        val note = reg.str("note")
        val lessonDemands = reg["eventLessonDemandsByRegistrationIdList"].asJsonArrayOrNull() ?: JsonArray(emptyList())

        val womanName: String?
        val manName: String?
        val nameText: String
        val personImage = if (couple == null) person.fileUrl() else null
        val womanImage = couple?.get("woman").asJsonObjectOrNull().fileUrl()
        val manImage = couple?.get("man").asJsonObjectOrNull().fileUrl()
        if (couple != null) {
            womanName = couple["woman"].asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
            manName = couple["man"].asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
            nameText = when {
                womanName != null && manName != null -> "$manName - $womanName"
                manName != null -> manName
                womanName != null -> womanName
                else -> return@forEach
            }
        } else {
            womanName = null
            manName = null
            nameText = person?.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
        }

        val personId = person?.str("id")
        val coupleId = couple?.str("id")
        val isMe = (personId != null && personId == state.myPersonId) ||
            (coupleId != null && state.myCoupleIds.contains(coupleId))

        val subItems = buildList<String> {
            lessonDemands.forEach { demandEl ->
                val demand = demandEl.asJsonObjectOrNull() ?: return@forEach
                val trainerId = demand.int("trainerId")
                val lessonCount = demand.int("lessonCount")
                val trainerName = state.trainers.firstOrNull { trainerEl ->
                    val trainer = trainerEl.asJsonObjectOrNull() ?: return@firstOrNull false
                    trainer.int("id") == trainerId
                }?.asJsonObjectOrNull()?.let { t ->
                    t["person"]?.asJsonObjectOrNull()?.str("name") ?: t.str("name")
                } ?: "${AppStrings.current.events.trainerFallbackPrefix}$trainerId"
                add(if (lessonCount != null && lessonCount > 0) "$trainerName: $lessonCount ${AppStrings.current.events.lessonCountSuffix}" else trainerName)
            }
            if (!note.isNullOrBlank()) add("${AppStrings.current.registration.notePrefix}$note")
        }

        val item = ParticipantItem(nameText, isMe, subItems, womanName, manName, personImage, womanImage, manImage, personId = if (couple == null) personId else null, coupleId = coupleId)
        if (couple != null) coupleItems.add(item) else singleItems.add(item)
    }
    return coupleItems + singleItems
}

private data class DetailInfoRow(
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val label: String,
    val value: androidx.compose.ui.text.AnnotatedString,
    val note: String? = null,
    val onClick: (() -> Unit)? = null,
    val valueContent: (@Composable () -> Unit)? = null
)

private data class CohortChip(val name: String, val color: Color, val id: String?)

@Composable
private fun DetailInfoRowView(row: DetailInfoRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.onClick != null) Modifier.pressClickable(0.995f, row.onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Box(Modifier.size(20.dp)) {
            if (row.icon != null) Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            if (row.label.isNotBlank()) Text(row.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.valueContent != null) row.valueContent.invoke() else Text(row.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (row.note != null) Text(row.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ParticipantRowView(item: ParticipantItem, onOpenPerson: ((String) -> Unit)? = null, onOpenCouple: ((String) -> Unit)? = null) {
    val open: (() -> Unit)? = when {
        item.coupleId != null && onOpenCouple != null -> { { onOpenCouple(item.coupleId) } }
        item.personId != null && onOpenPerson != null -> { { onOpenPerson(item.personId) } }
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().then(if (open != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = open) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        if (item.womanName != null || item.manName != null) {
            CoupleAvatar(womanName = item.womanName, manName = item.manName, size = 22.dp, fontSize = 9.sp, womanImageUrl = item.womanImageUrl, manImageUrl = item.manImageUrl)
        } else {
            InitialsAvatar(name = item.name, size = 32.dp, fontSize = 12.sp, imageUrl = item.imageUrl)
        }
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (item.isMe) FontWeight.SemiBold else FontWeight.Normal)
            item.subItems.forEach { sub ->
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.isMe) Text(AppStrings.current.calendarView.youTag.lowercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun DetailSectionLabel(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailToggle(selectedTab: Int, onSelect: (Int) -> Unit) {
    SegmentedToggle(
        labels = listOf(AppStrings.current.campSchedule.aboutEventTab, AppStrings.current.campSchedule.rozpisTab),
        selectedIndex = selectedTab,
        onSelect = onSelect,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = 14.dp)
    )
}

private fun openMaps(uriHandler: androidx.compose.ui.platform.UriHandler, locationName: String) {
    val encoded = locationName.map { c ->
        when {
            c.isLetterOrDigit() || c in "-_.~" -> c.toString()
            c == ' ' -> "+"
            else -> "%${c.code.toString(16).uppercase()}"
        }
    }.joinToString("")
    try { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$encoded") } catch (_: Exception) { }
}

/** Red block under the app bar: overline, big time/date, title, place, cohort colour stripe. */
@Composable
private fun EventHeader(
    state: EventState,
    today: kotlinx.datetime.LocalDate,
    loaded: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val strings = AppStrings.current
    val start = state.heroStartIso
    val end = state.heroEndIso
    val startDate = parseToLocal(start)?.date
    val multiDay = startDate != null && parseToLocal(end)?.date?.let { it != startDate } == true
    val isCamp = multiDay || state.eventType.equals("camp", ignoreCase = true)
    val typeLabel = translateEventType(state.eventType)?.takeIf { it.isNotBlank() }
    val whenLabel = if (isCamp) {
        campWhenLabel(com.tkolymp.shared.event.EventInstance(id = 0, isCancelled = false, since = start, until = end), today)
    } else startDate?.let {
        if (it == today) strings.timeline.today.lowercase() else formatFullCalendarDate(it, AppStrings.currentLanguage.code, it.year != today.year)
    }
    val overline = listOfNotNull(typeLabel, whenLabel).joinToString(" · ")
    val big = if (isCamp) campDateRange(start, end) else formatTimes(start, end).replace(" - ", " – ")
    val bigSub = if (isCamp) campMonth(start) else standardDurationMinutes(start, end)?.let { "$it min" }.orEmpty()
    val cohortColors = state.cohorts.mapNotNull { el ->
        el.asJsonObjectOrNull()?.get("cohort")?.asJsonObjectOrNull()?.str("colorRgb")?.let { hex -> try { parseColorOrDefault(hex) } catch (_: Exception) { null } }
    }
    val place = state.locationName?.takeIf { it.isNotBlank() }

    Box(Modifier.fillMaxWidth().background(BrandLightPrimary)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp)) {
            if (overline.isNotBlank()) Text(overline.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Color.White)
            if (loaded && big.isNotBlank()) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(big, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (bigSub.isNotBlank()) Text(bigSub, modifier = Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
            if (state.eventName.isNotBlank()) {
                Text(
                    state.eventName,
                    modifier = Modifier.padding(top = Spacing.small),
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textDecoration = if (state.isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
            }
            if (place != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { openMaps(uriHandler, place) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Text(" $place", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }
        if (cohortColors.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(5.dp).align(Alignment.BottomCenter)) {
                cohortColors.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
            }
        }
    }
}

/** Bottom action bar: red "register" pill, or edit pill plus a small delete button (one registration per user). */
@Composable
private fun RegistrationBar(
    state: EventState,
    onRegister: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = AppStrings.current.registration
    val editPrimary = state.registrationActionsRowVisible && state.editRegistrationButtonVisible
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        RowDivider()
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                state.registerButtonVisible -> SheetPrimaryButton(
                    text = strings.register,
                    onClick = onRegister,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.HowToReg
                )
                editPrimary -> SheetPrimaryButton(
                    text = strings.editRegistration,
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit
                )
                else -> TonalDangerButton(
                    text = strings.deleteRegistration,
                    onClick = onDelete,
                    enabled = !state.isDeletingRegistration,
                    modifier = Modifier.weight(1f)
                )
            }
            if (editPrimary) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable(enabled = !state.isDeletingRegistration, role = androidx.compose.ui.semantics.Role.Button, onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = AppStrings.current.registration.deleteRegistration, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TonalDangerButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.5f))
    }
}
