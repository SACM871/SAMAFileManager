package com.example.zipstats

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                FileManagerApp()
            }
        }
    }
}

private enum class Screen(val title: String) {
    HOME("الرئيسية"),
    ACCOUNTS("الحسابات"),
    DUPLICATES("المكررات"),
    STATS("الإحصائيات"),
    SEARCH("البحث")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<Node?>(null) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedScreen by remember { mutableStateOf(Screen.HOME) }
    var searchText by remember { mutableStateOf("") }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                error = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        Reader(context.contentResolver).read(uri).also { recalc(it) }
                    }
                    val computed = withContext(Dispatchers.Default) {
                        Analysis(result).also { it.run() }
                    }
                    root = result
                    analysis = computed
                    sourceName = uri.lastPathSegment ?: "ملف مستورد"
                    selectedScreen = Screen.HOME
                } catch (t: Throwable) {
                    error = t.message ?: "تعذر قراءة الملف"
                } finally {
                    loading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SAMA File Manager", fontWeight = FontWeight.Bold)
                        Text("مدير الحسابات والملفات", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        openFile.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "*/*"))
                    }) { Text("فتح ملف") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(Screen.HOME, Screen.ACCOUNTS, Screen.DUPLICATES, Screen.STATS, Screen.SEARCH).forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Text(when (screen) {
                            Screen.HOME -> "⌂"
                            Screen.ACCOUNTS -> "▤"
                            Screen.DUPLICATES -> "⧉"
                            Screen.STATS -> "▥"
                            Screen.SEARCH -> "⌕"
                        }) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("جاري قراءة الملف وتحليله…", modifier = Modifier.padding(vertical = 12.dp))
            }
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            val currentRoot = root
            val currentAnalysis = analysis
            if (currentRoot == null || currentAnalysis == null) {
                EmptyState(
                    onOpen = {
                        openFile.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "*/*"))
                    }
                )
            } else {
                when (selectedScreen) {
                    Screen.HOME -> HomeScreen(sourceName, currentRoot, currentAnalysis)
                    Screen.ACCOUNTS -> AccountsScreen(currentAnalysis)
                    Screen.DUPLICATES -> DuplicatesScreen(currentAnalysis)
                    Screen.STATS -> StatsScreen(currentRoot, currentAnalysis)
                    Screen.SEARCH -> SearchScreen(currentRoot, searchText, { searchText = it })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📂", style = MaterialTheme.typography.displayLarge)
        Text("افتح ملف JSON أو ZIP للبدء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "يقرأ التطبيق هيكل الملفات ويحلل الحسابات والمكررات دون تعديل الملف الأصلي أو الاتصال بالسحابة.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Button(onClick = onOpen) { Text("اختيار ملف") }
        Text("يدعم ملفات JSON وZIP", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun HomeScreen(source: String, root: Node, analysis: Analysis) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Text("ملخص الفحص", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("الملفات", num(root.files.toLong()), Modifier.weight(1f))
                MetricCard("المجلدات", num(root.dirs.toLong()), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("الحجم الكلي", fmtSize(root.size), Modifier.weight(1f))
                MetricCard("الحسابات", num(analysis.accounts.size), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("نتائج المكررات", fontWeight = FontWeight.Bold)
                    Text("المجموعات: ${num(analysis.duplicates.size)}")
                    Text("المساحة المحتمل توفيرها: ${fmtSize(analysis.suggestedSize)}")
                    Text("هذه اقتراحات للمراجعة فقط؛ لا ينفذ التطبيق الحذف.", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item { AccountCounts(analysis) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountCounts(analysis: Analysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("تصنيف الحسابات", fontWeight = FontWeight.Bold)
            Text("كامل: ${analysis.accountCounts[1]}  •  مؤقت: ${analysis.accountCounts[2]}  •  غير محدد: ${analysis.accountCounts[3]}")
            Text("مساحة الحسابات المؤقتة: ${fmtSize(analysis.accountSizes[2])}")
        }
    }
}

@Composable
private fun AccountsScreen(analysis: Analysis) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item { Text("الحسابات (${analysis.accounts.size})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(analysis.accounts) { account ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(account.node.name, fontWeight = FontWeight.Bold)
                    Text(when (account.kind) {
                        1 -> "حساب كامل"
                        2 -> "حساب مؤقت"
                        3 -> "غير محدد"
                        else -> "غير معروف"
                    }, color = MaterialTheme.colorScheme.secondary)
                    Text("${num(account.node.files)} ملف • ${num(account.node.dirs)} مجلد • ${fmtSize(account.node.size)}")
                    Text("عناصر مكررة مرشحة: ${account.dupCount}")
                    Text("اقتراحات مراجعة: ${account.suggestedCount} • ${fmtSize(account.suggestedSize)}")
                    account.bigFile?.let { Text("أكبر ملف: ${it.name} (${fmtSize(it.size)})", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun DuplicatesScreen(analysis: Analysis) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Text("مجموعات المكررات (${analysis.duplicates.size})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "التطابق الحالي بالبيانات الوصفية وليس ببصمة المحتوى. راجع كل مجموعة قبل أي قرار.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (analysis.duplicates.isEmpty()) {
            item { Text("لا توجد مجموعات مكررة وفق معايير الفحص الحالية.") }
        }
        items(analysis.duplicates.take(500)) { duplicate ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (duplicate.isDir) "📁 مجموعة مجلدات" else "📄 ${duplicate.items.first().name}", fontWeight = FontWeight.Bold)
                    Text("المعيار: ${duplicate.matchReason}")
                    Text("عدد النسخ: ${duplicate.items.size} • الحجم المحتمل: ${fmtSize(duplicate.wasted)}")
                    duplicate.items.forEach { item ->
                        val account = item.account()?.name ?: "بدون حساب مصنف"
                        val suggested = item in duplicate.sugg
                        Text(
                            "${if (suggested) "⚠️ مرشح للمراجعة" else "• نسخة"} $account\n${item.path()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (duplicate.sugg.isEmpty()) {
                        Text("لا توجد نسخة مقترحة للمراجعة في هذه المجموعة.", color = MaterialTheme.colorScheme.secondary)
                    } else {
                        Text("لا يتم الحذف تلقائياً. هذه العناصر تحتاج موافقتك.", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsScreen(root: Node, analysis: Analysis) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item { Text("الإحصائيات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { MetricCard("الحجم الإجمالي", fmtSize(root.size), Modifier.fillMaxWidth()) }
        item { MetricCard("الملفات", num(root.files.toLong()), Modifier.fillMaxWidth()) }
        item { MetricCard("المجلدات", num(root.dirs.toLong()), Modifier.fillMaxWidth()) }
        item { Text("أكبر الملفات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(analysis.topFiles.take(30)) { file ->
            Text("${fmtSize(file.size)}  •  ${file.name}", modifier = Modifier.padding(vertical = 3.dp))
        }
        item { Divider() }
        item { Text("توزيع الأنواع حسب الحجم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(analysis.typeSizes) { (extension, size) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(extension)
                Text(fmtSize(size))
            }
        }
        item { Text("أكبر المجلدات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(analysis.topDirs.take(30)) { directory ->
            Text("${fmtSize(directory.size)}  •  ${directory.path()}", modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}

@Composable
private fun SearchScreen(root: Node, query: String, onQueryChange: (String) -> Unit) {
    val results = remember(root, query) {
        if (query.isBlank()) emptyList()
        else {
            val found = ArrayList<Node>()
            fun walk(node: Node) {
                node.kids.forEach { child ->
                    if (child.name.contains(query, ignoreCase = true)) found.add(child)
                    if (child.isDir) walk(child)
                }
            }
            walk(root)
            found.take(1000)
        }
    }

    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Text("البحث في الملفات والمجلدات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اسم الملف أو المجلد") },
            singleLine = true
        )
        Text("${results.size} نتيجة (حد العرض 1000)", style = MaterialTheme.typography.labelMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text(item.path(), style = MaterialTheme.typography.bodySmall)
                        Text(if (item.isDir) "مجلد • ${num(item.files)} ملف • ${fmtSize(item.size)}" else "ملف • ${fmtSize(item.size)}")
                    }
                }
            }
        }
    }
}
