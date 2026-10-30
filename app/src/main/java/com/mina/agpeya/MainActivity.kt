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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val settings by viewModel.settings.collectAsState()
    val pagedReading by viewModel.pagedReading.collectAsState()
    val readerFontSettings by viewModel.readerFontSettings.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val psalmsByPrayer by viewModel.psalmsByPrayer.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsPrayer by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPrayer by rememberSaveable { mutableStateOf(initialPrayerKey) }
    var pendingSoundPrayer by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sectionsDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
                    containerColor = Color.Transparent,
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
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(R.drawable.background_photo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.3f,
                            modifier = Modifier.fillMaxSize(),
                        )
                        when {
                            reading != null -> ReadingScreen(
                                reading!!,
                                sectionsDrawerState,
                                pagedReading,
                                readerFontSettings,
                                viewModel::setPagedReading,
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
                                readerFontSettings = readerFontSettings,
                                onReaderFontScaleChanged = viewModel::setReaderFontScale,
                                onReaderFontFamilyChanged = viewModel::setReaderFontFamily,
                                onReaderFontStyleChanged = viewModel::setReaderFontStyle,
                                onReaderFontColorChanged = viewModel::setReaderFontColor,
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
                                prayerKey = settingsPrayer,
                                modifier = Modifier.padding(padding),
                            )
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
    onReaderFontScaleChanged: (Float) -> Unit,
    onReaderFontFamilyChanged: (ReaderFontFamily) -> Unit,
    onReaderFontStyleChanged: (ReaderFontStyle) -> Unit,
    onReaderFontColorChanged: (Int) -> Unit,
    onAlarmEnabled: (String, Boolean) -> Unit,
    onTimeChanged: (String, String) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onPickSound: (PrayerPreference) -> Unit,
    onPsalmModeChanged: (String, PsalmMode) -> Unit,
    onPsalmCountChanged: (String, Int) -> Unit,
    onSequentialGroupSelected: (String, Int) -> Unit,
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
            ReaderFontSettingsCard(
                settings = readerFontSettings,
                onScaleChanged = onReaderFontScaleChanged,
                onFamilyChanged = onReaderFontFamilyChanged,
                onStyleChanged = onReaderFontStyleChanged,
                onColorChanged = onReaderFontColorChanged,
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
                onVolumeChanged = onVolumeChanged,
                onPickSound = { onPickSound(preference) },
                onPsalmModeChanged = onPsalmModeChanged,
                onPsalmCountChanged = onPsalmCountChanged,
                onSequentialGroupSelected = onSequentialGroupSelected,
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
private fun ReaderFontSettingsCard(
    settings: ReaderFontSettings,
    onScaleChanged: (Float) -> Unit,
    onFamilyChanged: (ReaderFontFamily) -> Unit,
    onStyleChanged: (ReaderFontStyle) -> Unit,
    onColorChanged: (Int) -> Unit,
) {
    var scale by remember(settings.scale) { mutableFloatStateOf(settings.scale) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    val fontFamily = when (settings.family) {
        ReaderFontFamily.DEFAULT -> FontFamily.Default
        ReaderFontFamily.SERIF -> FontFamily.Serif
        ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
    }
    val fontWeight = when (settings.style) {
        ReaderFontStyle.REGULAR, ReaderFontStyle.ITALIC -> FontWeight.Normal
        ReaderFontStyle.BOLD, ReaderFontStyle.BOLD_ITALIC -> FontWeight.Bold
    }
    val fontStyle = when (settings.style) {
        ReaderFontStyle.REGULAR, ReaderFontStyle.BOLD -> FontStyle.Normal
        ReaderFontStyle.ITALIC, ReaderFontStyle.BOLD_ITALIC -> FontStyle.Italic
    }
    val selectedColor = Color(settings.color)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إعدادات خط القراءة", style = MaterialTheme.typography.titleMedium)
            Text("حجم الخط: ${(scale * 100).toInt()}٪")
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { onScaleChanged(scale) },
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
            Text(
                "مثال على شكل نص القراءة",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = selectedColor,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale,
                ),
            )
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
}

@Composable
private fun ReaderColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
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
        title = { Text("اختيار لون الخط") },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp)
                        .background(Color(pickerColor), CircleShape),
                )
                HueColorWheel(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onColorChanged = { newHue, newSaturation, newBrightness ->
                        hue = newHue
                        saturation = newSaturation
                        brightness = newBrightness
                        updateFromHsv()
                    },
                )
                Text("الشفافية")
                Slider(
                    value = alpha,
                    onValueChange = {
                        alpha = it
                        updateFromHsv()
                    },
                    valueRange = 0f..1f,
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
private fun HueColorWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onColorChanged: (Float, Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val updateAtPosition: (Offset, Float, Float) -> Unit = { position, width, height ->
        val center = Offset(width / 2f, height / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val radius = minOf(width, height) * 0.4f
        val ringWidth = minOf(width, height) * 0.085f
        val innerRadius = radius - ringWidth * 0.65f - 8.dp.value * density.density
        when {
            distance <= innerRadius -> onColorChanged(
                hue,
                ((dx / innerRadius + 1f) / 2f).coerceIn(0f, 1f),
                ((1f - dy / innerRadius) / 2f).coerceIn(0f, 1f),
            )
            distance in (radius - ringWidth / 2f)..(radius + ringWidth / 2f) -> {
                val angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble())
                onColorChanged(((angle + 360.0) % 360.0).toFloat(), saturation, brightness)
            }
        }
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
        val radius = minOf(size.width, size.height) * 0.4f
        val ringWidth = minOf(size.width, size.height) * 0.085f
        val innerRadius = radius - ringWidth * 0.65f - 8.dp.toPx()
        val hueColors = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red,
        )
        drawCircle(
            brush = Brush.sweepGradient(hueColors, center),
            radius = radius,
            center = center,
            style = Stroke(width = ringWidth),
        )

        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        val selectionPath = Path().apply {
            addOval(
                Rect(
                    center.x - innerRadius,
                    center.y - innerRadius,
                    center.x + innerRadius,
                    center.y + innerRadius,
                ),
            )
        }
        clipPath(selectionPath) {
            drawCircle(hueColor, innerRadius, center)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.White, Color.Transparent),
                    startX = center.x - innerRadius,
                    endX = center.x + innerRadius,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black),
                    startY = center.y - innerRadius,
                    endY = center.y + innerRadius,
                ),
            )
        }

        val hueAngle = Math.toRadians(hue.toDouble())
        val hueIndicator = Offset(
            center.x + kotlin.math.cos(hueAngle).toFloat() * radius,
            center.y + kotlin.math.sin(hueAngle).toFloat() * radius,
        )
        drawCircle(Color.White, ringWidth * 0.34f, hueIndicator)
        drawCircle(hueColor, ringWidth * 0.23f, hueIndicator)

        val valueIndicator = Offset(
            center.x + (saturation * 2f - 1f) * innerRadius,
            center.y + (1f - brightness * 2f) * innerRadius,
        )
        drawCircle(Color.White, 12.dp.toPx(), valueIndicator)
        drawCircle(Color.Black, 10.dp.toPx(), valueIndicator)
        }
    }
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
    onPickTime: (String) -> Unit,
) {
    var sliderValue by remember(preference.volume) { mutableFloatStateOf(preference.volume) }
    var countValue by remember(preference.psalmCount) { mutableFloatStateOf(preference.psalmCount.toFloat()) }
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
                    onSequentialGroupSelected = onSequentialGroupSelected,
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
