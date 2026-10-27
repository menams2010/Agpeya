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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mina.agpeya.alarm.PrayerNotification
import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PrayerSection
import com.mina.agpeya.data.PrayerSpec
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
    val settings by viewModel.settings.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val psalmsByPrayer by viewModel.psalmsByPrayer.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var settingsPrayer by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPrayer by rememberSaveable { mutableStateOf(initialPrayerKey) }
    var pendingSoundPrayer by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sectionsDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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

    BackHandler(enabled = reading != null || selectedTab != 0) {
        viewModel.closeReading()
        selectedTab = 0
        settingsPrayer = null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PrayerNavigationDrawer(
                        prayerGroups = groups,
                        selectedPrayerKey = reading?.prayer?.key,
                        onOpenPrayer = { prayerKey ->
                            scope.launch { drawerState.close() }
                            viewModel.openPrayer(prayerKey)
                        },
                    )
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(reading?.prayer?.title ?: "الأجبية") },
                            navigationIcon = {
                                if (reading != null) {
                                    IconButton(onClick = { scope.launch { sectionsDrawerState.open() } }) {
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
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                                    onClick = { selectedTab = 0 },
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
                    when {
                        reading != null -> ReadingScreen(
                            reading!!,
                            sectionsDrawerState,
                            Modifier.padding(padding),
                        )
                        selectedTab == 0 -> HomeScreen(
                            onOpenPrayer = viewModel::openPrayer,
                            onPrayerSettings = { prayerKey ->
                                settingsPrayer = prayerKey
                                selectedTab = 1
                            },
                            modifier = Modifier.padding(padding),
                        )
                        else -> SettingsScreen(
                            settings = settings,
                            psalmsByPrayer = psalmsByPrayer,
                            onAlarmEnabled = viewModel::setAlarmEnabled,
                            onTimeChanged = viewModel::setTime,
                            onVolumeChanged = viewModel::setVolume,
                            onPickSound = { preference ->
                                pendingSoundPrayer = preference.key
                                ringtonePicker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
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
                            onFixedPsalmToggled = viewModel::toggleFixedPsalm,
                            onResetPsalms = viewModel::resetPsalms,
                            prayerKey = settingsPrayer,
                            modifier = Modifier.padding(padding),
                        )
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
    onAlarmEnabled: (String, Boolean) -> Unit,
    onTimeChanged: (String, String) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onPickSound: (PrayerPreference) -> Unit,
    onPsalmModeChanged: (String, PsalmMode) -> Unit,
    onPsalmCountChanged: (String, Int) -> Unit,
    onFixedPsalmToggled: (String, String) -> Unit,
    onResetPsalms: (String) -> Unit,
    prayerKey: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expandedPrayer by rememberSaveable(prayerKey) { mutableStateOf(prayerKey) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
            ReminderPermissionCard()
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
                onTimeChanged = onTimeChanged,
                onVolumeChanged = onVolumeChanged,
                onPickSound = { onPickSound(preference) },
                onPsalmModeChanged = onPsalmModeChanged,
                onPsalmCountChanged = onPsalmCountChanged,
                onFixedPsalmToggled = onFixedPsalmToggled,
                onResetPsalms = { onResetPsalms(preference.key) },
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
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المزامنة السحابية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "مزامنة Google Drive لم تُنفّذ بعد. يتطلب ربطها إعداد مشروع OAuth، " +
                            "معرّف عميل ويب، وتسجيل بصمة توقيع التطبيق في Google Cloud.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

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
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}"),
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
    onTimeChanged: (String, String) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onPickSound: () -> Unit,
    onPsalmModeChanged: (String, PsalmMode) -> Unit,
    onPsalmCountChanged: (String, Int) -> Unit,
    onFixedPsalmToggled: (String, String) -> Unit,
    onResetPsalms: () -> Unit,
    onPickTime: (String) -> Unit,
) {
    var sliderValue by remember(preference.volume) { mutableStateOf(preference.volume) }
    var countValue by remember(preference.psalmCount) { mutableStateOf(preference.psalmCount.toFloat()) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(prayer.title, style = MaterialTheme.typography.titleMedium)
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
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onVolumeChanged(prayer.key, sliderValue) },
                valueRange = 0f..1f,
            )
            TextButton(onClick = onPickSound) {
                Text(if (preference.soundUri == null) "اختيار صوت التنبيه (الافتراضي)" else "تغيير صوت التنبيه")
            }
            HorizontalDivider()
            TextButton(onClick = onExpanded) {
                Text(if (expanded) "إخفاء إعدادات المزامير" else "إعدادات المزامير")
            }
            if (expanded) {
                PsalmSettings(
                    prayer = prayer,
                    preference = preference,
                    psalms = psalms,
                    countValue = countValue,
                    onCountValueChanged = { countValue = it },
                    onCountChangeFinished = { onPsalmCountChanged(prayer.key, countValue.toInt()) },
                    onPsalmModeChanged = onPsalmModeChanged,
                    onFixedPsalmToggled = onFixedPsalmToggled,
                    onResetPsalms = onResetPsalms,
                )
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
            Text("عدد المزامير: ${countValue.toInt().coerceIn(1, psalms.size)}")
            Slider(
                value = countValue.coerceIn(1f, psalms.size.toFloat()),
                onValueChange = onCountValueChanged,
                onValueChangeFinished = onCountChangeFinished,
                valueRange = 1f..psalms.size.toFloat(),
                steps = (psalms.size - 2).coerceAtLeast(0),
            )
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
