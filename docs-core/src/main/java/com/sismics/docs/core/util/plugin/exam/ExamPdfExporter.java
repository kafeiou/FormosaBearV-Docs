package com.sismics.docs.core.util.plugin.exam;

import com.google.common.base.Strings;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.PdfUtil;
import fr.opensagres.xdocreport.itext.extension.font.IFontProvider;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates localized blank exam PDFs (no watermark), completed exam PDFs (with exam-time watermark, no correct answers),
 * and landscape summary score report PDFs.
 */
public class ExamPdfExporter {
    private static final String[] LABEL_KEYS = {
            "exam_code", "version", "blank_paper", "name_line", "date_line",
            "score", "examinee", "exam_time", "author",
            "type_single", "type_multi", "type_tf",
            "correct", "incorrect", "true_opt", "false_opt",
            "summary_title", "date_range", "exam_filter", "all_exams",
            "exported_by", "exported_at", "total_attempts", "avg_score", "max_score", "min_score",
            "col_code_ver", "col_title", "col_owner", "col_examinee", "col_score", "col_time", "no_records"
    };

    private static Map<String, String> buildLabelMap(String... values) {
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < LABEL_KEYS.length && i < values.length; i++) {
            m.put(LABEL_KEYS[i], values[i]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    private static final Map<String, Map<String, String>> PDF_LABELS_I18N = Map.ofEntries(
            Map.entry("en", buildLabelMap(
                    "Exam Code", "Version", "Blank Exam Paper", "Name: _______________________", "Date: _______________________",
                    "Score", "Examinee", "Exam Time", "Author",
                    "[Single Choice]", "[Multiple Choice]", "[True / False]",
                    "   [✓ Correct]", "   [✗ Incorrect]", "True", "False",
                    "Online Exam Score Summary Report", "Date Range", "Exam Filter", "All Exams",
                    "Exported By", "Exported At", "Total Attempts", "Average Score", "Highest Score", "Lowest Score",
                    "Code / Version", "Exam Title", "Author / Group", "Examinee", "Score / Max", "Submitted Time",
                    "No exam score records found in this date range.")),
            Map.entry("zh_TW", buildLabelMap(
                    "考卷代號", "版號", "空白考卷", "姓名：_______________________", "日期：_______________________",
                    "分數", "應試人員", "應試時間", "出題來源",
                    "[單選題]", "[多選題]", "[是非題]",
                    "   [✓ 正確]", "   [✗ 錯誤]", "是 (True)", "否 (False)",
                    "線上考試成績總覽彙總表", "統計區間", "考卷篩選", "全部考卷",
                    "匯出人員", "匯出時間", "總應試人次", "平均分數", "最高分數", "最低分數",
                    "代號 / 版號", "考卷名稱", "出題老師 / 科別", "應試人員", "分數 / 滿分", "交卷時間",
                    "此時間區間內無符合條件之成績紀錄。")),
            Map.entry("zh_CN", buildLabelMap(
                    "考卷代号", "版号", "空白考卷", "姓名：_______________________", "日期：_______________________",
                    "分数", "应试人员", "应试时间", "出题来源",
                    "[单选题]", "[多选题]", "[判断题]",
                    "   [✓ 正确]", "   [✗ 错误]", "是 (True)", "否 (False)",
                    "在线考试成绩总览汇总表", "统计区间", "考卷筛选", "全部考卷",
                    "导出人员", "导出时间", "总应试人次", "平均分数", "最高分数", "最低分数",
                    "代号 / 版号", "考卷名称", "出题老师 / 科别", "应试人员", "分数 / 满分", "交卷时间",
                    "此时间区间内无符合条件的成绩记录。")),
            Map.entry("ja", buildLabelMap(
                    "試験コード", "版番", "白紙試験問題", "氏名：_______________________", "日付：_______________________",
                    "得点", "受験者", "受験日時", "作成者",
                    "[単一選択]", "[複数選択]", "[正誤問題]",
                    "   [✓ 正解]", "   [✗ 不正解]", "正しい (True)", "誤り (False)",
                    "オンライン試験 成績集計レポート", "集計期間", "試験フィルタ", "すべての試験",
                    "出力者", "出力日時", "総受験回数", "平均点", "最高点", "最低点",
                    "コード / 版", "試験名", "作成者 / グループ", "受験者", "得点 / 満点", "提出日時",
                    "指定期間内に該当する成績記録はありません。")),
            Map.entry("vi", buildLabelMap(
                    "Mã đề thi", "Phiên bản", "Đề thi trống", "Họ tên: _____________________", "Ngày: _______________________",
                    "Điểm số", "Thí sinh", "Thời gian thi", "Nguồn đề",
                    "[Một lựa chọn]", "[Nhiều lựa chọn]", "[Đúng / Sai]",
                    "   [✓ Đúng]", "   [✗ Sai]", "Đúng (True)", "Sai (False)",
                    "Báo cáo Tổng hợp Điểm Thi Trực tuyến", "Khoảng thời gian", "Bộ lọc đề thi", "Tất cả đề thi",
                    "Người xuất", "Thời gian xuất", "Tổng lượt thi", "Điểm trung bình", "Điểm cao nhất", "Điểm thấp nhất",
                    "Mã / Phiên bản", "Tên đề thi", "Giáo viên / Nhóm", "Thí sinh", "Điểm / Tối đa", "Thời gian nộp",
                    "Không có bản ghi điểm nào trong khoảng thời gian này.")),
            Map.entry("de", buildLabelMap(
                    "Prüfungscode", "Version", "Leerer Prüfungsbogen", "Name: _______________________", "Datum: ______________________",
                    "Punktzahl", "Prüfling", "Prüfungszeit", "Autor",
                    "[Einzelauswahl]", "[Mehrfachauswahl]", "[Wahr / Falsch]",
                    "   [✓ Richtig]", "   [✗ Falsch]", "Wahr", "Falsch",
                    "Online-Prüfung Ergebnisübersicht", "Zeitraum", "Prüfungsfilter", "Alle Prüfungen",
                    "Exportiert von", "Exportiert am", "Teilnahmen gesamt", "Durchschnitt", "Höchstpunktzahl", "Mindestpunktzahl",
                    "Code / Version", "Prüfungstitel", "Autor / Gruppe", "Prüfling", "Punkte / Max", "Abgabezeit",
                    "Keine Prüfungsergebnisse in diesem Zeitraum gefunden.")),
            Map.entry("fr", buildLabelMap(
                    "Code d'examen", "Version", "Sujet d'examen vierge", "Nom : _______________________", "Date : ______________________",
                    "Score", "Candidat", "Date de passage", "Auteur",
                    "[Choix unique]", "[Choix multiples]", "[Vrai / Faux]",
                    "   [✓ Correct]", "   [✗ Incorrect]", "Vrai", "Faux",
                    "Rapport récapitulatif des notes d'examen", "Période", "Filtre d'examen", "Tous les examens",
                    "Exporté par", "Exporté le", "Total des passages", "Score moyen", "Score max", "Score min",
                    "Code / Version", "Titre de l'examen", "Auteur / Groupe", "Candidat", "Score / Max", "Heure d'envoi",
                    "Aucun résultat d'examen trouvé pour cette période.")),
            Map.entry("es", buildLabelMap(
                    "Código de examen", "Versión", "Examen en blanco", "Nombre: _____________________", "Fecha: ______________________",
                    "Puntuación", "Examinado", "Fecha de examen", "Autor",
                    "[Opción única]", "[Opción múltiple]", "[Verdadero / Falso]",
                    "   [✓ Correcto]", "   [✗ Incorrecto]", "Verdadero", "Falso",
                    "Informe resumido de calificaciones de examen", "Rango de fechas", "Filtro de examen", "Todos los exámenes",
                    "Exportado por", "Exportado el", "Total de intentos", "Promedio", "Nota máxima", "Nota mínima",
                    "Código / Versión", "Título del examen", "Autor / Grupo", "Examinado", "Puntos / Máx", "Hora de entrega",
                    "No se encontraron registros de calificaciones en este rango de fechas.")),
            Map.entry("pt", buildLabelMap(
                    "Código do exame", "Versão", "Exame em branco", "Nome: _______________________", "Data: _______________________",
                    "Pontuação", "Examinando", "Data do exame", "Autor",
                    "[Escolha única]", "[Escolha múltipla]", "[Verdadeiro / Falso]",
                    "   [✓ Correto]", "   [✗ Incorreto]", "Verdadeiro", "Falso",
                    "Relatório Resumo de Classificações de Exame", "Período", "Filtro de exame", "Todos os exames",
                    "Exportado por", "Exportado em", "Total de provas", "Média", "Nota máxima", "Nota mínima",
                    "Código / Versão", "Título do exame", "Autor / Grupo", "Examinando", "Pontos / Máx", "Hora de submissão",
                    "Não foram encontrados registos de classificações neste período.")),
            Map.entry("pt_BR", buildLabelMap(
                    "Código da prova", "Versão", "Prova em branco", "Nome: _______________________", "Data: _______________________",
                    "Pontuação", "Candidato", "Data da prova", "Autor",
                    "[Escolha única]", "[Múltipla escolha]", "[Verdadeiro / Falso]",
                    "   [✓ Correto]", "   [✗ Incorreto]", "Verdadeiro", "Falso",
                    "Relatório Resumo de Notas da Prova Online", "Período", "Filtro de prova", "Todas as provas",
                    "Exportado por", "Exportado em", "Total de tentativas", "Média", "Nota máxima", "Nota mínima",
                    "Código / Versão", "Título da prova", "Autor / Grupo", "Candidato", "Nota / Máx", "Horário de envio",
                    "Nenhum registro de nota encontrado neste período.")),
            Map.entry("it", buildLabelMap(
                    "Codice esame", "Versione", "Prova d'esame in bianco", "Nome: _______________________", "Data: _______________________",
                    "Punteggio", "Esaminando", "Data esame", "Autore",
                    "[Scelta singola]", "[Scelta multipla]", "[Vero / Falso]",
                    "   [✓ Corretto]", "   [✗ Errato]", "Vero", "Falso",
                    "Riepilogo dei punteggi degli esami online", "Intervallo date", "Filtro esame", "Tutti gli esami",
                    "Esportato da", "Esportato il", "Totale prove", "Punteggio medio", "Punteggio max", "Punteggio min",
                    "Codice / Versione", "Titolo esame", "Autore / Gruppo", "Esaminando", "Punti / Max", "Data invio",
                    "Nessun risultato trovato nell'intervallo di date selezionato.")),
            Map.entry("pl", buildLabelMap(
                    "Kod egzaminu", "Wersja", "Pusty arkusz egzaminacyjny", "Imię i nazwisko: ____________", "Data: _______________________",
                    "Wynik", "Zdający", "Czas egzaminu", "Autor",
                    "[Jednokrotny wybór]", "[Wielokrotny wybór]", "[Prawda / Fałsz]",
                    "   [✓ Poprawnie]", "   [✗ Błędnie]", "Prawda", "Fałsz",
                    "Raport zbiorczy wyników egzaminów online", "Zakres dat", "Filtr egzaminu", "Wszystkie egzaminy",
                    "Wyeksportowano przez", "Data eksportu", "Liczba podejść", "Średni wynik", "Najwyższy wynik", "Najniższy wynik",
                    "Kod / Wersja", "Tytuł egzaminu", "Autor / Grupa", "Zdający", "Wynik / Maks.", "Czas przesłania",
                    "Brak wyników egzaminów w wybranym zakresie dat.")),
            Map.entry("ru", buildLabelMap(
                    "Код экзамена", "Версия", "Чистый бланк экзамена", "ФИО: ________________________", "Дата: _______________________",
                    "Баллы", "Экзаменуемый", "Время экзамена", "Автор",
                    "[Один вариант]", "[Несколько вариантов]", "[Верно / Неверно]",
                    "   [✓ Верно]", "   [✗ Неверно]", "Верно", "Неверно",
                    "Сводный отчет по результатам онлайн-экзаменов", "Период", "Фильтр экзамена", "Все экзамены",
                    "Экспортировал", "Дата экспорта", "Всего попыток", "Средний балл", "Макс. балл", "Мин. балл",
                    "Код / Версия", "Название экзамена", "Автор / Группа", "Экзаменуемый", "Баллы / Макс.", "Время сдачи",
                    "За выбранный период записей о результатах не найдено.")),
            Map.entry("el", buildLabelMap(
                    "Κωδικός εξέτασης", "Έκδοση", "Κενό φύλλο εξέτασης", "Ονοματεπώνυμο: ______________", "Ημερομηνία: _________________",
                    "Βαθμολογία", "Εξεταζόμενος", "Ώρα εξέτασης", "Συντάκτης",
                    "[Μονή επιλογή]", "[Πολλαπλή επιλογή]", "[Σωστό / Λάθος]",
                    "   [✓ Σωστό]", "   [✗ Λάθος]", "Σωστό", "Λάθος",
                    "Συγκεντρωτική Αναφορά Βαθμολογιών Εξέτασης", "Περίοδος", "Φίλτρο εξέτασης", "Όλες οι εξετάσεις",
                    "Εξαγωγή από", "Ημερομηνία εξαγωγής", "Σύνολο προσπαθειών", "Μέσος όρος", "Μέγιστη βαθμολογία", "Ελάχιστη βαθμολογία",
                    "Κωδικός / Έκδοση", "Τίτλος εξέτασης", "Συντάκτης / Ομάδα", "Εξεταζόμενος", "Βαθμός / Μέγ.", "Ώρα υποβολής",
                    "Δεν βρέθηκαν εγγραφές βαθμολογίας σε αυτό το διάστημα.")),
            Map.entry("sq_AL", buildLabelMap(
                    "Kodi i provimit", "Versioni", "Fletë provimi e zbrazët", "Emri: _______________________", "Data: _______________________",
                    "Pikët", "Kandidati", "Koha e provimit", "Autori",
                    "[Zgjedhje e vetme]", "[Zgjedhje e shumëfishtë]", "[E vërtetë / E gabuar]",
                    "   [✓ Saktë]", "   [✗ Gabim]", "E vërtetë", "E gabuar",
                    "Raporti Përmbledhës i Rezultateve të Provimit", "Periudha", "Filtri i provimit", "Të gjitha provimet",
                    "Eksportuar nga", "Eksportuar më", "Gjithsej tentativa", "Mesatarja", "Rezultati maksimal", "Rezultati minimal",
                    "Kodi / Versioni", "Titulli i provimit", "Autori / Grupi", "Kandidati", "Pikët / Maks", "Koha e dorëzimit",
                    "Nuk u gjetën rezultate provimi në këtë periudhë.")),
            Map.entry("sr", buildLabelMap(
                    "Шифра испита", "Верзија", "Празан испитни лист", "Име и презиме: ______________", "Датум: ______________________",
                    "Бодови", "Кандидат", "Време испита", "Аутор",
                    "[Један одговор]", "[Више одговора]", "[Тачно / Нетачно]",
                    "   [✓ Тачно]", "   [✗ Нетачно]", "Тачно", "Нетачно",
                    "Збирни извештај о резултатима онлајн испита", "Период", "Филтер испита", "Сви испити",
                    "Извезао", "Време извоза", "Укупно покушаја", "Просечан број бодова", "Највише бодова", "Најмање бодова",
                    "Шифра / Верзија", "Назив испита", "Аутор / Група", "Кандидат", "Бодови / Макс.", "Време предаје",
                    "Нема пронађених резултата испита у овом периоду."))
    );

    private static String msg(String lang, String key) {
        Map<String, String> enMap = PDF_LABELS_I18N.get("en");
        if (Strings.isNullOrEmpty(lang)) {
            return enMap.getOrDefault(key, key);
        }
        String clean = lang.trim();
        Map<String, String> dict = PDF_LABELS_I18N.get(clean);
        if (dict == null && clean.contains("-")) {
            dict = PDF_LABELS_I18N.get(clean.replace('-', '_'));
        }
        if (dict == null && clean.contains("_")) {
            dict = PDF_LABELS_I18N.get(clean.split("_")[0]);
        }
        if (dict == null && clean.toLowerCase(Locale.ROOT).startsWith("zh")) {
            dict = PDF_LABELS_I18N.get("zh_TW");
        }
        if (dict == null) {
            dict = enMap;
        }
        return dict.getOrDefault(key, enMap.getOrDefault(key, key));
    }

    public static byte[] exportBlankPdf(JsonObject exam) throws Exception {
        return exportBlankPdf(exam, "en");
    }

    public static byte[] exportBlankPdf(JsonObject exam, String lang) throws Exception {
        return renderPdf(exam, null, lang);
    }

    public static byte[] exportAttemptPdf(JsonObject exam, JsonObject attempt) throws Exception {
        return exportAttemptPdf(exam, attempt, "en");
    }

    public static byte[] exportAttemptPdf(JsonObject exam, JsonObject attempt, String lang) throws Exception {
        return renderPdf(exam, attempt, lang);
    }

    public static final List<String> SUPPORTED_TEXTBOOK_LOCALES = List.of(
            "en", "zh_TW", "zh_CN", "ja", "vi", "de", "fr", "es",
            "pt", "pt_BR", "it", "pl", "ru", "el", "sq_AL", "sr"
    );

    private static final class TextbookContent {
        final String fileName;
        final String title;
        final String intro;
        final String[][] sections;

        TextbookContent(String fileName, String title, String intro, String[][] sections) {
            this.fileName = fileName;
            this.title = title;
            this.intro = intro;
            this.sections = sections;
        }
    }

    private static final Map<String, TextbookContent> TEXTBOOK_I18N = Map.ofEntries(
            Map.entry("en", new TextbookContent(
                    "Security Fundamentals Guide (English).pdf",
                    "Security Fundamentals Guide",
                    "This reference guide accompanies the Security Fundamentals Assessment (SEC-001). Examinees may open this document in a new tab during the exam.",
                    new String[][]{
                            {"Chapter 1: Account & Password Security", "• Enable Multi-Factor Authentication (MFA) and use strong, unique passwords.\n• Never reuse the same password across systems, write passwords on sticky notes, or disable account lockout protections."},
                            {"Chapter 2: Phishing & Social Engineering Defense", "• Carefully inspect sender domains and hyperlink URLs for spoofing before clicking.\n• Never open untrusted email attachments or reply with passwords or verification codes."},
                            {"Chapter 3: 3-2-1 Backup Rule & Encryption", "• The 3-2-1 backup rule requires keeping 3 copies of data on 2 different storage media with at least 1 copy stored offsite or offline.\n• Protect sensitive files with AES-256 disk encryption and regular recovery drills."},
                            {"Chapter 4: Principle of Least Privilege", "• Grant each user or service only the minimum permissions required for its role (e.g., examinees receive READ-only access to reference materials).\n• Never assign top Administrator (Admin) privileges by default for convenience."}
                    })),
            Map.entry("zh_TW", new TextbookContent(
                    "資安基礎講義（繁體中文）.pdf",
                    "資安基礎講義",
                    "本講義對應「SEC-001 資安基礎測驗（Security Fundamentals Assessment）」，應試人員可於作答畫面點擊上方參考文件連結開啟本教材對照閱讀。",
                    new String[][]{
                            {"第一章：密碼與帳號安全", "• 強化帳號安全的最佳實務為：啟用多因素驗證（MFA）並使用高強度且不重複的密碼。\n• 嚴禁於多個系統共用同一組密碼、將密碼寫在螢幕便利貼上，或停用登入失敗鎖定機制。"},
                            {"第二章：防範網路釣魚與社交工程", "• 收信時應仔細檢查寄件者網域與超連結網址是否遭偽造。\n• 切勿隨意開啟來源不明的郵件附加檔案，亦不可在電子郵件中直接回覆密碼或驗證碼。"},
                            {"第三章：3-2-1 備份原則與加密保護", "• 「3-2-1 備份原則」定義為：至少保存 3 份資料、使用 2 種不同儲存媒介，且至少 1 份存放於異地或離線環境。\n• 機敏文件應搭配 AES-256 磁碟加密與定期還原演練。"},
                            {"第四章：最小權限原則（Least Privilege）", "• 「最小權限原則」要求每個帳號僅能取得完成職務所需的最低權限（例如應試人員僅具教材讀取 READ 權限）。\n• 嚴禁為求方便而預設給予所有員工最高系統管理員（Admin）權限。"}
                    })),
            Map.entry("zh_CN", new TextbookContent(
                    "资安基础讲义（简体中文）.pdf",
                    "资安基础讲义",
                    "本讲义对应“SEC-001 资安基础测验（Security Fundamentals Assessment）”，考生可在作答页面点击上方参考文件链接打开本教材对照阅读。",
                    new String[][]{
                            {"第一章：密码与账号安全", "• 强化账号安全的最佳实践为：启用多因素验证（MFA）并使用高强度且不重复的密码。\n• 严禁在多个系统共用同一组密码、将密码写在便利贴上，或停用登录失败锁定机制。"},
                            {"第二章：防范网络钓鱼与社会工程", "• 收信时应仔细检查发件人域名与超链接网址是否伪造。\n• 切勿随意打开来源不明的邮件附件，也不可在邮件中直接回复密码或验证码。"},
                            {"第三章：3-2-1 备份原则与加密保护", "• “3-2-1 备份原则”定义为：至少保存 3 份数据、使用 2 种不同存储介质，且至少 1 份存放于异地或离线环境。\n• 敏感文件应配合 AES-256 磁盘加密与定期恢复演练。"},
                            {"第四章：最小权限原则（Least Privilege）", "• “最小权限原则”要求每个账号仅获取完成工作所需的最低权限（例如考生仅具教材读取 READ 权限）。\n• 严禁为图方便而默认赋予所有员工最高系统管理员（Admin）权限。"}
                    })),
            Map.entry("ja", new TextbookContent(
                    "情報セキュリティ基礎ガイド（日本語）.pdf",
                    "情報セキュリティ基礎ガイド",
                    "本ガイドは「SEC-001 情報セキュリティ基礎試験」の参考資料です。受験中に上部の参考ドキュメントリンクから新しいタブで開いて参照できます。",
                    new String[][]{
                            {"第1章：アカウントとパスワードのセキュリティ", "• 多要素認証（MFA）を有効にし、強力で固有のパスワードを使用してください。\n• 複数システムでのパスワード使い回し、付箋へのメモ、ロックアウト機能の無効化は厳禁です。"},
                            {"第2章：フィッシングとソーシャルエンジニアリング対策", "• 送信元ドメインやリンク先URLが偽装されていないか注意深く確認してください。\n• 不審な添付ファイルを開いたり、メールでパスワードを返信したりしないでください。"},
                            {"第3章：3-2-1 バックアップ原則と暗号化", "• 「3-2-1 バックアップ原則」とは、3つのデータコピーを2種類の異なる媒体に保存し、そのうち1つをオフサイトまたはオフラインに保管することです。\n• 機密ファイルは AES-256 暗号化で保護します。"},
                            {"第4章：最小権限の原則（Least Privilege）", "• 各ユーザーには業務に必要な最小限の権限（例：閲覧のみ READ）のみを付与します。\n• 利便性のために全従業員へデフォルトで管理者（Admin）権限を付与してはなりません。"}
                    })),
            Map.entry("vi", new TextbookContent(
                    "Hướng dẫn Cơ bản về An ninh Mạng (Tiếng Việt).pdf",
                    "Hướng dẫn Cơ bản về An ninh Mạng",
                    "Tài liệu tham khảo này đi kèm với bài kiểm tra SEC-001. Thí sinh có thể mở tài liệu này trong tab mới khi làm bài.",
                    new String[][]{
                            {"Chương 1: Bảo mật Tài khoản & Mật khẩu", "• Bật Xác thực Đa yếu tố (MFA) và sử dụng mật khẩu mạnh, duy nhất.\n• Không dùng chung một mật khẩu cho nhiều hệ thống hoặc ghi mật khẩu lên giấy nhớ."},
                            {"Chương 2: Phòng chống Lừa đảo (Phishing)", "• Kiểm tra kỹ tên miền người gửi và đường dẫn URL trước khi nhấp.\n• Không mở tệp đính kèm không rõ nguồn gốc hoặc trả lời mật khẩu qua email."},
                            {"Chương 3: Quy tắc Sao lưu 3-2-1 & Mã hóa", "• Quy tắc sao lưu 3-2-1 gồm: lưu giữ 3 bản sao dữ liệu trên 2 loại phương tiện lưu trữ khác nhau và ít nhất 1 bản ở ngoài cơ sở hoặc ngoại tuyến.\n• Bảo vệ tệp nhạy cảm bằng mã hóa AES-256."},
                            {"Chương 4: Nguyên tắc Đặc quyền Tối thiểu (Least Privilege)", "• Chỉ cấp quyền tối thiểu cần thiết cho công việc (ví dụ: quyền ĐỌC cho thí sinh).\n• Không bao giờ cấp quyền Quản trị viên (Admin) mặc định cho tất cả nhân viên."}
                    })),
            Map.entry("de", new TextbookContent(
                    "Leitfaden IT-Sicherheitsgrundlagen (Deutsch).pdf",
                    "Leitfaden IT-Sicherheitsgrundlagen",
                    "Dieser Leitfaden dient als Referenzdokument für die Prüfung SEC-001 und kann während der Prüfung in einem neuen Tab geöffnet werden.",
                    new String[][]{
                            {"Kapitel 1: Konto- und Passwortsicherheit", "• Aktivieren Sie die Multi-Faktor-Authentifizierung (MFA) und verwenden Sie starke, einzigartige Passwörter.\n• Verwenden Sie Passwörter niemals mehrfach und notieren Sie sie nicht auf Haftnotizen."},
                            {"Kapitel 2: Schutz vor Phishing & Social Engineering", "• Prüfen Sie Absenderdomänen und URLs sorgfältig auf Manipulationen.\n• Öffnen Sie keine unbekannten Anhänge und senden Sie niemals Passwörter per E-Mail."},
                            {"Kapitel 3: 3-2-1-Backup-Regel & Verschlüsselung", "• Die 3-2-1-Regel besagt: 3 Datenkopien auf 2 verschiedenen Medien, davon mindestens 1 Kopie extern oder offline.\n• Schützen Sie vertrauliche Dateien mit AES-256-Verschlüsselung."},
                            {"Kapitel 4: Prinzip der geringsten Rechte (Least Privilege)", "• Gewähren Sie nur die minimal erforderlichen Berechtigungen (z. B. Lesezugriff READ).\n• Vergeben Sie niemals standardmäßig Administratorrechte (Admin) an alle Mitarbeitenden."}
                    })),
            Map.entry("fr", new TextbookContent(
                    "Guide des Fondamentaux de la Sécurité (Français).pdf",
                    "Guide des Fondamentaux de la Sécurité",
                    "Ce guide de référence accompagne l'examen SEC-001. Les candidats peuvent l'ouvrir dans un nouvel onglet pendant l'épreuve.",
                    new String[][]{
                            {"Chapitre 1 : Sécurité des comptes et mots de passe", "• Activez l'authentification multifacteur (MFA) et utilisez des mots de passe robustes et uniques.\n• Ne réutilisez jamais le même mot de passe et ne l'écrivez pas sur un post-it."},
                            {"Chapitre 2 : Défense contre l'hameçonnage (Phishing)", "• Vérifiez attentivement le domaine de l'expéditeur et les liens URL.\n• N'ouvrez jamais de pièces jointes suspectes et ne communiquez jamais votre mot de passe par courriel."},
                            {"Chapitre 3 : Règle de sauvegarde 3-2-1 et chiffrement", "• La règle 3-2-1 consiste à conserver 3 copies des données sur 2 supports différents, dont 1 copie hors site ou hors ligne.\n• Chiffrez les fichiers sensibles avec AES-256."},
                            {"Chapitre 4 : Principe du moindre privilège (Least Privilege)", "• Accordez uniquement les droits strictement nécessaires (ex. lecture READ).\n• N'attribuez jamais les droits Administrateur (Admin) par défaut à tous les employés."}
                    })),
            Map.entry("es", new TextbookContent(
                    "Guía de Fundamentos de Seguridad (Español).pdf",
                    "Guía de Fundamentos de Seguridad",
                    "Esta guía de referencia acompaña la evaluación SEC-001. Los examinados pueden abrirla en una pestaña nueva durante el examen.",
                    new String[][]{
                            {"Capítulo 1: Seguridad de cuentas y contraseñas", "• Active la autenticación multifactor (MFA) y utilice contraseñas seguras y únicas.\n• Nunca reutilice la misma contraseña ni la anote en notas adhesivas."},
                            {"Capítulo 2: Defensa contra Phishing e ingeniería social", "• Verifique cuidadosamente el dominio del remitente y las direcciones URL.\n• No abra archivos adjuntos desconocidos ni envíe contraseñas por correo electrónico."},
                            {"Capítulo 3: Regla de respaldo 3-2-1 y cifrado", "• La regla 3-2-1 exige mantener 3 copias de los datos en 2 medios distintos y al menos 1 copia fuera del sitio o sin conexión.\n• Proteja los archivos confidenciales con cifrado AES-256."},
                            {"Capítulo 4: Principio de mínimo privilegio (Least Privilege)", "• Otorgue solo los permisos mínimos necesarios (por ejemplo, permiso de lectura READ).\n• Nunca asigne permisos de Administrador (Admin) por defecto a todos los empleados."}
                    })),
            Map.entry("pt", new TextbookContent(
                    "Guia de Fundamentos de Segurança (Português).pdf",
                    "Guia de Fundamentos de Segurança",
                    "Este guia de referência acompanha a avaliação SEC-001. Os examinandos podem abri-lo num novo separador durante o exame.",
                    new String[][]{
                            {"Capítulo 1: Segurança de Contas e Palavras-passe", "• Ative a autenticação multifator (MFA) e utilize palavras-passe fortes e únicas.\n• Nunca reutilize a mesma palavra-passe em vários sistemas."},
                            {"Capítulo 2: Defesa contra Phishing e Engenharia Social", "• Verifique cuidadosamente o domínio do remetente e os endereços URL.\n• Não abra anexos de origem desconhecida nem envie palavras-passe por e-mail."},
                            {"Capítulo 3: Regra de Cópia de Segurança 3-2-1 e Encriptação", "• A regra 3-2-1 exige 3 cópias dos dados em 2 suportes diferentes e pelo menos 1 cópia fora do local ou offline.\n• Proteja ficheiros sensíveis com encriptação AES-256."},
                            {"Capítulo 4: Princípio do Menor Privilégio (Least Privilege)", "• Conceda apenas as permissões mínimas necessárias (ex.: leitura READ).\n• Nunca atribua permissões de Administrador (Admin) por predefinição a todos os colaboradores."}
                    })),
            Map.entry("pt_BR", new TextbookContent(
                    "Guia de Fundamentos de Segurança (Português do Brasil).pdf",
                    "Guia de Fundamentos de Segurança",
                    "Este guia de referência acompanha a avaliação SEC-001. Os candidatos podem abri-lo em uma nova aba durante a prova.",
                    new String[][]{
                            {"Capítulo 1: Segurança de Contas e Senhas", "• Ative a autenticação multifator (MFA) e use senhas fortes e exclusivas.\n• Nunca reutilize a mesma senha em vários sistemas nem a anote em adesivos."},
                            {"Capítulo 2: Defesa contra Phishing e Engenharia Social", "• Verifique cuidadosamente o domínio do remetente e os links URL.\n• Não abra anexos desconhecidos nem responda e-mails informando sua senha."},
                            {"Capítulo 3: Regra de Backup 3-2-1 e Criptografia", "• A regra 3-2-1 exige manter 3 cópias dos dados em 2 mídias diferentes e pelo menos 1 cópia externa ou offline.\n• Proteja arquivos confidenciais com criptografia AES-256."},
                            {"Capítulo 4: Princípio do Menor Privilégio (Least Privilege)", "• Conceda apenas as permissões mínimas necessárias (ex.: leitura READ).\n• Nunca atribua privilégios de Administrador (Admin) por padrão a todos os funcionários."}
                    })),
            Map.entry("it", new TextbookContent(
                    "Guida ai Fondamenti di Sicurezza (Italiano).pdf",
                    "Guida ai Fondamenti di Sicurezza",
                    "Questa guida di riferimento accompagna l'esame SEC-001. Gli esaminandi possono aprirla in una nuova scheda durante la prova.",
                    new String[][]{
                            {"Capitolo 1: Sicurezza di account e password", "• Abilitare l'autenticazione a più fattori (MFA) e usare password robuste e univoche.\n• Non riutilizzare mai la stessa password su più sistemi."},
                            {"Capitolo 2: Difesa da Phishing e ingegneria sociale", "• Controllare attentamente il dominio del mittente e gli URL dei collegamenti.\n• Non aprire allegati sospetti e non inviare mai password via e-mail."},
                            {"Capitolo 3: Regola di backup 3-2-1 e crittografia", "• La regola 3-2-1 prevede 3 copie dei dati su 2 supporti diversi e almeno 1 copia offsite o offline.\n• Proteggere i file sensibili con crittografia AES-256."},
                            {"Capitolo 4: Principio del privilegio minimo (Least Privilege)", "• Concedere solo i permessi minimi necessari (ad es. sola lettura READ).\n• Non assegnare mai i privilegi di Amministratore (Admin) per impostazione predefinita a tutti i dipendenti."}
                    })),
            Map.entry("pl", new TextbookContent(
                    "Przewodnik Podstawy Bezpieczeństwa (Polski).pdf",
                    "Przewodnik Podstawy Bezpieczeństwa",
                    "Niniejszy przewodnik referencyjny towarzyszy egzaminowi SEC-001. Zdający mogą otworzyć go w nowej karcie podczas testu.",
                    new String[][]{
                            {"Rozdział 1: Bezpieczeństwo kont i haseł", "• Włącz uwierzytelnianie wieloskładnikowe (MFA) i stosuj silne, unikalne hasła.\n• Nigdy nie używaj tego samego hasła w wielu systemach."},
                            {"Rozdział 2: Ochrona przed phishingiem i socjotechniką", "• Dokładnie sprawdzaj domeny nadawców oraz adresy URL linków.\n• Nie otwieraj nieznanych załączników i nigdy nie wysyłaj haseł e-mailem."},
                            {"Rozdział 3: Reguła kopii zapasowych 3-2-1 i szyfrowanie", "• Reguła 3-2-1 oznacza przechowywanie 3 kopii danych na 2 różnych nośnikach, z czego co najmniej 1 kopia znajduje się poza siedzibą lub offline.\n• Chroń poufne pliki szyfrowaniem AES-256."},
                            {"Rozdział 4: Zasada najmniejszych uprawnień (Least Privilege)", "• Przyznawaj wyłącznie minimalne wymagane uprawnienia (np. odczyt READ).\n• Nigdy domyślnie nie nadawaj uprawnień Administratora (Admin) wszystkim pracownikom."}
                    })),
            Map.entry("ru", new TextbookContent(
                    "Руководство по основам безопасности (Русский).pdf",
                    "Руководство по основам информационной безопасности",
                    "Данное справочное руководство предназначено для экзамена SEC-001. Экзаменуемые могут открыть его в новой вкладке во время теста.",
                    new String[][]{
                            {"Глава 1: Безопасность учетных записей и паролей", "• Включите многофакторную аутентификацию (MFA) и используйте сложные уникальные пароли.\n• Никогда не используйте один и тот же пароль в разных системах."},
                            {"Глава 2: Защита от фишинга и социальной инженерии", "• Внимательно проверяйте домен отправителя и URL-адреса ссылок.\n• Не открывайте подозрительные вложения и не отправляйте пароли по электронной почте."},
                            {"Глава 3: Правило резервного копирования 3-2-1 и шифрование", "• Правило 3-2-1 требует хранения 3 копий данных на 2 разных носителях, при этом минимум 1 копия должна храниться удаленно или офлайн.\n• Защищайте конфиденциальные файлы шифрованием AES-256."},
                            {"Глава 4: Принцип наименьших привилегий (Least Privilege)", "• Предоставляйте только минимально необходимые права доступа (например, чтение READ).\n• Никогда не назначайте права Администратора (Admin) всем сотрудникам по умолчанию."}
                    })),
            Map.entry("el", new TextbookContent(
                    "Οδηγός Βασικών Αρχών Ασφάλειας (Ελληνικά).pdf",
                    "Οδηγός Βασικών Αρχών Ασφάλειας",
                    "Αυτός ο οδηγός αναφοράς συνοδεύει την εξέταση SEC-001. Οι εξεταζόμενοι μπορούν να τον ανοίξουν σε νέα καρτέλα κατά τη διάρκεια της εξέτασης.",
                    new String[][]{
                            {"Κεφάλαιο 1: Ασφάλεια λογαριασμών και κωδικών πρόσβασης", "• Ενεργοποιήστε τον έλεγχο ταυτότητας πολλαπλών παραγόντων (MFA) και χρησιμοποιήστε ισχυρούς, μοναδικούς κωδικούς.\n• Μην επαναχρησιμοποιείτε τον ίδιο κωδικό σε διαφορετικά συστήματα."},
                            {"Κεφάλαιο 2: Προστασία από ηλεκτρονικό ψάρεμα (Phishing)", "• Ελέγχετε προσεκτικά τον τομέα αποστολέα και τις διευθύνσεις URL.\n• Μην ανοίγετε άγνωστα συνημμένα και μην στέλνετε κωδικούς μέσω email."},
                            {"Κεφάλαιο 3: Κανόνας αντιγράφων ασφαλείας 3-2-1 και κρυπτογράφηση", "• Ο κανόνας 3-2-1 απαιτεί 3 αντίγραφα δεδομένων σε 2 διαφορετικά μέσα, με τουλάχιστον 1 αντίγραφο εκτός εγκατάστασης ή εκτός σύνδεσης.\n• Προστατεύστε τα αρχεία με κρυπτογράφηση AES-256."},
                            {"Κεφάλαιο 4: Αρχή του ελάχιστου προνομίου (Least Privilege)", "• Εκχωρήστε μόνο τα ελάχιστα απαραίτητα δικαιώματα (π.χ. ανάγνωση READ).\n• Μην εκχωρείτε ποτέ δικαιώματα Διαχειριστή (Admin) από προεπιλογή σε όλους τους υπαλλήλους."}
                    })),
            Map.entry("sq_AL", new TextbookContent(
                    "Udhëzuesi i Bazave të Sigurisë (Shqip).pdf",
                    "Udhëzuesi i Bazave të Sigurisë",
                    "Ky udhëzues referues shoqëron provimin SEC-001. Kandidatët mund ta hapin në një skedë të re gjatë provimit.",
                    new String[][]{
                            {"Kapitulli 1: Siguria e Llogarive dhe Fjalëkalimeve", "• Aktivizoni Autentikimin me Shumë Faktorë (MFA) dhe përdorni fjalëkalime të forta e unike.\n• Mos përdorni kurrë të njëjtin fjalëkalim në disa sisteme."},
                            {"Kapitulli 2: Mbrojtja nga Phishing dhe Inxhinieria Sociale", "• Kontrolloni me kujdes domenin e dërguesit dhe lidhjet URL.\n• Mos hapni bashkëngjitje të panjohura dhe mos dërgoni fjalëkalime me email."},
                            {"Kapitulli 3: Rregulli i Rezervimit 3-2-1 dhe Enkriptimi", "• Rregulli 3-2-1 kërkon ruajtjen e 3 kopjeve të të dhënave në 2 media të ndryshme dhe të paktën 1 kopje jashtë vendndodhjes ose offline.\n• Mbroni skedarët me enkriptim AES-256."},
                            {"Kapitulli 4: Parimi i Privilegjit Minimal (Least Privilege)", "• Jepni vetëm lejet minimale të nevojshme (p.sh. lexim READ).\n• Mos jepni kurrë privilegje Administratori (Admin) si parazgjedhje për të gjithë punonjësit."}
                    })),
            Map.entry("sr", new TextbookContent(
                    "Водич за основе безбедности (Српски).pdf",
                    "Водич за основе информационе безбедности",
                    "Овај референтни водич прати испит SEC-001. Кандидати га могу отворити у новој картици током полагања испита.",
                    new String[][]{
                            {"Поглавље 1: Безбедност налога и лозинки", "• Омогућите вишефакторску аутентификацију (MFA) и користите јаке, јединствене лозинке.\n• Никада не користите исту лозинку на више система."},
                            {"Поглавље 2: Заштита од фишинга и социјалног инжењеринга", "• Пажљиво проверите домен пошиљаоца и URL адресе линкова.\n• Не отварајте непознате прилоге и не шаљите лозинке путем е-поште."},
                            {"Поглавље 3: Правило резервне копије 3-2-1 и шифровање", "• Правило 3-2-1 подразумева чување 3 копије података на 2 различита медија и најмање 1 копију ван локације или офлајн.\n• Заштитите осетљиве датотеке AES-256 шифровањем."},
                            {"Поглавље 4: Принцип најмањих привилегија (Least Privilege)", "• Доделите само минимално потребне дозволе (нпр. читање READ).\n• Никада не додељујте подразумевано права Администратора (Admin) свим запосленима."}
                    }))
    );

    public static String getTextbookFileName(String localeCode) {
        TextbookContent c = TEXTBOOK_I18N.getOrDefault(localeCode, TEXTBOOK_I18N.get("en"));
        return c.fileName;
    }

    /**
     * Generates the localized textbook PDF for the given locale so that PDFBox text extraction ("eye" icon) and Lucene indexing work across all 16 languages.
     */
    public static byte[] exportTextbookPdf(String localeCode) throws Exception {
        TextbookContent content = TEXTBOOK_I18N.getOrDefault(localeCode, TEXTBOOK_I18N.get("en"));
        IFontProvider fontProvider = PdfUtil.getFontProvider();
        Font titleFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 16f, Font.BOLD, new Color(25, 35, 55));
        Font h2Font = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 12.5f, Font.BOLD, new Color(30, 64, 175));
        Font bodyFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10.5f, Font.NORMAL, new Color(30, 41, 59));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 42, 42);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Paragraph title = new Paragraph(content.title, titleFont);
        title.setSpacingAfter(10f);
        doc.add(title);

        Paragraph intro = new Paragraph(content.intro, bodyFont);
        intro.setSpacingAfter(14f);
        doc.add(intro);

        for (String[] sec : content.sections) {
            Paragraph h = new Paragraph(sec[0], h2Font);
            h.setSpacingBefore(6f);
            h.setSpacingAfter(4f);
            doc.add(h);

            Paragraph p = new Paragraph(sec[1], bodyFont);
            p.setSpacingAfter(10f);
            doc.add(p);
        }

        doc.close();
        return baos.toByteArray();
    }

    public static byte[] exportSummaryPdf(List<JsonObject> attempts, String fromDate, String toDate,
                                          String exportedBy, String examFilterLabel, String lang) throws Exception {
        IFontProvider fontProvider = PdfUtil.getFontProvider();
        Font titleFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 16f, Font.BOLD, new Color(25, 35, 55));
        Font metaFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9.5f, Font.NORMAL, new Color(75, 85, 100));
        Font kpiLabelFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9f, Font.NORMAL, new Color(90, 100, 115));
        Font kpiValFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 13f, Font.BOLD, new Color(30, 64, 175));
        Font thFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9.5f, Font.BOLD, Color.WHITE);
        Font tdFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 9.5f, Font.NORMAL, new Color(30, 41, 59));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 34, 34, 38, 38);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Paragraph title = new Paragraph(msg(lang, "summary_title"), titleFont);
        title.setSpacingAfter(6f);
        doc.add(title);

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(10f);
        addMetaCell(metaTable, msg(lang, "date_range") + ": " + fromDate + " ~ " + toDate, metaFont);
        addMetaCell(metaTable, msg(lang, "exam_filter") + ": " + (Strings.isNullOrEmpty(examFilterLabel) ? msg(lang, "all_exams") : examFilterLabel), metaFont);
        addMetaCell(metaTable, msg(lang, "exported_by") + ": " + exportedBy, metaFont);
        addMetaCell(metaTable, msg(lang, "exported_at") + ": " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date()), metaFont);
        doc.add(metaTable);

        int count = attempts.size();
        double sum = 0.0;
        double max = count > 0 ? Double.NEGATIVE_INFINITY : 0.0;
        double min = count > 0 ? Double.POSITIVE_INFINITY : 0.0;
        for (JsonObject a : attempts) {
            double s = a.getJsonNumber("score").doubleValue();
            sum += s;
            if (s > max) max = s;
            if (s < min) min = s;
        }
        double avg = count > 0 ? sum / count : 0.0;

        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(12f);
        addKpiCell(kpiTable, msg(lang, "total_attempts"), String.valueOf(count), kpiLabelFont, kpiValFont);
        addKpiCell(kpiTable, msg(lang, "avg_score"), String.format(Locale.ENGLISH, "%.2f", avg), kpiLabelFont, kpiValFont);
        addKpiCell(kpiTable, msg(lang, "max_score"), String.format(Locale.ENGLISH, "%.2f", max), kpiLabelFont, kpiValFont);
        addKpiCell(kpiTable, msg(lang, "min_score"), String.format(Locale.ENGLISH, "%.2f", min), kpiLabelFont, kpiValFont);
        doc.add(kpiTable);

        if (attempts.isEmpty()) {
            Paragraph empty = new Paragraph(msg(lang, "no_records"), metaFont);
            empty.setSpacingBefore(10f);
            doc.add(empty);
        } else {
            PdfPTable table = new PdfPTable(new float[]{1.4f, 2.2f, 2.0f, 1.9f, 1.3f, 1.6f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            String[] headers = {
                    msg(lang, "col_code_ver"),
                    msg(lang, "col_title"),
                    msg(lang, "col_owner"),
                    msg(lang, "col_examinee"),
                    msg(lang, "col_score"),
                    msg(lang, "col_time")
            };
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
                cell.setBackgroundColor(new Color(30, 58, 138));
                cell.setPadding(6f);
                cell.setBorderColor(new Color(203, 213, 225));
                table.addCell(cell);
            }

            SimpleDateFormat timeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
            boolean zebra = false;
            for (JsonObject att : attempts) {
                Color rowBg = zebra ? new Color(248, 250, 252) : Color.WHITE;
                zebra = !zebra;
                String codeVer = att.getString("exam_code", "-") + " v" + att.getString("exam_version", "-");
                String eTitle = att.getString("exam_title", "-");
                String ownerLabel = att.getString("owner_label", att.getString("owner_username", "-"));
                String examinee = att.getString("display_name", att.getString("username", "")) + " (" + att.getString("username", "") + ")";
                String scoreStr = String.format(Locale.ENGLISH, "%.2f / %.2f",
                        att.getJsonNumber("score").doubleValue(),
                        att.getJsonNumber("max_score").doubleValue());
                long ts = att.containsKey("submitted_at") ? att.getJsonNumber("submitted_at").longValue() : 0L;
                String tsStr = ts > 0 ? timeFmt.format(new Date(ts)) : "-";

                addRowCell(table, codeVer, tdFont, rowBg);
                addRowCell(table, eTitle, tdFont, rowBg);
                addRowCell(table, ownerLabel, tdFont, rowBg);
                addRowCell(table, examinee, tdFont, rowBg);
                addRowCell(table, scoreStr, tdFont, rowBg);
                addRowCell(table, tsStr, tdFont, rowBg);
            }
            doc.add(table);
        }

        doc.close();
        return baos.toByteArray();
    }

    private static byte[] renderPdf(JsonObject exam, JsonObject attempt, String lang) throws Exception {
        IFontProvider fontProvider = PdfUtil.getFontProvider();
        Font titleFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 16f, Font.BOLD, new Color(25, 35, 55));
        Font metaFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10f, Font.NORMAL, new Color(80, 90, 105));
        Font qFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 11f, Font.BOLD, Color.BLACK);
        Font optFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10.5f, Font.NORMAL, new Color(40, 40, 40));
        Font correctFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10f, Font.BOLD, new Color(22, 130, 62));
        Font wrongFont = fontProvider.getFont(PdfUtil.BUNDLED_CJK_FONT_FAMILY, BaseFont.IDENTITY_H, 10f, Font.BOLD, new Color(195, 45, 45));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 46, 46);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        if (attempt != null) {
            String watermarkText = buildWatermarkText(attempt);
            BaseFont watermarkBaseFont = optFont.getBaseFont();
            writer.setPageEvent(new WatermarkPageEvent(watermarkBaseFont, watermarkText));
        }

        doc.open();

        String title = exam.getString("title", "Online Exam");
        String examCode = exam.getString("exam_code", "-");
        String examVersion = exam.getString("exam_version", "-");

        Paragraph header = new Paragraph(title, titleFont);
        header.setSpacingAfter(6f);
        doc.add(header);

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(14f);
        addMetaCell(metaTable, msg(lang, "exam_code") + ": " + examCode + "   |   " + msg(lang, "version") + ": " + examVersion, metaFont);
        if (attempt != null) {
            String displayName = attempt.getString("display_name", attempt.getString("username", ""));
            String username = attempt.getString("username", "");
            long submittedAt = attempt.containsKey("submitted_at") ? attempt.getJsonNumber("submitted_at").longValue() : System.currentTimeMillis();
            String formattedDate = formatEnglishDate(submittedAt);
            double score = attempt.getJsonNumber("score").doubleValue();
            double maxScore = attempt.getJsonNumber("max_score").doubleValue();
            addMetaCell(metaTable, String.format(Locale.ENGLISH, "%s: %.2f / %.2f", msg(lang, "score"), score, maxScore), metaFont);
            addMetaCell(metaTable, msg(lang, "examinee") + ": " + displayName + " (" + username + ")", metaFont);
            addMetaCell(metaTable, msg(lang, "exam_time") + ": " + formattedDate, metaFont);
        } else {
            addMetaCell(metaTable, msg(lang, "blank_paper"), metaFont);
            addMetaCell(metaTable, msg(lang, "name_line"), metaFont);
            addMetaCell(metaTable, msg(lang, "date_line"), metaFont);
        }
        doc.add(metaTable);

        JsonObject resultsMap = attempt != null && attempt.containsKey("results")
                ? attempt.getJsonObject("results")
                : null;
        JsonObject answersMap = attempt != null && attempt.containsKey("answers")
                ? attempt.getJsonObject("answers")
                : null;

        JsonArray questions = exam.getJsonArray("questions");
        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                JsonObject q = questions.getJsonObject(i);
                String qId = q.getString("id", "q" + (i + 1));
                String qType = q.getString("type", GiftParser.TYPE_SINGLE_CHOICE);
                String qPrompt = q.getString("prompt", "");
                String typeBadge = GiftParser.TYPE_MULTIPLE_CHOICE.equals(qType) ? msg(lang, "type_multi")
                        : (GiftParser.TYPE_TRUE_FALSE.equals(qType) ? msg(lang, "type_tf") : msg(lang, "type_single"));

                Paragraph qPara = new Paragraph();
                qPara.setSpacingBefore(6f);
                qPara.setSpacingAfter(4f);
                qPara.add(new Phrase((i + 1) + ". " + typeBadge + " " + qPrompt, qFont));

                if (resultsMap != null && resultsMap.containsKey(qId)) {
                    boolean isCorrect = resultsMap.getJsonObject(qId).getBoolean("correct", false);
                    qPara.add(new Phrase(isCorrect ? msg(lang, "correct") : msg(lang, "incorrect"), isCorrect ? correctFont : wrongFont));
                }
                doc.add(qPara);

                Set<String> selectedIds = extractSelectedIds(answersMap, qId);

                if (GiftParser.TYPE_TRUE_FALSE.equals(qType)) {
                    String tfMarkTrue = selectedIds.contains("true") ? "[■] " : "[  ] ";
                    String tfMarkFalse = selectedIds.contains("false") ? "[■] " : "[  ] ";
                    Paragraph pTrue = new Paragraph("    " + tfMarkTrue + msg(lang, "true_opt"), optFont);
                    Paragraph pFalse = new Paragraph("    " + tfMarkFalse + msg(lang, "false_opt"), optFont);
                    pFalse.setSpacingAfter(6f);
                    doc.add(pTrue);
                    doc.add(pFalse);
                } else {
                    JsonArray choices = q.getJsonArray("choices");
                    if (choices != null) {
                        for (int cIdx = 0; cIdx < choices.size(); cIdx++) {
                            JsonObject choice = choices.getJsonObject(cIdx);
                            String cId = choice.getString("id", "");
                            String cText = choice.getString("text", "");
                            String mark = selectedIds.contains(cId) ? "[■] " : "[  ] ";
                            char letter = (char) ('A' + cIdx);
                            Paragraph cPara = new Paragraph("    " + mark + letter + ". " + cText, optFont);
                            if (cIdx == choices.size() - 1) {
                                cPara.setSpacingAfter(6f);
                            }
                            doc.add(cPara);
                        }
                    }
                }
            }
        }

        doc.close();
        return baos.toByteArray();
    }

    private static void addMetaCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(220, 225, 232));
        cell.setPadding(6f);
        cell.setBackgroundColor(new Color(248, 250, 252));
        table.addCell(cell);
    }

    private static void addKpiCell(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setBackgroundColor(new Color(239, 246, 255));
        cell.setPadding(8f);
        Paragraph p1 = new Paragraph(label, labelFont);
        Paragraph p2 = new Paragraph(value, valFont);
        cell.addElement(p1);
        cell.addElement(p2);
        table.addCell(cell);
    }

    private static void addRowCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6f);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    private static Set<String> extractSelectedIds(JsonObject answersMap, String qId) {
        Set<String> selected = new HashSet<>();
        if (answersMap == null || !answersMap.containsKey(qId)) {
            return selected;
        }
        JsonValue val = answersMap.get(qId);
        if (val.getValueType() == JsonValue.ValueType.STRING) {
            selected.add(answersMap.getString(qId).toLowerCase(Locale.ROOT));
        } else if (val.getValueType() == JsonValue.ValueType.TRUE) {
            selected.add("true");
        } else if (val.getValueType() == JsonValue.ValueType.FALSE) {
            selected.add("false");
        } else if (val.getValueType() == JsonValue.ValueType.ARRAY) {
            JsonArray arr = val.asJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                selected.add(arr.getString(i));
            }
        }
        return selected;
    }

    private static String buildWatermarkText(JsonObject attempt) {
        String displayName = attempt.getString("display_name", attempt.getString("username", "Examinee"));
        String username = attempt.getString("username", "");
        long submittedAt = attempt.containsKey("submitted_at")
                ? attempt.getJsonNumber("submitted_at").longValue()
                : System.currentTimeMillis();
        return displayName + " (" + username + ") · " + formatEnglishDate(submittedAt);
    }

    private static String formatEnglishDate(long epochMillis) {
        String pattern = ConfigUtil.getPreviewWatermarkDateFormat();
        try {
            return new SimpleDateFormat(pattern, Locale.ENGLISH).format(new Date(epochMillis));
        } catch (Exception e) {
            return new SimpleDateFormat(ConfigUtil.DEFAULT_PREVIEW_WATERMARK_DATE_FORMAT, Locale.ENGLISH).format(new Date(epochMillis));
        }
    }

    private static class WatermarkPageEvent extends PdfPageEventHelper {
        private final BaseFont baseFont;
        private final String text;

        WatermarkPageEvent(BaseFont baseFont, String text) {
            this.baseFont = baseFont;
            this.text = text;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            canvas.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.16f);
            canvas.setGState(gs);
            canvas.beginText();
            canvas.setFontAndSize(baseFont, 18f);
            canvas.setColorFill(new Color(95, 105, 120));
            float width = document.getPageSize().getWidth();
            float height = document.getPageSize().getHeight();
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.30f, height * 0.28f, 35f);
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.70f, height * 0.28f, 35f);
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.30f, height * 0.72f, 35f);
            canvas.showTextAligned(Element.ALIGN_CENTER, text, width * 0.70f, height * 0.72f, 35f);
            canvas.endText();
            canvas.restoreState();
        }
    }
}
