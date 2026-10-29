package com.mina.agpeya

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.PrayerSection
import com.mina.agpeya.data.PrayerSpec
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    onOpenPrayer: (String) -> Unit,
    onPrayerSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "صلوات السواعي اليومية",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("جميع الصلوات متاحة أيضاً من القائمة الجانبية على اليسار.")
            }
        }
        items(PrayerCatalog.canonicalPrayers, key = PrayerSpec::key) { prayer ->
            Card(onClick = { onOpenPrayer(prayer.key) }) {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 20.dp)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painterResource(prayerImage(prayer.key)),
                            contentDescription = null,
                            modifier = Modifier.size(144.dp),
                        )
                        Text(
                            prayer.title,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    // End is the physical left in this RTL screen.
                    IconButton(
                        onClick = { onPrayerSettings(prayer.key) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = "إعدادات ${prayer.title}",
                        )
                    }
                }
            }
        }
    }
}

@DrawableRes
private fun prayerImage(prayerKey: String): Int = when (prayerKey) {
    "Prime" -> R.drawable.prayer_resurrection
    "Terce" -> R.drawable.prayer_pentecost
    "Sext" -> R.drawable.prayer_crucifixion
    "None" -> R.drawable.prayer_ninth_hour
    "Vespers" -> R.drawable.prayer_descent
    "Compline" -> R.drawable.prayer_entombment
    else -> R.drawable.prayer_midnight_icon
}

@Composable
internal fun PrayerNavigationDrawer(
    prayerGroups: List<String>,
    selectedPrayerKey: String?,
    onOpenPrayer: (String) -> Unit,
) {
    ModalDrawerSheet(Modifier.widthIn(max = 340.dp)) {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Text(
                    "الصلوات",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(PrayerCatalog.canonicalPrayers, key = PrayerSpec::key) { prayer ->
                NavigationDrawerItem(
                    label = { Text(prayer.title) },
                    selected = selectedPrayerKey == prayer.key,
                    onClick = { onOpenPrayer(prayer.key) },
                )
            }
            val additional = prayerGroups.filterNot(PrayerCatalog::isCanonical)
            if (additional.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("صلوات أخرى", modifier = Modifier.padding(16.dp))
                }
                items(additional, key = { it }) { prayerKey ->
                    NavigationDrawerItem(
                        label = { Text(PrayerCatalog.titleFor(prayerKey)) },
                        selected = selectedPrayerKey == prayerKey,
                        onClick = { onOpenPrayer(prayerKey) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReadingScreen(
    reading: PrayerReading,
    drawerState: DrawerState,
    paged: Boolean,
    onPagedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reading.sections.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا يوجد نص لهذه الصلاة في قاعدة البيانات")
        }
        return
    }
    key(reading.prayer.key) {
        PrayerReader(reading.sections, drawerState, paged, onPagedChanged, modifier)
    }
}

@Composable
private fun PrayerReader(
    sections: List<PrayerSection>,
    drawerState: DrawerState,
    paged: Boolean,
    onPagedChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { sections.size })
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier.fillMaxSize(),
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 340.dp)) {
                LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    item {
                        Text(
                            "أجزاء الصلاة",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    itemsIndexed(sections, key = { _, section -> section.id }) { index, section ->
                        val selected by remember(index, paged, pagerState, listState) {
                            derivedStateOf {
                                index == if (paged) {
                                    pagerState.currentPage
                                } else {
                                    listState.firstVisibleItemIndex
                                }
                            }
                        }
                        NavigationDrawerItem(
                            label = { Text(section.title) },
                            selected = selected,
                            onClick = {
                                scope.launch {
                                    if (paged) {
                                        pagerState.scrollToPage(index)
                                    } else {
                                        listState.scrollToItem(index)
                                    }
                                    drawerState.close()
                                }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !paged,
                    onClick = {
                        if (paged) {
                            scope.launch { listState.scrollToItem(pagerState.currentPage) }
                            onPagedChanged(false)
                        }
                    },
                    label = { Text("تمرير متصل") },
                )
                FilterChip(
                    selected = paged,
                    onClick = {
                        if (!paged) {
                            scope.launch { pagerState.scrollToPage(listState.firstVisibleItemIndex) }
                            onPagedChanged(true)
                        }
                    },
                    label = { Text("صفحة بصفحة") },
                )
            }
            if (paged) {
                HorizontalPager(
                    state = pagerState,
                    key = { sections[it].id },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PrayerSectionCard(sections[page])
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                    ) { Text("السابق") }
                    Text("${pagerState.currentPage + 1} / ${sections.size}")
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < sections.lastIndex,
                    ) { Text("التالي") }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sections, key = PrayerSection::id) { PrayerSectionCard(it) }
                }
            }
        }
    }
}

@Composable
private fun PrayerSectionCard(section: PrayerSection) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                section.content.replace("\\n", "\n"),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
        }
    }
}
