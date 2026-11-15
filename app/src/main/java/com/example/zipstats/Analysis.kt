package com.example.zipstats

import java.util.Locale

class AccStat(val node: Node, val kind: Int) {
    var bigFile: Node? = null
    var bigDir: Node? = null
    var dupCount = 0
    var suggestedCount = 0
    var suggestedSize = 0L
    var extensions: List<Pair<String, Int>> = emptyList()
}

class Analysis(private val root: Node) {
    val accounts = ArrayList<AccStat>()
    val duplicates = ArrayList<Dup>()
    val suggestions = ArrayList<Node>()
    val accountCounts = IntArray(4)
    val accountSizes = LongArray(4)
    var suggestedSize = 0L
    var topFiles: List<Node> = emptyList()
    var topDirs: List<Node> = emptyList()
    var typeSizes: List<Pair<String, Long>> = emptyList()

    private data class ScanResult(
        var largestFile: Node? = null,
        val extensionCounts: MutableMap<String, Int> = HashMap()
    )

    private fun hasAncestor(node: Node, candidates: Set<Node>): Boolean {
        var parent = node.parent
        while (parent != null) {
            if (parent in candidates) return true
            parent = parent.parent
        }
        return false
    }

    private fun walk(node: Node, files: MutableList<Node>, dirs: MutableList<Node>, accountNodes: MutableList<Node>) {
        for (child in node.kids) {
            if (child.isDir) {
                if (Acc.kind(child.name) != 0) accountNodes.add(child) else dirs.add(child)
                walk(child, files, dirs, accountNodes)
            } else {
                files.add(child)
            }
        }
    }

    private fun scan(node: Node, result: ScanResult) {
        for (child in node.kids) {
            if (child.isDir) {
                scan(child, result)
            } else {
                val current = result.largestFile
                if (current == null || child.size > current.size) result.largestFile = child
                val extension = child.type.ifBlank { "—" }.lowercase(Locale.ROOT)
                result.extensionCounts[extension] = (result.extensionCounts[extension] ?: 0) + 1
            }
        }
    }

    fun run() {
        // يجعل إعادة تشغيل التحليل آمنة ولا يكرر النتائج.
        accounts.clear()
        duplicates.clear()
        suggestions.clear()
        accountCounts.fill(0)
        accountSizes.fill(0L)
        suggestedSize = 0L
        topFiles = emptyList()
        topDirs = emptyList()
        typeSizes = emptyList()

        val allFiles = ArrayList<Node>()
        val allDirs = ArrayList<Node>()
        val accountNodes = ArrayList<Node>()
        walk(root, allFiles, allDirs, accountNodes)

        // تطابق الاسم والحجم = مرشح فقط، وليس إثباتاً لتطابق المحتوى.
        val fileGroups = allFiles
            .groupBy { it.name.lowercase(Locale.ROOT) to it.size }
            .values
            .filter { it.size >= 2 && it.none { node -> hasAncestor(node, emptySet()) } }

        for (group in fileGroups) {
            val safeGroup = group.filter { node ->
                val account = node.account()
                account == null || Acc.kind(account.name) != 0
            }
            if (safeGroup.size >= 2) {
                duplicates.add(Dup(false, safeGroup, "اسم الملف + الحجم", false))
            }
        }

        // مقارنة المجلدات بالاسم والحجم وعدد الملفات فقط؛ تظل نتائج احتمالية.
        val dirGroups = allDirs
            .filter { it.files > 0 }
            .groupBy { Triple(it.name.lowercase(Locale.ROOT), it.files, it.size) }
            .values
            .filter { it.size >= 2 }

        for (group in dirGroups) {
            duplicates.add(Dup(true, group, "اسم المجلد + الحجم + عدد الملفات", false))
        }

        duplicates.sortByDescending { it.wasted }

        // اقتراح العناصر الموجودة داخل الحسابات المؤقتة فقط.
        val selectedForSuggestion = LinkedHashSet<Node>()
        val orderedGroups = duplicates.sortedWith(
            compareBy<Dup>({ !it.isDir }, { -it.wasted })
        )

        for (group in orderedGroups) {
            val temporaryCopies = group.items.filter { node ->
                val account = node.account()
                account != null && Acc.kind(account.name) == 2
            }
            if (temporaryCopies.isEmpty()) continue

            val candidates = if (temporaryCopies.size < group.items.size) {
                temporaryCopies
            } else {
                temporaryCopies.drop(1)
            }

            for (candidate in candidates) {
                if (!hasAncestor(candidate, selectedForSuggestion)) {
                    selectedForSuggestion.add(candidate)
                    group.sugg.add(candidate)
                }
            }
        }

        // إزالة اقتراحات الملفات الواقعة داخل مجلد مقترح بالكامل.
        for (group in duplicates) {
            group.sugg.removeAll { candidate ->
                hasAncestor(candidate, selectedForSuggestion - candidate)
            }
        }
        for (group in duplicates) suggestions.addAll(group.sugg)

        val suggestionByAccount = suggestions.groupBy { it.account() }
        val duplicateCountByAccount = duplicates
            .flatMap { it.items }
            .groupingBy { it.account() }
            .eachCount()

        for (accountNode in accountNodes) {
            val kind = Acc.kind(accountNode.name)
            val stat = AccStat(accountNode, kind)
            val scan = ScanResult()
            scan(accountNode, scan)

            stat.bigFile = scan.largestFile
            stat.bigDir = accountNode.kids.filter { it.isDir }.maxByOrNull { it.size }
            stat.dupCount = duplicateCountByAccount[accountNode] ?: 0
            val accountSuggestions = suggestionByAccount[accountNode].orEmpty()
            stat.suggestedCount = accountSuggestions.size
            stat.suggestedSize = accountSuggestions.fold(0L) { sum, node ->
                Math.addExact(sum, node.size)
            }
            stat.extensions = scan.extensionCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }

            accounts.add(stat)
            accountCounts[kind]++
            accountSizes[kind] = Math.addExact(accountSizes[kind], accountNode.size)
        }

        suggestedSize = suggestions.fold(0L) { sum, node ->
            Math.addExact(sum, node.size)
        }
        topFiles = allFiles.sortedByDescending { it.size }.take(100)
        topDirs = allDirs.sortedByDescending { it.size }.take(50)

        typeSizes = allFiles
            .groupBy { it.type.ifBlank { "—" }.lowercase(Locale.ROOT) }
            .mapValues { (_, nodes) -> nodes.fold(0L) { sum, node -> Math.addExact(sum, node.size) } }
            .entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key to it.value }
    }
}
