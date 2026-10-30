package com.mina.agpeya

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mina.agpeya.alarm.PrayerNotification
import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PrayerSection
import com.mina.agpeya.data.PrayerSpec
import com.mina.agpeya.data.ReaderFontFamily
import com.mina.agpeya.data.ReaderBackground
import com.mina.agpeya.data.ReaderFontStyle
import com.mina.agpeya.data.ReaderFontSettings
import com.mina.agpeya.data.PsalmMode
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AgpeyaViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refreshSchedules()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AgpeyaApp(
                    viewModel = viewModel,
                    initialPrayerKey = intent.getStringExtra(PrayerNotification.EXTRA_OPEN_PRAYER),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgpeyaApp(viewModel: AgpeyaViewModel, initialPrayerKey: String?) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsDraft by viewModel.settingsDraft.collectAsState()
    val saving by viewModel.savingSettings.collectAsState()
    val syncAction by viewModel.syncAction.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val backupAccount by viewModel.backupAccount.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val savingSettings = saving || syncAction != null
    DriveAuthorization(viewModel)
    val settingsChanged by viewModel.settingsChanged.collectAsState()
    val pagedReading by viewModel.pagedReading.collectAsState()
    val readerFontSettings by viewModel.readerFontSettings.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val psalmsByPrayer by viewModel.psalmsByPrayer.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbars.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsPrayer by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPrayer by rememberSaveable { mutableStateOf(initialPrayerKey) }
    var pendingSoundPrayer by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sectionsDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun navigate(action: () -> Unit) {
        if (savingSettings) return
        if (settingsChanged) {
            pendingNavigation = action
        } else {
            viewModel.endEditingSettings()
            action()
        }
    }
    LaunchedEffect(selectedTab, reading == null) {
        if (selectedTab == 1 && reading == null) viewModel.beginEditingSettings()
    }
    if (pendingNavigation != null) {
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text("تغييرات غير محفوظة") },
            text = { Text("احفظ التغييرات من شاشة الإعدادات أو تجاهلها للمتابعة.") },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingNavigation
                    pendingNavigation = null
                    viewModel.endEditingSettings()
                    action?.invoke()
                }) { Text("تجاهل التغييرات") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNavigation = null }) { Text("متابعة التعديل") }
            },
        )
    }
    DisposableEffect(view, reading != null) {
        val isReading = reading != null
        val wasKeepingScreenOn = view.keepScreenOn
        if (isReading) view.keepScreenOn = true
        onDispose {
            if (isReading) view.keepScreenOn = wasKeepingScreenOn
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val prayerKey = pendingSoundPrayer
        if (result.resultCode == android.app.Activity.RESULT_OK && prayerKey != null) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            }
            viewModel.setSoundUri(prayerKey, uri?.toString())
        }
        pendingSoundPrayer = null
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(pendingPrayer) {
        pendingPrayer?.let {
            viewModel.openPrayer(it)
            pendingPrayer = null
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbars.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(reading?.prayer?.key) {
        sectionsDrawerState.close()
    }

    BackHandler(enabled = drawerState.isOpen || sectionsDrawerState.isOpen || reading != null || selectedTab != 0) {
        if (drawerState.isOpen || sectionsDrawerState.isOpen) {
            scope.launch {
                drawerState.close()
                sectionsDrawerState.close()
            }
            return@BackHandler
        }
        navigate {
            viewModel.closeReading()
            selectedTab = 0
            settingsPrayer = null
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = { Text(reading?.prayer?.title ?: "الأجبية") },
                            navigationIcon = {
                                if (reading != null) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            if (sectionsDrawerState.isOpen) sectionsDrawerState.close()
                                            else {
                                                drawerState.close()
                                                sectionsDrawerState.open()
                                            }
                                        }
                                    }) {
                                        Icon(
                                            painterResource(R.drawable.ic_menu),
                                            contentDescription = "أجزاء الصلاة",
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (reading != null) {
                                    TextButton(onClick = viewModel::closeReading) {
                                        Text("رجوع")
                                    }
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close()
                                        else {
                                            sectionsDrawerState.close()
                                            drawerState.open()
                                        }
                                    }
                                }) {
                                    Icon(
                                        painterResource(R.drawable.ic_menu),
                                        contentDescription = "قائمة الصلوات",
                                    )
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (reading == null) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { navigate { selectedTab = 0 } },
                                    icon = { Text("☼") },
                                    label = { Text("الصلوات") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        settingsPrayer = null
                                        selectedTab = 1
                                    },
                                    icon = { Text("⚙") },
                                    label = { Text("الإعدادات") },
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbars) },
                ) { padding ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            modifier = Modifier.fillMaxSize().padding(padding),
                            gesturesEnabled = !sectionsDrawerState.isOpen,
                            drawerContent = {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    PrayerNavigationDrawer(
                                        prayerGroups = groups,
                                        selectedPrayerKey = reading?.prayer?.key,
                                        onOpenPrayer = { prayerKey ->
                                            scope.launch { drawerState.close() }
                                            navigate { viewModel.openPrayer(prayerKey) }
                                        },
                                    )
                                }
                            },
                        ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(readerFontSettings.background.image),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = if (reading != null) 1f else 0.3f,
                            colorFilter = if (reading != null) {
                                backgroundContrastFilter(readerFontSettings.backgroundContrast)
                            } else null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (reading != null) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Color.Black.copy(alpha = readerFontSettings.backgroundTransparency),
                                ),
                            )
                        }
                        when {
                            reading != null -> ReadingScreen(
                                reading!!,
                                sectionsDrawerState,
                                pagedReading,
                                readerFontSettings,
                                Modifier,
                            )
                            selectedTab == 0 -> HomeScreen(
                                onOpenPrayer = viewModel::openPrayer,
                                onPrayerSettings = { prayerKey ->
                                    settingsPrayer = prayerKey
                                    selectedTab = 1
                                },
                                modifier = Modifier,
                            )
                            settingsDraft == null -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                            else -> SettingsScreen(
                                settings = settingsDraft!!.prayers,
                                psalmsByPrayer = psalmsByPrayer,
                                readerFontSettings = settingsDraft!!.font,
                                pagedReading = settingsDraft!!.pagedReading,
                                onPagedReadingChanged = viewModel::setPagedReading,
                                saving = savingSettings,
                                changed = settingsChanged,
                                onSave = viewModel::saveSettings,
                                onCancel = viewModel::cancelSettings,
                                onReaderFontScaleChanged = viewModel::setReaderFontScale,
                                onReaderFontFamilyChanged = viewModel::setReaderFontFamily,
                                onReaderFontStyleChanged = viewModel::setReaderFontStyle,
                                onReaderFontColorChanged = viewModel::setReaderFontColor,
                                onReaderShadowColorChanged = viewModel::setReaderShadowColor,
                                onBackgroundTransparencyChanged = viewModel::setBackgroundTransparency,
                                onBackgroundContrastChanged = viewModel::setBackgroundContrast,
                                onBackgroundChanged = viewModel::setBackground,
                                onAlarmEnabled = viewModel::setAlarmEnabled,
                                onTimeChanged = viewModel::setTime,
                                onVolumeChanged = viewModel::setVolume,
                                onPickSound = { preference ->
                                    pendingSoundPrayer = preference.key
                                    ringtonePicker.launch(
                                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE,
                                            )
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختيار صوت التنبيه")
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                preference.soundUri?.let(Uri::parse)
                                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                                            )
                                        },
                                    )
                                },
                                onPsalmModeChanged = viewModel::setPsalmMode,
                                onPsalmCountChanged = viewModel::setPsalmCount,
                                onSequentialGroupSelected = viewModel::setSequentialGroup,
                                onFixedPsalmToggled = viewModel::toggleFixedPsalm,
                                onResetPsalms = viewModel::resetPsalms,
                                onApplyToAll = viewModel::applyToAll,
                                syncing = syncAction != null,
                                backupAccount = backupAccount,
                                backupStatus = backupStatus,
                                onConnectBackup = { viewModel.requestSettingsSync(SettingsSyncAction.CONNECT) },
                                onDisconnectBackup = viewModel::disconnectDriveBackup,
                                onBackup = { viewModel.requestSettingsSync(SettingsSyncAction.BACKUP) },
                                onRestore = { viewModel.requestSettingsSync(SettingsSyncAction.RESTORE) },
                                prayerKey = settingsPrayer,
                                modifier = Modifier,
                            )
                        }
                    }
                    }
                        }
                    }
                }
    }
}

@Composable
private fun SettingsScreen(
    settings: List<PrayerPreference>,
    psalmsByPrayer: Map<String, List<PrayerSection>>,
    readerFontSettings: ReaderFontSettings,
    pagedReading: Boolean,
    onPagedReadingChanged: (Boolean) -> Unit,
    saving: Boolean,
    changed: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReaderFontScaleChanged: (Float) -> Unit,
    onReaderFontFamilyChanged: (ReaderFontFamily) -> Unit,
    onReaderFontStyleChanged: (ReaderFontStyle) -> Unit,
    onReaderFontColorChanged: (Int) -> Unit,
    onReaderShadowColorChanged: (Int?) -> Unit,
    onBackgroundTransparencyChanged: (Float) -> Unit,
    onBackgroundContrastChanged: (Float) -> Unit,
    onBackgroundChanged: (ReaderBackground) -> Unit,
    onAlarmEnabled: (String, Boolean) -> Unit,
    onTimeChanged: (String, String) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onPickSound: (PrayerPreference) -> Unit,
    onPsalmModeChanged: (String, PsalmMode) -> Unit,
    onPsalmCountChanged: (String, Int) -> Unit,
    onSequentialGroupSelected: (String, Int) -> Unit,
    onFixedPsalmToggled: (String, String) -> Unit,
    onResetPsalms: (String) -> Unit,
    onApplyToAll: (String) -> Unit,
    syncing: Boolean,
    backupAccount: String?,
    backupStatus: String?,
    onConnectBackup: () -> Unit,
    onDisconnectBackup: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    prayerKey: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expandedPrayer by rememberSaveable(prayerKey) { mutableStateOf(prayerKey) }
    var generalExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSave, enabled = changed && !saving) {
                Text(if (saving && !syncing) "جارٍ الحفظ..." else "حفظ")
            }
            OutlinedButton(onClick = onCancel, enabled = changed && !saving) { Text("إلغاء") }
            Text(
                if (changed) "تغييرات غير محفوظة" else "الإعدادات المحفوظة",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = !saving,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "إعدادات التنبيهات والمزامير",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TextButton(
                        onClick = { generalExpanded = !generalExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (generalExpanded) "−" else "+")
                            Text("الإعدادات العامة", textAlign = TextAlign.Start)
                        }
                    }
                    if (generalExpanded) {
                        Column(
                            Modifier.padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Column(Modifier.padding(18.dp)) {
                                    Text("أسلوب القراءة", style = MaterialTheme.typography.titleMedium)
                                    listOf(false to "تمرير متصل", true to "صفحة بصفحة").forEach { (paged, label) ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { onPagedReadingChanged(paged) },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = pagedReading == paged,
                                                onClick = { onPagedReadingChanged(paged) },
                                            )
                                            Text(label)
                                        }
                                    }
                                }
                            }
                            ReaderFontSettingsCard(
                                settings = readerFontSettings,
                                onScaleChanged = onReaderFontScaleChanged,
                                onFamilyChanged = onReaderFontFamilyChanged,
                                onStyleChanged = onReaderFontStyleChanged,
                                onColorChanged = onReaderFontColorChanged,
                                onShadowColorChanged = onReaderShadowColorChanged,
                            )
                            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("اختيار الخلفية", style = MaterialTheme.typography.titleMedium)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(ReaderBackground.entries, key = { it.name }) { background ->
                                            val selected = readerFontSettings.background == background
                                            Column(
                                                Modifier.widthIn(max = 88.dp)
                                                    .selectable(
                                                        selected = selected,
                                                        role = Role.RadioButton,
                                                        onClick = { onBackgroundChanged(background) },
                                                    ),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Image(
                                                    painterResource(background.image),
                                                    contentDescription = "الخلفية ${background.ordinal + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(80.dp, 112.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .border(
                                                            if (selected) 3.dp else 1.dp,
                                                            if (selected) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.outline,
                                                            MaterialTheme.shapes.small,
                                                        ),
                                                )
                                                Text(
                                                    if (selected) "مختارة" else "${background.ordinal + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                    Text("شفافية خلفية القراءة", style = MaterialTheme.typography.titleMedium)
                                    Text("${(readerFontSettings.backgroundTransparency * 100).toInt()}٪")
                                    Slider(
                                        value = readerFontSettings.backgroundTransparency,
                                        onValueChange = onBackgroundTransparencyChanged,
                                        valueRange = 0f..1f,
                                    )
                                    Text(
                                        "٠٪: صورة واضحة — ١٠٠٪: خلفية سوداء",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text("تباين الخلفية: ${(readerFontSettings.backgroundContrast * 100).toInt()}٪")
                                    Slider(
                                        value = readerFontSettings.backgroundContrast,
                                        onValueChange = onBackgroundContrastChanged,
                                        valueRange = 0.5f..2f,
                                    )
                                    TextButton(onClick = { onBackgroundContrastChanged(1f) }) {
                                        Text("التباين الافتراضي (١٠٠٪)")
                                    }
                                    ReaderAppearancePreview(readerFontSettings)
                                }
                            }
                            ReminderPermissionCard()
                            Column(
                                Modifier.padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("المزامنة السحابية", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "حفظ الإعدادات في مساحة التطبيق الخاصة في Google Drive أو استعادتها. " +
                                        "أصوات التنبيه الخاصة بالجهاز لا تُنقل. الاستعادة تحتاج حفظاً لتطبيقها.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "اختر حساب Google مرة واحدة. بعد تفعيل النسخ التلقائي، تُنسخ التغييرات " +
                                        "إلى الحساب نفسه كلما ضغطت حفظ، وعند توفر الإنترنت. إلغاء لا ينسخ التعديلات.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (changed) {
                                    Text("احفظ التغييرات أو ألغها قبل المزامنة.")
                                }
                                if (syncing) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                    Text("جارٍ الاتصال بـ Google Drive...")
                                }
                                var confirmRestore by rememberSaveable { mutableStateOf(false) }
                                if (backupAccount != null) {
                                    Text("حساب النسخ التلقائي: $backupAccount")
                                    backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                OutlinedButton(onClick = onConnectBackup, enabled = !saving && !changed) {
                                    Text(if (backupAccount == null) "اختيار حساب وتفعيل النسخ التلقائي" else "تغيير الحساب أو إعادة ربطه")
                                }
                                if (backupAccount != null) {
                                    TextButton(onClick = onDisconnectBackup, enabled = !saving) {
                                        Text("إيقاف النسخ التلقائي")
                                    }
                                }
                                OutlinedButton(onClick = onBackup, enabled = !saving && !changed) {
                                    Text("نسخ الإعدادات إلى Google Drive")
                                }
                                OutlinedButton(
                                    onClick = { confirmRestore = true },
                                    enabled = !saving && !changed,
                                ) {
                                    Text("استعادة الإعدادات من Google Drive")
                                }
                                if (confirmRestore) {
                                    AlertDialog(
                                        onDismissRequest = { confirmRestore = false },
                                        title = { Text("استعادة الإعدادات") },
                                        text = { Text("سيتم تحميل نسخة حساب Google إلى مسودة الإعدادات. لن تُطبّق إلا بعد الضغط على حفظ.") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                confirmRestore = false
                                                onRestore()
                                            }) { Text("استعادة") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { confirmRestore = false }) { Text("إلغاء") }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(
                settings.filter { prayerKey == null || it.key == prayerKey },
                key = PrayerPreference::key,
            ) { preference ->
                val prayer = PrayerCatalog.canonicalPrayers.first { it.key == preference.key }
                PrayerSettingsCard(
                    prayer = prayer,
                    preference = preference,
                    psalms = psalmsByPrayer[preference.key].orEmpty(),
                    expanded = expandedPrayer == preference.key,
                    onExpanded = {
                        expandedPrayer = if (expandedPrayer == preference.key) null else preference.key
                    },
                    onAlarmEnabled = onAlarmEnabled,
                    onVolumeChanged = onVolumeChanged,
                    onPickSound = { onPickSound(preference) },
                    onPsalmModeChanged = onPsalmModeChanged,
                    onPsalmCountChanged = onPsalmCountChanged,
                    onSequentialGroupSelected = onSequentialGroupSelected,
                    onFixedPsalmToggled = onFixedPsalmToggled,
                    onResetPsalms = { onResetPsalms(preference.key) },
                    onApplyToAll = { onApplyToAll(preference.key) },
                    onPickTime = { current ->
                        val parts = current.split(':').map(String::toInt)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onTimeChanged(
                                    preference.key,
                                    String.format(Locale.ROOT, "%02d:%02d", hour, minute),
                                )
                            },
                            parts[0],
                            parts[1],
                            true,
                        ).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderFontSettingsCard(
    settings: ReaderFontSettings,
    onScaleChanged: (Float) -> Unit,
    onFamilyChanged: (ReaderFontFamily) -> Unit,
    onStyleChanged: (ReaderFontStyle) -> Unit,
    onColorChanged: (Int) -> Unit,
    onShadowColorChanged: (Int?) -> Unit,
) {
    var scale by remember(settings.scale) { mutableFloatStateOf(settings.scale) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showShadowColorPicker by rememberSaveable { mutableStateOf(false) }
    val selectedColor = Color(settings.color)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إعدادات خط القراءة", style = MaterialTheme.typography.titleMedium)
            Text("حجم الخط: ${(scale * 100).toInt()}٪")
            Slider(
                value = scale,
                onValueChange = {
                    scale = it
                    onScaleChanged(it)
                },
                valueRange = 0.8f..1.6f,
                steps = 7,
            )
            Text("نوع الخط", style = MaterialTheme.typography.titleSmall)
            ReaderFontFamily.entries.forEach { family ->
                val label = when (family) {
                    ReaderFontFamily.DEFAULT -> "الافتراضي"
                    ReaderFontFamily.SERIF -> "نسخي"
                    ReaderFontFamily.MONOSPACE -> "أحادي المسافة"
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onFamilyChanged(family) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.family == family,
                        onClick = { onFamilyChanged(family) },
                    )
                    Text(label)
                }
            }
            Text("نمط الخط", style = MaterialTheme.typography.titleSmall)
            ReaderFontStyle.entries.forEach { style ->
                val label = when (style) {
                    ReaderFontStyle.REGULAR -> "عادي"
                    ReaderFontStyle.BOLD -> "عريض"
                    ReaderFontStyle.ITALIC -> "مائل"
                    ReaderFontStyle.BOLD_ITALIC -> "عريض ومائل"
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onStyleChanged(style) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.style == style,
                        onClick = { onStyleChanged(style) },
                    )
                    Text(label)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("لون الخط", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showColorPicker = true }) {
                    Box(
                        Modifier.padding(end = 8.dp).size(24.dp)
                            .background(selectedColor, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text("اختيار اللون")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("لون ظل النص", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showShadowColorPicker = true }) {
                    Box(
                        Modifier.padding(end = 8.dp).size(24.dp)
                            .background(readerTextShadow(selectedColor, settings.shadowColor).color, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text("اختيار اللون")
                }
            }
            TextButton(
                onClick = { onShadowColorChanged(null) },
                enabled = settings.shadowColor != null,
            ) {
                Text("لون الظل التلقائي")
            }
            ReaderAppearancePreview(settings.copy(scale = scale))
        }
    }
    if (showColorPicker) {
        ReaderColorPickerDialog(
            initialColor = settings.color,
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                onColorChanged(it)
                showColorPicker = false
            },
        )
    }
    if (showShadowColorPicker) {
        ReaderColorPickerDialog(
            initialColor = settings.shadowColor ?: if (selectedColor.luminance() > 0.5f) {
                0xE6000000.toInt()
            } else {
                0xE6FFFFFF.toInt()
            },
            title = "اختيار لون ظل النص",
            onDismiss = { showShadowColorPicker = false },
            onColorSelected = {
                onShadowColorChanged(it)
                showShadowColorPicker = false
            },
        )
    }
}

@Composable
private fun ReaderAppearancePreview(settings: ReaderFontSettings) {
    val photo = painterResource(settings.background.image)
    val scrollState = key(settings.background) { rememberScrollState() }
    val color = Color(settings.color)
    val family = when (settings.family) {
        ReaderFontFamily.DEFAULT -> FontFamily.Default
        ReaderFontFamily.SERIF -> FontFamily.Serif
        ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
    }
    val weight = when (settings.style) {
        ReaderFontStyle.REGULAR, ReaderFontStyle.ITALIC -> FontWeight.Normal
        ReaderFontStyle.BOLD, ReaderFontStyle.BOLD_ITALIC -> FontWeight.Bold
    }
    val style = when (settings.style) {
        ReaderFontStyle.REGULAR, ReaderFontStyle.BOLD -> FontStyle.Normal
        ReaderFontStyle.ITALIC, ReaderFontStyle.BOLD_ITALIC -> FontStyle.Italic
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "مرّر الصورة لأعلى ولأسفل لاختبار وضوح النص على جميع أجزاء الخلفية.",
            style = MaterialTheme.typography.bodySmall,
        )
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Image(
                    painter = photo,
                    contentDescription = "معاينة خلفية القراءة",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = backgroundContrastFilter(settings.backgroundContrast),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        .aspectRatio(photo.intrinsicSize.width / photo.intrinsicSize.height),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Color.Black.copy(alpha = settings.backgroundTransparency),
                ),
            )
            Text(
                "باسم الآب والابن والروح القدس\nمثال على شكل نص القراءة",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = color,
                    fontFamily = family,
                    fontWeight = weight,
                    fontStyle = style,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * settings.scale,
                    shadow = readerTextShadow(color, settings.shadowColor),
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReaderColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    title: String = "اختيار لون الخط",
) {
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember(initialColor) {
        mutableFloatStateOf(android.graphics.Color.alpha(initialColor) / 255f)
    }
    var pickerColor by remember(initialColor) { mutableIntStateOf(initialColor) }

    fun updateColor(color: Int) {
        pickerColor = color
    }

    fun updateFromHsv() {
        updateColor(
            android.graphics.Color.HSVToColor(
                (alpha * 255).toInt(),
                floatArrayOf(hue, saturation, brightness),
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HueColorWheel(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onColorChanged = { newHue, newSaturation ->
                        hue = newHue
                        saturation = newSaturation
                        updateFromHsv()
                    },
                )
                Row(
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(24.dp).background(Color(pickerColor))
                            .border(1.dp, MaterialTheme.colorScheme.outline),
                    )
                    Text(String.format(Locale.ROOT, "#%08X", pickerColor))
                }
                Text("الشفافية")
                ColorGradientSlider(
                    value = alpha,
                    startColor = Color(pickerColor).copy(alpha = 0f),
                    endColor = Color(pickerColor).copy(alpha = 1f),
                    checkerboard = true,
                    onValueChange = {
                        alpha = it
                        updateFromHsv()
                    },
                )
                Text("السطوع")
                ColorGradientSlider(
                    value = brightness,
                    startColor = Color.Black,
                    endColor = Color(
                        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)),
                    ),
                    onValueChange = {
                        brightness = it
                        updateFromHsv()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(pickerColor) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
private fun ColorGradientSlider(
    value: Float,
    startColor: Color,
    endColor: Color,
    onValueChange: (Float) -> Unit,
    checkerboard: Boolean = false,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(20.dp)) {
                if (checkerboard) {
                    val cell = 5.dp.toPx()
                    val columns = kotlin.math.ceil(size.width / cell).toInt()
                    val rows = kotlin.math.ceil(size.height / cell).toInt()
                    for (row in 0 until rows) {
                        for (column in 0 until columns) {
                            drawRect(
                                if ((row + column) % 2 == 0) Color.White else Color.LightGray,
                                topLeft = Offset(column * cell, row * cell),
                                size = androidx.compose.ui.geometry.Size(
                                    minOf(cell, size.width - column * cell),
                                    minOf(cell, size.height - row * cell),
                                ),
                            )
                        }
                    }
                }
                drawRect(Brush.horizontalGradient(listOf(startColor, endColor)))
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun HueColorWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onColorChanged: (Float, Float) -> Unit,
) {
    val updateAtPosition: (Offset, Float, Float) -> Unit = { position, width, height ->
        val center = Offset(width / 2f, height / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val radius = minOf(width, height) * 0.47f
        val angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble())
        onColorChanged(
            ((360.0 - angle) % 360.0).toFloat(),
            (distance / radius).coerceIn(0f, 1f),
        )
    }
    val latestUpdateAtPosition = rememberUpdatedState(updateAtPosition)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        latestUpdateAtPosition.value(
                            it,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        latestUpdateAtPosition.value(
                            change.position,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) * 0.47f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
                        Color.Green, Color.Yellow, Color.Red,
                    ),
                    center,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(Color.Black.copy(alpha = 1f - brightness), radius, center)
            val angle = Math.toRadians(-hue.toDouble())
            val indicator = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * radius * saturation,
                center.y + kotlin.math.sin(angle).toFloat() * radius * saturation,
            )
            drawCircle(Color.Black, 9.dp.toPx(), indicator)
            drawCircle(Color.White, 8.dp.toPx(), indicator)
            drawCircle(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))),
                5.dp.toPx(),
                indicator,
            )
        }
    }
}

private fun backgroundContrastFilter(contrast: Float): ColorFilter {
    val offset = 127.5f * (1f - contrast)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, offset,
                0f, contrast, 0f, 0f, offset,
                0f, 0f, contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

private fun colorToHsv(color: Int): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }

@Composable
private fun ReminderPermissionCard() {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
    val exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("دقة التنبيهات", style = MaterialTheme.typography.titleMedium)
            Text(
                if (exactAlarmsAllowed) "التنبيهات الدقيقة مسموح بها."
                else "فعّل التنبيهات الدقيقة للحصول على مواعيد أكثر دقة.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!exactAlarmsAllowed) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    },
                ) {
                    Text("فتح إعدادات التنبيه الدقيق")
                }
            }
        }
    }
}

@Composable
private fun PrayerSettingsCard(
    prayer: PrayerSpec,
    preference: PrayerPreference,
    psalms: List<PrayerSection>,
    expanded: Boolean,
    onExpanded: () -> Unit,
    onAlarmEnabled: (String, Boolean) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onPickSound: () -> Unit,
    onPsalmModeChanged: (String, PsalmMode) -> Unit,
    onPsalmCountChanged: (String, Int) -> Unit,
    onSequentialGroupSelected: (String, Int) -> Unit,
    onFixedPsalmToggled: (String, String) -> Unit,
    onResetPsalms: () -> Unit,
    onApplyToAll: () -> Unit,
    onPickTime: (String) -> Unit,
) {
    var sliderValue by remember(preference.volume) { mutableFloatStateOf(preference.volume) }
    var countValue by remember(preference.psalmCount) { mutableFloatStateOf(preference.psalmCount.toFloat()) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onExpanded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (expanded) "−" else "+")
                    Text(
                        prayer.title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { onPickTime(preference.time) }) {
                            Text("موعد التنبيه: ${preference.time}")
                        }
                    }
                    Switch(
                        checked = preference.enabled,
                        onCheckedChange = { onAlarmEnabled(prayer.key, it) },
                    )
                }
                Text("مستوى صوت التنبيه: ${(sliderValue * 100).toInt()}٪")
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onVolumeChanged(prayer.key, it)
                    },
                    valueRange = 0f..1f,
                )
                TextButton(onClick = onPickSound) {
                    Text(if (preference.soundUri == null) "اختيار صوت التنبيه (الافتراضي)" else "تغيير صوت التنبيه")
                }
                HorizontalDivider()
                Text("إعدادات المزامير", style = MaterialTheme.typography.titleSmall)
                PsalmSettings(
                    prayer = prayer,
                    preference = preference,
                    psalms = psalms,
                    countValue = countValue,
                    onCountValueChanged = {
                        countValue = it
                        onPsalmCountChanged(prayer.key, it.toInt())
                    },
                    onCountChangeFinished = { onPsalmCountChanged(prayer.key, countValue.toInt()) },
                    onPsalmModeChanged = onPsalmModeChanged,
                    onSequentialGroupSelected = onSequentialGroupSelected,
                    onFixedPsalmToggled = onFixedPsalmToggled,
                    onResetPsalms = onResetPsalms,
                )
                HorizontalDivider()
                Text(
                    "تطبيق الصوت ومستواه وتفعيل التنبيه واختيارات المزامير على باقي الصلوات، " +
                        "دون تغيير المواعيد أو المجموعة الحالية.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onApplyToAll) {
                    Text("تطبيق على الكل")
                }
            }
        }
    }
}

@Composable
private fun PsalmSettings(
    prayer: PrayerSpec,
    preference: PrayerPreference,
    psalms: List<PrayerSection>,
    countValue: Float,
    onCountValueChanged: (Float) -> Unit,
    onCountChangeFinished: () -> Unit,
    onPsalmModeChanged: (String, PsalmMode) -> Unit,
    onSequentialGroupSelected: (String, Int) -> Unit,
    onFixedPsalmToggled: (String, String) -> Unit,
    onResetPsalms: () -> Unit,
) {
    val modeLabels = mapOf(
        PsalmMode.RANDOM to "عشوائي",
        PsalmMode.SEQUENTIAL to "تتابعي",
        PsalmMode.FIXED to "ثابت",
        PsalmMode.ALL to "جميع المزامير",
    )
    Text("طريقة اختيار المزامير", style = MaterialTheme.typography.titleSmall)
    TextButton(onClick = onResetPsalms) {
        Text("إعادة ضبط المزامير")
    }
    PsalmMode.entries.forEach { mode ->
        Row(
            Modifier.fillMaxWidth().clickable { onPsalmModeChanged(prayer.key, mode) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = preference.psalmMode == mode,
                onClick = { onPsalmModeChanged(prayer.key, mode) },
            )
            Text(modeLabels.getValue(mode))
        }
    }
    if (psalms.isNotEmpty()) {
        if (preference.psalmMode == PsalmMode.RANDOM ||
            preference.psalmMode == PsalmMode.SEQUENTIAL
        ) {
            val selectedCount = countValue.toInt().coerceIn(1, psalms.size)
            Text("عدد المزامير: $selectedCount")
            Slider(
                value = countValue.coerceIn(1f, psalms.size.toFloat()),
                onValueChange = onCountValueChanged,
                onValueChangeFinished = onCountChangeFinished,
                valueRange = 1f..psalms.size.toFloat(),
                steps = (psalms.size - 2).coerceAtLeast(0),
            )
            if (preference.psalmMode == PsalmMode.SEQUENTIAL) {
                var groupMenuExpanded by rememberSaveable(prayer.key) {
                    mutableStateOf(false)
                }
                val groupOffsets = (psalms.indices step selectedCount).toList()
                val selectedGroup = ((preference.sequentialOffset % psalms.size) /
                    selectedCount).coerceIn(groupOffsets.indices)
                val groupLabel: (Int) -> String = { groupIndex ->
                    val start = groupOffsets[groupIndex]
                    val end = (start + selectedCount - 1).coerceAtMost(psalms.lastIndex)
                    val psalmRange = if (start == end) {
                        psalms[start].title
                    } else {
                        "${psalms[start].title} - ${psalms[end].title}"
                    }
                    "المجموعة ${groupIndex + 1}: $psalmRange"
                }
                Text("مجموعة المزامير التالية", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { groupMenuExpanded = true }) {
                        Text(groupLabel(selectedGroup))
                    }
                    DropdownMenu(
                        expanded = groupMenuExpanded,
                        onDismissRequest = { groupMenuExpanded = false },
                    ) {
                        groupOffsets.forEachIndexed { index, offset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        groupLabel(index),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Right,
                                    )
                                },
                                onClick = {
                                    onSequentialGroupSelected(prayer.key, offset)
                                    groupMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        if (preference.psalmMode == PsalmMode.FIXED) {
            Text("اختر المزامير الثابتة", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                psalms.forEach { psalm ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onFixedPsalmToggled(prayer.key, psalm.key)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = psalm.key in preference.fixedPsalmKeys,
                            onCheckedChange = { onFixedPsalmToggled(prayer.key, psalm.key) },
                        )
                        Text(psalm.title)
                    }
                }
            }
        }
    } else {
        Text("لا توجد مزامير مرتبطة بهذه الصلاة.")
    }
}
