package com.example.zipstats

import java.util.Locale

// 1 = كامل "SAMA SA 04" | 2 = مؤقت "SAMASA28" | 3 = غير محدد
object Acc {
    private val full = Regex("^SAMA\\s+SA\\s+\\d+$", RegexOption.IGNORE_CASE)
    private val temp = Regex("^SAMASA\\d+$", RegexOption.IGNORE_CASE)
    private val loose = Regex("^SAMA\\s*SA\\s*\\d+$", RegexOption.IGNORE_CASE)

    fun kind(name: String): Int {
        val value = name.trim()
        return when {
            full.matches(value) -> 1
            temp.matches(value) -> 2
            loose.matches(value) -> 3
            else -> 0
        }
    }
}

class Node(
    n0: String,
    val isDir: Boolean,
    var size: Long = 0,
    val type: String = ""
) {
    var name: String = n0.replace('/', '∕')
    var parent: Node? = null
        private set
    val kids = ArrayList<Node>()
    var files = 0
    var dirs = 0

    fun add(child: Node) {
        require(isDir) { "Cannot add a child to a file: ${path()}" }
        require(child !== this) { "A node cannot contain itself" }
        require(child.parent == null) { "Node already belongs to another parent: ${child.name}" }
        var ancestor: Node? = this
        while (ancestor != null) {
            require(ancestor !== child) { "Adding this node would create a cycle" }
            ancestor = ancestor.parent
        }
        require(kids.none { it.name.equals(child.name, ignoreCase = true) && it.isDir == child.isDir }) {
            "Duplicate child entry: ${child.name}"
        }
        child.parent = this
        kids.add(child)
    }

    fun path(): String {
        if (parent == null) return "/"
        val parts = ArrayList<String>()
        var current: Node? = this
        while (current != null && current.parent != null) {
            parts.add(current.name)
            current = current.parent
        }
        parts.reverse()
        return "/" + parts.joinToString("/")
    }

    fun account(): Node? {
        var current: Node? = this
        while (current != null) {
            if (current.isDir && Acc.kind(current.name) != 0) return current
            current = current.parent
        }
        return null
    }
}

class Dup(
    val isDir: Boolean,
    val items: List<Node>,
    val matchReason: String = "الاسم والحجم",
    val verified: Boolean = false
) {
    init {
        require(items.size >= 2)
    }

    val size: Long = items.first().size
    val wasted: Long
        get() = runCatching { Math.multiplyExact(size.coerceAtLeast(0), (items.size - 1).toLong()) }
            .getOrDefault(Long.MAX_VALUE)

    // لا يتم تنفيذ الحذف آلياً. هذه قائمة عناصر تحتاج مراجعة بشرية.
    val sugg = ArrayList<Node>()
}

fun recalc(node: Node) {
    if (!node.isDir) return
    var totalSize = 0L
    var fileCount = 0
    var dirCount = 0

    for (child in node.kids) {
        if (child.isDir) {
            recalc(child)
            totalSize = Math.addExact(totalSize, child.size)
            fileCount = Math.addExact(fileCount, child.files)
            dirCount = Math.addExact(dirCount, child.dirs + 1)
        } else {
            require(child.size >= 0) { "Negative file size at ${child.path()}" }
            totalSize = Math.addExact(totalSize, child.size)
            fileCount = Math.addExact(fileCount, 1)
        }
    }

    node.size = totalSize
    node.files = fileCount
    node.dirs = dirCount
}

fun extOf(name: String): String {
    val index = name.lastIndexOf('.')
    return if (index > 0 && index < name.length - 1) name.substring(index + 1).lowercase(Locale.ROOT) else ""
}

fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.2f %s", value, units[index])
}

fun num(value: Long): String = String.format(Locale.US, "%,d", value)
fun num(value: Int): String = num(value.toLong())

// ================= النصوص: عربي / English =================
object L {
    var lang = "ar"

    private val m: Map<String, List<String>> = mapOf(
        "app_sub" to listOf("مدير الحسابات والملفات", "Accounts & Files Manager"),
        "lang_btn" to listOf("EN", "عربي"),
        "sec_home" to listOf("🏠 الرئيسية", "🏠 Home"),
        "sec_files" to listOf("📂 الملفات", "📂 Files"),
        "sec_accounts" to listOf("🏷️ الحسابات", "🏷️ Accounts"),
        "sec_dups" to listOf("♊ المكرر", "♊ Duplicates"),
        "sec_stats" to listOf("📊 الإحصائيات", "📊 Statistics"),
        "sec_favs" to listOf("⭐ المفضلة", "⭐ Favorites"),
        "sec_search" to listOf("🔍 بحث", "🔍 Search"),
        "sec_tools" to listOf("⚙️ الأدوات", "⚙️ Tools"),
        "sub_0" to listOf("نظرة عامة سريعة على ملفك وحساباتك", "Quick overview of your file and accounts"),
        "sub_1" to listOf("", ""),
        "sub_2" to listOf("تفاصيل كل حساب: كامل أو مؤقت", "Details of every account: full or temporary"),
        "sub_3" to listOf("فحص الملفات والمجلدات المكررة واقتراح الحذف", "Find duplicate files/folders and get cleanup suggestions"),
        "sub_4" to listOf("الأكبر حجماً وتوزيع الأنواع", "Largest items and type breakdown"),
        "sub_5" to listOf("وصول سريع لمجلداتك المهمة", "Quick access to your important folders"),
        "sub_6" to listOf("ابحث بالاسم في كل الهيكل", "Search by name across the whole structure"),
        "sub_7" to listOf("فتح، حفظ، تقارير، اللغة", "Open, save, reports, language"),
        "open" to listOf("فتح", "Open"),
        "close" to listOf("إغلاق", "Close"),
        "cancel" to listOf("إلغاء", "Cancel"),
        "save" to listOf("حفظ", "Save"),
        "back" to listOf("رجوع", "Back"),
        "empty" to listOf("(فارغ)", "(empty)"),
        "busy" to listOf("انتظر انتهاء العملية الحالية", "Please wait for the current task"),
        "error" to listOf("خطأ", "Error"),
        "reading" to listOf("جاري القراءة والتحليل...", "Reading and analyzing..."),
        "analyzing" to listOf("جاري التحليل...", "Analyzing..."),
        "saving" to listOf("جاري حفظ الهيكل...", "Saving structure..."),
        "saving_report" to listOf("جاري إعداد التقرير...", "Preparing report..."),
        "deleting" to listOf("جاري الحذف...", "Deleting..."),
        "pasting" to listOf("جاري اللصق...", "Pasting..."),
        "hero_title" to listOf("افتح ملف للبدء", "Open a file to start"),
        "hero_sub" to listOf("terabox_structure.json أو ملف ZIP", "terabox_structure.json or a ZIP file"),
        "tip_title" to listOf("نصيحة", "Tip"),
        "tip_sub" to listOf("الملفات والمجلدات تُحلَّل بالكامل وتُعرض في الأقسام أعلاه.", "Everything is analyzed and shown in the sections above."),
        "loaded" to listOf("الملف المحمّل", "Loaded file"),
        "total_files" to listOf("إجمالي الملفات", "Total files"),
        "total_dirs" to listOf("إجمالي المجلدات", "Total folders"),
        "total_size" to listOf("الحجم الكلي", "Total size"),
        "kind_1" to listOf("✅ كامل", "✅ Full"),
        "kind_2" to listOf("🕒 مؤقت", "🕒 Temporary"),
        "kind_3" to listOf("❓ غير محدد", "❓ Unclassified"),
        "tag_1" to listOf("كامل", "Full"),
        "tag_2" to listOf("مؤقت", "Temp"),
        "tag_3" to listOf("؟", "?"),
        "accounts_word" to listOf("حسابات", "accounts"),
        "cleanup_sub" to listOf("يمكن تحريرها بحذف {0} عنصر مكرر من الحسابات المؤقتة", "Can be freed by removing {0} duplicate items from temporary accounts"),
        "cleanup_title" to listOf("مساحة قابلة للتوفير", "Recoverable space"),
        "review" to listOf("مراجعة", "Review"),
        "rep_title" to listOf("تصدير تقرير الحسابات", "Export accounts report"),
        "rep_sub" to listOf("ملف نصي فيه كل حساب والمقترح حذفه", "Text file with every account and suggested deletions"),
        "json_title" to listOf("حفظ الهيكل المعدّل (JSON)", "Save edited structure (JSON)"),
        "json_sub" to listOf("يحفظ تعديلاتك في ملف جديد", "Saves your edits to a new file"),
        "sort_row" to listOf("الترتيب", "Sort"),
        "sort_0" to listOf("بالاسم", "By name"),
        "sort_1" to listOf("بالحجم (الأكبر أولاً)", "By size (largest first)"),
        "sort_2" to listOf("بعدد الملفات", "By file count"),
        "dir_sub" to listOf("{0} ملف • {1}", "{0} files • {1}"),
        "no_acc" to listOf("ما لقيت حسابات", "No accounts found"),
        "no_acc_sub" to listOf("أسماء المجلدات المطلوبة: SAMA SA 04 أو SAMASA28", "Expected folder names: SAMA SA 04 or SAMASA28"),
        "acc_summary" to listOf("ملخص الحسابات", "Accounts summary"),
        "acc_l1" to listOf("📄 {0} ملف • 📁 {1} مجلد • 💾 {2}", "📄 {0} files • 📁 {1} folders • 💾 {2}"),
        "acc_big_file" to listOf("🔝 أكبر ملف: {0} ({1})", "🔝 Largest file: {0} ({1})"),
        "acc_big_dir" to listOf("🔝 أكبر مجلد: {0} ({1})", "🔝 Largest folder: {0} ({1})"),
        "acc_dups" to listOf("♊ عناصر مكررة: {0}", "♊ Duplicate items: {0}"),
        "acc_sug" to listOf("🗑️ مقترح حذفه: {0} عنصر ({1}) ← يتبقى {2}", "🗑️ Suggested removal: {0} items ({1}) → remaining {2}"),
        "sum_lines" to listOf("♊ مجموعات مكررة: {0}\n🗑️ مقترح حذفه من المؤقتة: {1} عنصر ({2})", "♊ Duplicate groups: {0}\n🗑️ Suggested removal from temp: {1} items ({2})"),
        "dup_header" to listOf("♊ مجموعات مكررة: {0} (📁 {1} | 📄 {2})", "♊ Duplicate groups: {0} (📁 {1} | 📄 {2})"),
        "dup_header_sub" to listOf("🗑️ مقترح حذفه من المؤقتة: {0} عنصر ({1})\n⚠️ المقارنة بالاسم + الحجم. الحسابات الكاملة لا يُقترح الحذف منها أبداً.", "🗑️ Suggested removal from temp: {0} items ({1})\n⚠️ Matching is by name + size. Full accounts are never suggested for deletion."),
        "apply_all" to listOf("طبّق كل الحذف المقترح", "Apply all suggested deletions"),
        "apply_all_sub" to listOf("{0} عنصر • {1}", "{0} items • {1}"),
        "per_copy" to listOf("{0} للنسخة • هدر {1}", "{0} per copy • wasted {1}"),
        "dup_more" to listOf("يظهر أكبر 400 مجموعة فقط (التقرير فيه كل المقترح)", "Showing top 400 groups only (the report has everything)"),
        "no_dups" to listOf("لا يوجد مكرر 🎉", "No duplicates 🎉"),
        "types_title" to listOf("توزيع الأنواع حسب الحجم", "Type breakdown by size"),
        "top_accs" to listOf("أكبر الحسابات", "Largest accounts"),
        "top_files" to listOf("أكبر {0} ملف", "Top {0} files"),
        "top_dirs" to listOf("أكبر {0} مجلد", "Top {0} folders"),
        "favs_title" to listOf("المفضلة ({0})", "Favorites ({0})"),
        "favs_hint" to listOf("اضغط مطولاً على أي عنصر في «الملفات» لإضافته", "Long-press any item in Files to add it"),
        "fav_missing" to listOf("غير موجود بالهيكل الحالي (اضغط لإزالته)", "Not in the current structure (tap to remove)"),
        "fav_remove_q" to listOf("إزالة من المفضلة؟", "Remove from favorites?"),
        "fav_add" to listOf("⭐ إضافة للمفضلة", "⭐ Add to favorites"),
        "fav_rem" to listOf("☆ إزالة من المفضلة", "☆ Remove from favorites"),
        "fav_added" to listOf("أُضيف للمفضلة ⭐", "Added to favorites ⭐"),
        "fav_removed" to listOf("أُزيل من المفضلة", "Removed from favorites"),
        "remove" to listOf("إزالة", "Remove"),
        "search_hint" to listOf("اكتب اسم ملف أو مجلد...", "Type a file or folder name..."),
        "search_btn" to listOf("بحث", "Search"),
        "search_empty" to listOf("اكتب كلمة واضغط بحث", "Type a word and press Search"),
        "search_res" to listOf("{0} نتيجة لـ «{1}»", "{0} results for “{1}”"),
        "search_none" to listOf("لا نتائج", "No results"),
        "m_details" to listOf("ℹ️ تفاصيل", "ℹ️ Details"),
        "m_rename" to listOf("✏️ إعادة تسمية", "✏️ Rename"),
        "m_copy" to listOf("📋 نسخ", "📋 Copy"),
        "m_cut" to listOf("✂️ نقل", "✂️ Move"),
        "m_delete" to listOf("🗑️ حذف", "🗑️ Delete"),
        "d_path" to listOf("المسار", "Path"),
        "d_type" to listOf("النوع", "Type"),
        "d_folder" to listOf("مجلد", "Folder"),
        "d_file" to listOf("ملف", "File"),
        "d_size" to listOf("الحجم", "Size"),
        "d_counts" to listOf("الملفات: {0} • المجلدات: {1}", "Files: {0} • Folders: {1}"),
        "d_ext" to listOf("الامتداد", "Extension"),
        "d_acc" to listOf("الحساب", "Account"),
        "rename_title" to listOf("إعادة تسمية", "Rename"),
        "invalid_name" to listOf("اسم غير صالح", "Invalid name"),
        "exists" to listOf("الاسم موجود مسبقاً", "Name already exists"),
        "del_title" to listOf("حذف", "Delete"),
        "del_q" to listOf("حذف «{0}» ({1}) من الهيكل؟", "Delete “{0}” ({1}) from the structure?"),
        "delete" to listOf("حذف", "Delete"),
        "apply_title" to listOf("تطبيق الحذف المقترح", "Apply suggested deletions"),
        "apply_msg" to listOf("سيُحذف {0} عنصر ({1}) من الحسابات المؤقتة فقط.\nالملف الأصلي لا يتغير إلا إذا حفظت JSON جديد.", "{0} items ({1}) will be removed from temporary accounts only.\nThe original file is unchanged unless you save a new JSON."),
        "apply" to listOf("طبّق", "Apply"),
        "copied" to listOf("تم النسخ — افتح المجلد الهدف في «الملفات» واضغط لصق", "Copied — open the target folder in Files and tap Paste"),
        "cut" to listOf("تم القص — افتح المجلد الهدف في «الملفات» واضغط لصق", "Cut — open the target folder in Files and tap Paste"),
        "paste_bar" to listOf("📋 الصق هنا ({0}): {1}", "📋 Paste here ({0}): {1}"),
        "w_copy" to listOf("نسخ", "copy"),
        "w_move" to listOf("نقل", "move"),
        "into_self" to listOf("لا يمكن نقل المجلد إلى داخل نفسه", "Cannot move a folder into itself"),
        "copy_w" to listOf("نسخة", "copy"),
        "saved_json" to listOf("تم حفظ الهيكل المعدّل ✅", "Edited structure saved ✅"),
        "saved_report" to listOf("تم حفظ التقرير ✅", "Report saved ✅"),
        "deleted" to listOf("تم الحذف من الهيكل ✅", "Deleted from the structure ✅"),
        "gone" to listOf("العنصر لم يعد موجوداً", "Item no longer exists"),
        "keep_mark" to listOf("✅ يُبقى: ", "✅ Keep: "),
        "del_mark" to listOf("🗑️ يُحذف: ", "🗑️ Delete: "),
        "and_more" to listOf("... و{0} أخرى", "... and {0} more"),
        "del_sugg_btn" to listOf("🗑️ احذف المقترح ({0})", "🗑️ Delete suggested ({0})"),
        "copies_n" to listOf("{0} للنسخة • {1} نسخ", "{0} per copy • {1} copies"),
        "tool_lang" to listOf("اللغة / Language", "Language / اللغة"),
        "tool_lang_sub" to listOf("العربية ⇄ English (الأرقام إنجليزية دائماً)", "English ⇄ العربية"),
        "tool_open_sub" to listOf("اختر JSON أو ZIP", "Pick a JSON or ZIP"),
        "tool_about" to listOf("حول التطبيق وملاحظات", "About & notes"),
        "tool_about_sub" to listOf("كيف يعمل وكيف تُحسب الأشياء", "How it works"),
        "about_msg" to listOf(
            "• التعديل والنقل والنسخ والحذف تتم على الهيكل المحمّل فقط، ولا تلمس السحابة ولا الملف الأصلي.\n• احفظ تعديلاتك بـ «حفظ JSON».\n• الحسابات الكاملة (SAMA SA 04) لا يُقترح الحذف منها أبداً.\n• المكرر = نفس الاسم + نفس الحجم (للمجلدات: + عدد الملفات).\n• الأرقام إنجليزية في كل الشاشات.",
            "• Edit/move/copy/delete work on the loaded structure only; the cloud and the original file are untouched.\n• Save your edits with “Save JSON”.\n• Full accounts (SAMA SA 04) are never suggested for deletion.\n• Duplicate = same name + same size (folders: + file count).\n• Numbers are always shown in English digits."
        )
    )

    fun t(k: String, vararg a: Any): String {
        val v = m[k] ?: return k
        var s = if (lang == "ar") v[0] else v[1]
        for ((i, x) in a.withIndex()) s = s.replace("{$i}", x.toString())
        return s
    }
}
