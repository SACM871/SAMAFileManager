# SAMA File Manager — Android

نسخة أولية قابلة للبناء عبر GitHub Actions من الهاتف، دون الحاجة إلى كمبيوتر.

## الوظائف الحالية
- فتح ملفات JSON وZIP عبر منتقي ملفات Android (SAF).
- قراءة هيكل الملفات والمجلدات وتحليل الأحجام.
- تصنيف مجلدات الحسابات: كامل / مؤقت / غير محدد.
- اكتشاف مجموعات تشابه بالاسم والحجم (ليست بصمة محتوى مؤكدة).
- عرض اقتراحات المراجعة للحسابات المؤقتة فقط.
- إحصائيات أكبر الملفات والمجلدات وتوزيع الامتدادات.
- البحث داخل الهيكل.
- لا يوجد حذف تلقائي، ولا اتصال مباشر بأي خدمة سحابية.

## بناء APK من الهاتف باستخدام GitHub
1. أنشئ مستودعاً جديداً على GitHub من المتصفح أو التطبيق.
2. ارفع محتويات هذا المشروع إلى جذر المستودع (لا ترفع مجلد ZipStatsAndroid كطبقة إضافية).
3. افتح تبويب **Actions** في المستودع.
4. اختر **Build Android APK** ثم **Run workflow**، أو انتظر انتهاء البناء التلقائي بعد الرفع.
5. افتح آخر تشغيل ناجح.
6. من قسم **Artifacts** نزّل `SAMA-File-Manager-debug`.
7. فك ضغط الملف على هاتف Android وثبّت `app-debug.apk`.

## إعدادات البناء
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- Kotlin 2.0.21
- minSdk 26 (Android 8.0 أو أحدث)

## حدود النسخة الأولى
- المقارنة الحالية تعتمد بيانات الاسم والحجم، ولا تثبت تطابق محتوى الملفات.
- SHA-256 الحقيقي يتطلب الوصول إلى محتوى الملفات أو بصمة موثوقة من مزود التخزين.
- عمليات النقل والحذف والتعديل السحابي غير موجودة.
- لا تحفظ النسخة الحالية تغييرات على الملف الأصلي.
- حجم JSON المدعوم حالياً محدود بـ 256 MB، وفهرس ZIP محدود بـ 256 MB.

## السلامة
كل عمليات التحليل للقراءة فقط. اقتراحات التنظيف لا تنفذ حذفاً. لا تضع مفاتيح API أو كلمات مرور في مستودع GitHub.

## رفع المشروع من Termux (بدون كمبيوتر)

بعد تنزيل ملف ZIP إلى مجلد Download على الهاتف:

```bash
pkg update -y && pkg install git unzip -y
mkdir -p ~/SAMAFileManager
unzip /sdcard/Download/SAMA_File_Manager_GitHub_Project.zip -d ~/SAMAFileManager
cd ~/SAMAFileManager
git init -b main
git add .
git commit -m "Initial Android project"
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

استبدل `USERNAME/REPOSITORY` بمسار المستودع الذي أنشأته على GitHub. عند طلب كلمة المرور استخدم Personal Access Token من GitHub بدلاً من كلمة مرور الحساب. لا تشارك الرمز مع أي شخص ولا تضعه داخل ملفات المشروع. عند استخدام Fine-grained token امنح المستودع صلاحية Contents: Read and write وWorkflows: Read and write.
