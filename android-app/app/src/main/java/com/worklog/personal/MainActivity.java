package com.worklog.personal;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 41;
    private static final int REQUEST_IMPORT = 42;
    private static final int TARGET_MINUTES = 8 * 60;
    private static final int GREEN = 0xFF235B45;
    private static final int INK = 0xFF202A24;
    private static final int MUTED = 0xFF748078;
    private static final int PAPER = 0xFFF5F4EF;
    private static final int CARD = 0xFFFFFEFA;
    private static final int LINE = 0xFFE7E8DF;
    private static final int MINT = 0xFFE7F1E8;
    private static final int ORANGE = 0xFFD77B42;
    private static final String APP_VERSION = "0.0.11";
    private static final String SYNC_PREFS = "webdav_sync";
    private static final String SECRET_ALIAS = "work_hours_webdav_password";

    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA);
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final Object databaseLock = new Object();
    private LocalDate shownMonth = LocalDate.now().withDayOfMonth(1);
    private LocalDate today;
    private AttendanceDatabase database;
    private Map<String, AttendanceRecord> records = new HashMap<>();
    private LinearLayout rootLayout;
    private SwipeFrameLayout pageContainer;
    private LinearLayout monthlyPage;
    private LinearLayout homePage;
    private LinearLayout navigationBar;
    private final ArrayList<LinearLayout> navigationItems = new ArrayList<>();
    private ScrollView daysScroll;
    private LinearLayout daysList;
    private TextView monthTitle;
    private TextView calendarMonthTitle;
    private GridLayout calendarGrid;
    private TextView actualHours;
    private TextView averageHours;
    private TextView balanceAmount;
    private TextView unitSwitch;
    private TextView balanceCaption;
    private TextView balanceDetails;
    private TextView calendarSummary;
    private TextView progressLabel;
    private LinearLayout progressTrack;
    private View progressFill;
    private View progressRemainder;
    private TextView progressDays;
    private TextView progressPending;
    private TextView quickPunchButton;
    private TextView syncStatusView;
    private ValueAnimator progressAnimator;
    private boolean focusTodayOnRender = true;
    private boolean scrollDaysToTopOnRender;
    private boolean showMinutes;
    private int currentPage = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        today = LocalDate.now();
        showMinutes = getPreferences(MODE_PRIVATE).getBoolean("show_minutes", false);
        database = new AttendanceDatabase(this);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(createPage());
        render();
        if (hasWebDavConfig()) queueSync(false, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalDate resumedToday = LocalDate.now();
        if (monthTitle != null && !resumedToday.equals(today)) {
            today = resumedToday;
            shownMonth = today.withDayOfMonth(1);
            focusTodayOnRender = true;
            render();
        } else {
            today = resumedToday;
        }
    }

    @Override
    protected void onDestroy() {
        syncExecutor.shutdownNow();
        super.onDestroy();
    }

    private View createPage() {
        pageContainer = new SwipeFrameLayout(this);
        pageContainer.setBackgroundColor(PAPER);
        pageContainer.setOnSwipeListener(left -> showPage(Math.max(0, Math.min(2, currentPage + (left ? 1 : -1)))));

        rootLayout = column();
        rootLayout.setBackgroundColor(PAPER);
        rootLayout.addView(pageContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView calendarScroll = new ScrollView(this);
        calendarScroll.setFillViewport(true);
        calendarScroll.setBackgroundColor(PAPER);
        monthlyPage = column();
        monthlyPage.setPadding(dp(16), dp(12), dp(16), dp(18));
        calendarScroll.addView(monthlyPage);
        pageContainer.addView(calendarScroll, new FrameLayout.LayoutParams(-1, -1));
        buildMonthlyPage(monthlyPage);

        homePage = column();
        homePage.setPadding(dp(16), dp(12), dp(16), dp(10));
        pageContainer.addView(homePage, new FrameLayout.LayoutParams(-1, -1));
        buildHomePage(homePage);

        ScrollView toolsScroll = new ScrollView(this);
        toolsScroll.setFillViewport(true);
        toolsScroll.setBackgroundColor(PAPER);
        toolsScroll.setVisibility(View.GONE);
        LinearLayout toolsPage = column();
        toolsPage.setPadding(dp(20), dp(22), dp(20), dp(34));
        toolsScroll.addView(toolsPage);
        pageContainer.addView(toolsScroll, new FrameLayout.LayoutParams(-1, -1));
        buildToolsPage(toolsPage);

        navigationBar = row();
        navigationBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        navigationBar.setBackgroundColor(CARD);
        navigationBar.setElevation(dp(6));
        String[] labels = {"月度", "每日", "工具"};
        String[] icons = {"▦", "◷", "⋯"};
        for (int index = 0; index < labels.length; index++) {
            final int pageIndex = index;
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(3));
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(labels[index] + "页面");
            TextView icon = text(icons[index], 17, MUTED, true);
            icon.setGravity(Gravity.CENTER);
            TextView label = text(labels[index], 10, MUTED, true);
            label.setGravity(Gravity.CENTER);
            item.addView(icon);
            item.addView(label, marginParams(-1, -2, 0, 2, 0, 0));
            item.setOnClickListener(v -> showPage(pageIndex));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            itemParams.setMargins(dp(4), 0, dp(4), 0);
            navigationBar.addView(item, itemParams);
            navigationItems.add(item);
        }
        rootLayout.addView(navigationBar, params(-1, 64));
        pageContainer.setCurrentPage(currentPage);
        showPage(currentPage);
        return rootLayout;
    }

    private void buildMonthlyPage(LinearLayout page) {
        LinearLayout monthRow = row();
        TextView previous = action("‹", false);
        previous.setTextSize(21);
        previous.setOnClickListener(v -> changeMonth(-1));
        monthRow.addView(previous, params(44, 42));
        calendarMonthTitle = text("", 15, INK, true);
        calendarMonthTitle.setGravity(Gravity.CENTER);
        monthRow.addView(calendarMonthTitle, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView next = action("›", false);
        next.setTextSize(21);
        next.setOnClickListener(v -> changeMonth(1));
        monthRow.addView(next, params(44, 42));
        TextView todayButton = action("今天", true);
        todayButton.setOnClickListener(v -> { shownMonth = today.withDayOfMonth(1); focusTodayOnRender = true; render(); });
        monthRow.addView(todayButton, marginParams(-2, 42, 8, 0, 0, 0));
        page.addView(monthRow);

        TextView heading = text("月度概览", 21, INK, true);
        page.addView(heading, marginParams(-1, -2, 0, 10, 0, 0));
        calendarSummary = text("工作日 0 天 · 休息日 0 天\n已记录 0.000 小时 · 目标 0.000 小时", 12, MUTED, false);
        calendarSummary.setLineSpacing(dp(5), 1f);
        calendarSummary.setPadding(dp(14), dp(12), dp(14), dp(12));
        calendarSummary.setBackground(roundBackground(CARD, 13, LINE));
        setDurationToggle(calendarSummary);
        page.addView(calendarSummary, params(-1, -2));
        TextView legend = text("班 = 工作日 · 休 = 休息日；点日期可编辑，点状态可切换", 10, MUTED, false);
        page.addView(legend, marginParams(-1, -2, 2, 10, 0, 6));

        LinearLayout weekdays = row();
        String[] weekdayLabels = {"一", "二", "三", "四", "五", "六", "日"};
        for (String weekday : weekdayLabels) {
            TextView label = text(weekday, 10, MUTED, true);
            label.setGravity(Gravity.CENTER);
            weekdays.addView(label, new LinearLayout.LayoutParams(0, dp(27), 1));
        }
        page.addView(weekdays);
        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        page.addView(calendarGrid, marginParams(-1, -2, 0, 2, 0, 0));
    }

    private void buildHomePage(LinearLayout page) {
        LinearLayout monthRow = row();
        TextView previous = action("‹", false);
        previous.setTextSize(21);
        previous.setOnClickListener(v -> changeMonth(-1));
        monthRow.addView(previous, params(44, 42));
        monthTitle = text("", 15, INK, true);
        monthTitle.setGravity(Gravity.CENTER);
        monthRow.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView next = action("›", false);
        next.setTextSize(21);
        next.setOnClickListener(v -> changeMonth(1));
        monthRow.addView(next, params(44, 42));
        TextView todayButton = action("今天", true);
        todayButton.setOnClickListener(v -> { shownMonth = today.withDayOfMonth(1); focusTodayOnRender = true; render(); });
        monthRow.addView(todayButton, marginParams(-2, 42, 8, 0, 0, 0));
        page.addView(monthRow);

        addSpace(page, 8);
        LinearLayout balanceCard = column();
        balanceCard.setPadding(dp(15), dp(12), dp(15), dp(12));
        balanceCard.setBackground(roundBackground(GREEN, 14, 0));
        LinearLayout balanceHeading = row();
        TextView balanceTitle = text("当前工时结算", 11, 0xFFD0E1D3, true);
        balanceHeading.addView(balanceTitle, new LinearLayout.LayoutParams(0, -2, 1));
        unitSwitch = action(showMinutes ? "显示小时" : "显示分钟", false);
        unitSwitch.setTextSize(9);
        unitSwitch.setOnClickListener(v -> toggleDurationUnit());
        balanceHeading.addView(unitSwitch, params(-2, 32));
        balanceCard.addView(balanceHeading);
        balanceAmount = text("欠 0.000 小时", 23, 0xFFF2B978, true);
        balanceAmount.setPadding(0, dp(4), 0, dp(6));
        setDurationToggle(balanceAmount);
        balanceCard.addView(balanceAmount);
        balanceDetails = text("0 个工作日 × 8.000 小时 = 0.000 小时目标\n实际累计 0.000 小时", 10, 0xFFF7FBF6, false);
        balanceDetails.setLineSpacing(dp(3), 1f);
        setDurationToggle(balanceDetails);
        balanceCard.addView(balanceDetails);
        balanceCaption = text("按当前月份截至今天计算", 9, 0xFFD0E1D3, false);
        balanceCaption.setPadding(0, dp(5), 0, 0);
        balanceCard.addView(balanceCaption);
        page.addView(balanceCard);

        addSpace(page, 9);
        LinearLayout statsTop = row();
        actualHours = addStat(statsTop, "已记工时", "0 小时", "本月计薪时段", GREEN);
        averageHours = addStat(statsTop, "工作日均工时", "0 小时", "按已过工作日平均", GREEN);
        setDurationToggle(actualHours);
        setDurationToggle(averageHours);
        page.addView(statsTop);

        addSpace(page, 9);
        LinearLayout progressCard = column();
        progressCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        progressCard.setBackground(roundBackground(GREEN, 14, 0));
        LinearLayout progressTop = row();
        progressTop.addView(text("截至今天的工时进度", 12, 0xFFF7FBF6, true), new LinearLayout.LayoutParams(0, -2, 1));
        progressLabel = text("0%", 12, 0xFFF2B978, true);
        progressTop.addView(progressLabel);
        progressCard.addView(progressTop);
        addSpace(progressCard, 9);
        progressTrack = row();
        progressTrack.setBackground(roundBackground(0x4AFFFFFF, 5, 0));
        progressFill = new View(this);
        progressFill.setBackground(roundBackground(0xFFF2B978, 5, 0));
        progressRemainder = new View(this);
        progressTrack.addView(progressFill, new LinearLayout.LayoutParams(0, dp(7), 0));
        progressTrack.addView(progressRemainder, new LinearLayout.LayoutParams(0, dp(7), 1));
        progressCard.addView(progressTrack, params(-1, 7));
        progressDays = text("0/0 个工作日已记录", 10, 0xFFD0E1D3, false);
        progressPending = text("0 天待补录", 10, 0xFFD0E1D3, false);
        LinearLayout progressBottom = row();
        progressBottom.addView(progressDays, new LinearLayout.LayoutParams(0, -2, 1));
        progressBottom.addView(progressPending);
        progressCard.addView(progressBottom, marginParams(-1, -2, 0, 7, 0, 0));
        page.addView(progressCard, marginParams(-1, -2, 0, 8, 0, 0));

        addSpace(page, 9);
        LinearLayout dailyHeading = row();
        dailyHeading.addView(text("每日记录", 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        dailyHeading.addView(text("状态可点切换", 9, MUTED, false));
        page.addView(dailyHeading);
        quickPunchButton = action("上班打卡", true);
        quickPunchButton.setTextSize(12);
        page.addView(quickPunchButton, marginParams(-1, 42, 0, 7, 0, 0));
        TextView tip = text("快捷按钮记录手机当前时间 · 点日期卡片可编辑或补录", 10, MUTED, false);
        tip.setPadding(0, dp(4), 0, dp(7));
        page.addView(tip);
        daysScroll = new ScrollView(this);
        daysScroll.setFillViewport(false);
        daysScroll.setVerticalScrollBarEnabled(true);
        daysScroll.setClipToPadding(false);
        daysList = column();
        daysScroll.addView(daysList, new ScrollView.LayoutParams(-1, -2));
        page.addView(daysScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void buildToolsPage(LinearLayout page) {
        LinearLayout top = row();
        top.addView(text("▦  工时日记 " + APP_VERSION, 14, GREEN, true), new LinearLayout.LayoutParams(0, dp(42), 1));
        page.addView(top);
        addSpace(page, 21);
        page.addView(text("工具与备份", 24, INK, true));
        TextView toolHint = text("记录保存在手机本地；也可配置 WebDAV 自动同步，并保留 CSV 备份。", 12, MUTED, false);
        toolHint.setPadding(0, dp(6), 0, dp(18));
        page.addView(toolHint);

        TextView export = action("导出备份", true);
        export.setTextSize(13);
        export.setOnClickListener(v -> exportBackup());
        page.addView(export, params(-1, 50));
        TextView backupButton = action("恢复备份", false);
        backupButton.setTextSize(13);
        backupButton.setOnClickListener(v -> chooseBackup());
        page.addView(backupButton, marginParams(-1, 50, 0, 10, 0, 0));

        addSpace(page, 20);
        LinearLayout syncCard = column();
        syncCard.setPadding(dp(15), dp(14), dp(15), dp(14));
        syncCard.setBackground(roundBackground(CARD, 14, LINE));
        syncCard.addView(text("WebDAV 云同步", 14, INK, true));
        TextView syncHelp = text("启动时拉取，记录修改后自动同步。按日期比较内容哈希；冲突时可选择保留本机或服务器版本。", 11, MUTED, false);
        syncHelp.setLineSpacing(dp(3), 1f);
        syncHelp.setPadding(0, dp(5), 0, dp(8));
        syncCard.addView(syncHelp);
        syncStatusView = text(hasWebDavConfig() ? "已配置 · 等待同步" : "尚未配置服务器", 10, MUTED, false);
        syncStatusView.setPadding(0, 0, 0, dp(9));
        syncCard.addView(syncStatusView);
        TextView syncButton = action("立即同步", true);
        syncButton.setTextSize(12);
        syncButton.setOnClickListener(v -> queueSync(true));
        syncCard.addView(syncButton, params(-1, 44));
        TextView configureButton = action("设置 WebDAV", false);
        configureButton.setTextSize(12);
        configureButton.setOnClickListener(v -> showWebDavSettings());
        syncCard.addView(configureButton, marginParams(-1, 44, 0, 8, 0, 0));
        page.addView(syncCard);

        addSpace(page, 22);
        LinearLayout policy = column();
        policy.setPadding(dp(15), dp(14), dp(15), dp(14));
        policy.setBackground(roundBackground(0xFFEEEFE8, 14, 0));
        policy.addView(text("公司计薪时段", 12, INK, true));
        TextView policyText = text("08:00–12:00   ·   13:30–17:30\n18:00–24:00 计薪；12:00–13:30、17:30–18:00 休息。", 11, MUTED, false);
        policyText.setLineSpacing(dp(3), 1f);
        policyText.setPadding(0, dp(5), 0, 0);
        policy.addView(policyText);
        page.addView(policy);
        addSpace(page, 10);
        TextView localHint = text("换机前请先导出 CSV；恢复时选择之前保存的备份文件。", 10, MUTED, false);
        localHint.setGravity(Gravity.CENTER);
        page.addView(localHint);
    }

    private void showPage(int page) {
        if (pageContainer == null || pageContainer.getChildCount() < 3) return;
        int previousPage = currentPage;
        currentPage = Math.max(0, Math.min(2, page));
        boolean changed = previousPage != currentPage;
        for (int index = 0; index < pageContainer.getChildCount(); index++) {
            View child = pageContainer.getChildAt(index);
            if (index == currentPage) {
                child.animate().cancel();
                child.setVisibility(View.VISIBLE);
                if (changed) {
                    child.setAlpha(0f);
                    child.setTranslationX(dp(currentPage > previousPage ? 14 : -14));
                    child.animate().alpha(1f).translationX(0f).setDuration(210)
                            .setInterpolator(new DecelerateInterpolator()).start();
                } else {
                    child.setAlpha(1f);
                    child.setTranslationX(0f);
                }
            } else {
                child.animate().cancel();
                child.setVisibility(View.GONE);
                child.setAlpha(1f);
                child.setTranslationX(0f);
            }
        }
        pageContainer.setCurrentPage(currentPage);
        for (int index = 0; index < navigationItems.size(); index++) {
            boolean selected = index == currentPage;
            LinearLayout item = navigationItems.get(index);
            item.setBackground(roundBackground(selected ? MINT : CARD, 11, 0));
            ((TextView) item.getChildAt(0)).setTextColor(selected ? GREEN : MUTED);
            ((TextView) item.getChildAt(1)).setTextColor(selected ? GREEN : MUTED);
        }
    }

    private TextView addStat(LinearLayout parent, String label, String initial, String note, int color) {
        LinearLayout card = column();
        card.setPadding(dp(13), dp(12), dp(12), dp(12));
        card.setBackground(roundBackground(CARD, 13, LINE));
        card.addView(text(label, 11, MUTED, false));
        TextView value = text(initial, 20, color, true);
        value.setPadding(0, dp(7), 0, dp(2));
        card.addView(value);
        card.addView(text(note, 9, MUTED, false));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() == 0) cardParams.rightMargin = dp(7);
        else cardParams.leftMargin = dp(7);
        parent.addView(card, cardParams);
        return value;
    }

    private void render() {
        monthTitle.setText(monthFormatter.format(shownMonth));
        calendarMonthTitle.setText(monthFormatter.format(shownMonth));
        records = database.loadMonth(shownMonth.getYear(), shownMonth.getMonthValue());
        renderSummary();
        renderCalendar();
        renderDays();
    }

    private void changeMonth(int amount) {
        shownMonth = shownMonth.plusMonths(amount);
        focusTodayOnRender = false;
        scrollDaysToTopOnRender = true;
        render();
    }

    private void renderSummary() {
        int daysInMonth = shownMonth.lengthOfMonth();
        int elapsedThrough;
        if (shownMonth.isBefore(today.withDayOfMonth(1))) elapsedThrough = daysInMonth;
        else if (shownMonth.getYear() == today.getYear() && shownMonth.getMonthValue() == today.getMonthValue()) elapsedThrough = today.getDayOfMonth();
        else elapsedThrough = 0;

        int actual = 0;
        int monthActual = 0;
        int expectedDays = 0;
        int fullMonthDays = 0;
        int restDays = 0;
        int pending = 0;
        int recordedDays = 0;
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = shownMonth.withDayOfMonth(day);
            AttendanceRecord record = records.get(date.toString());
            boolean workday = isWorkday(date, record);
            if (workday) fullMonthDays++;
            else restDays++;
            int paid = getPaidMinutes(record);
            if (paid >= 0) {
                monthActual += paid;
                if (workday) recordedDays++;
            }
            if (day > elapsedThrough) continue;
            if (workday) {
                expectedDays++;
                if (paid < 0) pending++;
            }
            if (paid >= 0) actual += paid;
        }
        int expected = expectedDays * TARGET_MINUTES;
        int goal = fullMonthDays * TARGET_MINUTES;
        int balance = expected - actual;
        double average = expectedDays == 0 ? 0 : (double) actual / expectedDays;
        int percent = expected == 0 ? 0 : Math.min(100, (int) Math.round(100.0 * actual / expected));
        actualHours.setText(formatDuration(actual));
        averageHours.setText(formatDuration(average));
        if (balance > 0) {
            balanceAmount.setText("欠 " + formatDuration(balance));
            balanceAmount.setTextColor(0xFFF2B978);
        } else if (balance < 0) {
            balanceAmount.setText("超出 " + formatDuration(-balance));
            balanceAmount.setTextColor(0xFFBFE4C8);
        } else {
            balanceAmount.setText("已达目标");
            balanceAmount.setTextColor(0xFFBFE4C8);
        }
        balanceDetails.setText(expectedDays + " 个工作日 × " + formatDuration(TARGET_MINUTES) + " = " + formatDuration(expected) + " 目标\n实际累计 " + formatDuration(actual));
        unitSwitch.setText(showMinutes ? "显示小时" : "显示分钟");
        balanceCaption.setText(shownMonth.getYear() == today.getYear() && shownMonth.getMonthValue() == today.getMonthValue()
                ? "截至 " + today.getMonthValue() + " 月 " + today.getDayOfMonth() + " 日（含）"
                : shownMonth.isBefore(today.withDayOfMonth(1)) ? "整月工时结算" : "未来月份，尚未开始累计");
        calendarSummary.setText("工作日 " + fullMonthDays + " 天 · 休息日 " + restDays + " 天\n已记录 " + formatDuration(monthActual) + " · 月目标 " + formatDuration(goal) + "\n已记录工作日 " + recordedDays + " 天 · 待补录 " + pending + " 天");
        progressLabel.setText(percent + "%");
        float fraction = expected == 0 ? 0 : Math.min(1f, (float) actual / expected);
        animateProgress(fraction);
        progressDays.setText((expectedDays - pending) + "/" + expectedDays + " 个工作日已记录");
        progressPending.setText(pending + " 天待补录");
        renderQuickPunchButton();
    }

    private void animateProgress(float target) {
        if (progressFill == null || progressRemainder == null) return;
        if (progressAnimator != null) progressAnimator.cancel();
        LinearLayout.LayoutParams current = (LinearLayout.LayoutParams) progressFill.getLayoutParams();
        float start = current.weight;
        progressAnimator = ValueAnimator.ofFloat(start, target);
        progressAnimator.setDuration(280);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.addUpdateListener(animator -> {
            float fraction = (float) animator.getAnimatedValue();
            LinearLayout.LayoutParams fillParams = (LinearLayout.LayoutParams) progressFill.getLayoutParams();
            fillParams.width = 0;
            fillParams.weight = fraction;
            progressFill.setLayoutParams(fillParams);
            LinearLayout.LayoutParams remainderParams = (LinearLayout.LayoutParams) progressRemainder.getLayoutParams();
            remainderParams.width = 0;
            remainderParams.weight = Math.max(0.001f, 1f - fraction);
            progressRemainder.setLayoutParams(remainderParams);
        });
        progressAnimator.start();
    }

    private void renderQuickPunchButton() {
        if (quickPunchButton == null) return;
        AttendanceRecord record = records.get(today.toString());
        boolean hasStart = record != null && hasText(record.start);
        if (!hasStart) {
            quickPunchButton.setText("上班打卡  ·  使用当前时间");
        } else if (record == null || !hasText(record.end)) {
            quickPunchButton.setText("下班打卡  ·  使用当前时间");
        } else {
            quickPunchButton.setText("更新签退  ·  使用当前时间");
        }
        quickPunchButton.setContentDescription(hasStart
                ? "点击使用当前时间签退；之后可再次点击更新签退时间"
                : "点击使用当前时间记录上班打卡");
        quickPunchButton.setOnClickListener(v -> punchCurrentTime());
    }

    private void punchCurrentTime() {
        today = LocalDate.now();
        if (shownMonth.getYear() != today.getYear() || shownMonth.getMonthValue() != today.getMonthValue()) {
            shownMonth = today.withDayOfMonth(1);
            focusTodayOnRender = true;
            render();
        }
        String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US));
        boolean signIn;
        synchronized (databaseLock) {
            AttendanceRecord record = database.loadOne(today.toString());
            signIn = record == null || !hasText(record.start);
            if (!signIn && parseTime(now, false) <= parseTime(record.start, false)) {
                Toast.makeText(this, "当前时间不晚于上班时间，请检查打卡记录", Toast.LENGTH_LONG).show();
                return;
            }
            database.save(signIn
                    ? new AttendanceRecord(today.toString(), now, null, record == null ? null : record.workday)
                    : new AttendanceRecord(today.toString(), record.start, now, record.workday));
        }
        records = database.loadMonth(shownMonth.getYear(), shownMonth.getMonthValue());
        render();
        queueSync(false);
        Toast.makeText(this, signIn ? "已记录上班时间 " + now : "已更新签退时间 " + now, Toast.LENGTH_SHORT).show();
    }

    private void renderCalendar() {
        calendarGrid.removeAllViews();
        int offset = shownMonth.withDayOfMonth(1).getDayOfWeek().getValue() - 1;
        int days = shownMonth.lengthOfMonth();
        for (int slot = 0; slot < 42; slot++) {
            int rowIndex = slot / 7;
            int columnIndex = slot % 7;
            int day = slot - offset + 1;
            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams(
                    GridLayout.spec(rowIndex), GridLayout.spec(columnIndex, 1, 1f));
            cellParams.width = 0;
            cellParams.height = dp(64);
            cellParams.setMargins(dp(2), dp(2), dp(2), dp(2));
            if (day < 1 || day > days) {
                calendarGrid.addView(new View(this), cellParams);
                continue;
            }
            LocalDate date = shownMonth.withDayOfMonth(day);
            AttendanceRecord record = records.get(date.toString());
            boolean workday = isWorkday(date, record);
            int paid = getPaidMinutes(record);
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(2), dp(4), dp(2), dp(3));
            cell.setBackground(roundBackground(workday ? MINT : 0xFFEEEFE8, 9, date.equals(today) ? GREEN : 0));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setContentDescription(date.getMonthValue() + "月" + day + "日，" + (workday ? "工作日" : "休息日") + (paid >= 0 ? "，" + formatDuration(paid) : ""));
            cell.setOnClickListener(v -> showDayDetails(date));
            TextView dayLabel = text(String.valueOf(day), 13, date.equals(today) ? GREEN : INK, true);
            dayLabel.setGravity(Gravity.CENTER);
            cell.addView(dayLabel);
            TextView status = text(workday ? "班" : "休", 8, workday ? GREEN : MUTED, true);
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(8), dp(2), dp(8), dp(2));
            status.setBackground(roundBackground(CARD, 7, 0));
            status.setClickable(true);
            status.setFocusable(true);
            status.setContentDescription("切换为" + (workday ? "休息日" : "工作日"));
            status.setOnClickListener(v -> toggleWorkday(date, record));
            cell.addView(status, marginParams(-2, -2, 0, 2, 0, 0));
            TextView hours = text(paid >= 0 ? formatDurationCompact(paid) : (record != null && (hasText(record.start) || hasText(record.end)) ? "补录" : "·"), 8,
                    paid >= 0 ? GREEN : MUTED, paid >= 0);
            hours.setGravity(Gravity.CENTER);
            if (paid >= 0) setDurationToggle(hours);
            cell.addView(hours, marginParams(-1, -2, 0, 2, 0, 0));
            calendarGrid.addView(cell, cellParams);
        }
    }

    private void renderDays() {
        int previousScrollY = daysScroll.getScrollY();
        boolean focusToday = focusTodayOnRender
                && shownMonth.getYear() == today.getYear()
                && shownMonth.getMonthValue() == today.getMonthValue();
        boolean resetScroll = scrollDaysToTopOnRender;
        daysList.removeAllViews();
        for (int day = 1; day <= shownMonth.lengthOfMonth(); day++) {
            LocalDate date = shownMonth.withDayOfMonth(day);
            AttendanceRecord record = records.get(date.toString());
            boolean workday = isWorkday(date, record);
            int paid = getPaidMinutes(record);
            boolean invalid = record != null && hasText(record.start) && hasText(record.end) && paid < 0;

            LinearLayout card = column();
            card.setPadding(dp(13), dp(11), dp(13), dp(12));
            card.setBackground(roundBackground(CARD, 12, date.equals(today) ? GREEN : LINE));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> showDayDetails(date));
            LinearLayout heading = row();
            LinearLayout dateBadge = column();
            dateBadge.setGravity(Gravity.CENTER);
            dateBadge.setBackground(roundBackground(workday ? MINT : 0xFFEFEFE9, 9, 0));
            TextView dayNumber = text(String.format(Locale.US, "%02d", day), 15, workday ? GREEN : MUTED, true);
            dayNumber.setGravity(Gravity.CENTER);
            dateBadge.addView(dayNumber);
            TextView weekday = text(weekdayName(date.getDayOfWeek()), 8, MUTED, false);
            weekday.setGravity(Gravity.CENTER);
            dateBadge.addView(weekday);
            heading.addView(dateBadge, params(40, 42));

            LinearLayout dateText = column();
            dateText.setPadding(dp(9), 0, 0, 0);
            String title = date.getMonthValue() + " 月 " + day + " 日" + (date.equals(today) ? " · 今天" : "");
            dateText.addView(text(title, 12, date.equals(today) ? GREEN : INK, true));
            TextView workdayToggle = text(workday ? "工作日" : "休息日", 9, workday ? GREEN : MUTED, true);
            workdayToggle.setPadding(dp(7), dp(2), dp(7), dp(2));
            workdayToggle.setBackground(roundBackground(workday ? MINT : 0xFFEFEFE9, 6, 0));
            workdayToggle.setClickable(true);
            workdayToggle.setFocusable(true);
            workdayToggle.setContentDescription((workday ? "工作日" : "休息日") + "，点击切换为" + (workday ? "休息日" : "工作日"));
            workdayToggle.setOnClickListener(v -> toggleWorkday(date, record));
            dateText.addView(workdayToggle, marginParams(-2, dp(21), 0, 3, 0, 0));
            TextView targetHint = text(workday ? "目标 " + formatDuration(TARGET_MINUTES) : "可补录实际工时", 9, MUTED, false);
            if (workday) setDurationToggle(targetHint);
            dateText.addView(targetHint, marginParams(-1, -2, 0, 2, 0, 0));
            heading.addView(dateText, new LinearLayout.LayoutParams(0, -2, 1));

            String totalLabel = paid >= 0 ? formatDuration(paid)
                    : invalid ? "时间需检查"
                    : record != null && hasText(record.start) && !hasText(record.end) ? "待填下班时间"
                    : record != null && !hasText(record.start) && hasText(record.end) ? "待填上班时间"
                    : "未记录";
            int totalColor = invalid ? 0xFFAE5345
                    : (record != null && (hasText(record.start) || hasText(record.end)) && paid < 0 ? ORANGE : GREEN);
            TextView dayTotal = text(totalLabel, 15, totalColor, true);
            dayTotal.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            if (paid >= 0) setDurationToggle(dayTotal);
            heading.addView(dayTotal);
            card.addView(heading);

            String punchSummary = "上班 " + (hasText(record == null ? null : record.start) ? record.start : "--:--")
                    + "  ·  下班 " + (hasText(record == null ? null : record.end) ? record.end : "--:--");
            TextView punchText = text(punchSummary, 10, MUTED, false);
            card.addView(punchText, marginParams(-1, -2, 49, 4, 18, 0));
            TextView detail = text("点击查看并编辑  ›", 9, invalid ? 0xFFAE5345 : MUTED, false);
            detail.setGravity(Gravity.RIGHT);
            card.addView(detail, marginParams(-1, -2, 0, 2, 0, 0));
            daysList.addView(card, marginParams(-1, -2, 0, 0, 0, 7));
        }
        daysScroll.post(() -> {
            if (focusToday) {
                View todayCard = daysList.getChildAt(today.getDayOfMonth() - 1);
                if (todayCard != null) daysScroll.smoothScrollTo(0, todayCard.getTop());
            } else if (resetScroll) {
                daysScroll.scrollTo(0, 0);
            } else {
                daysScroll.scrollTo(0, previousScrollY);
            }
        });
        focusTodayOnRender = false;
        scrollDaysToTopOnRender = false;
    }

    private String dayDetail(AttendanceRecord record, boolean workday, int paid, boolean invalid) {
        if (paid >= 0) return (workday ? "已记录" : "休息日补录") + " · 按计薪时段累计";
        if (invalid) return "上班和下班时间须有效，且下班时间晚于上班时间";
        if (record != null && hasText(record.start)) return "已录上班时间，请补录下班时间";
        if (record != null && hasText(record.end)) return "已录下班时间，请补录上班时间";
        return workday ? "计薪时段：08:00–12:00 · 13:30–17:30 · 18:00–24:00" : "休息日，可切换为工作日或补录实际工时";
    }

    private void showDayDetails(LocalDate date) {
        AttendanceRecord record = records.get(date.toString());
        boolean workday = isWorkday(date, record);
        int paid = getPaidMinutes(record);
        boolean invalid = record != null && hasText(record.start) && hasText(record.end) && paid < 0;
        boolean future = date.isAfter(today);

        LinearLayout content = column();
        content.setPadding(dp(20), dp(2), dp(20), dp(6));
        String status = paid >= 0 ? "当天计薪工时 " + formatDuration(paid)
                : dayDetail(record, workday, paid, invalid);
        TextView statusText = text(status, 12, invalid ? 0xFFAE5345 : (paid >= 0 ? GREEN : MUTED), true);
        statusText.setPadding(0, 0, 0, dp(12));
        if (paid >= 0) setDurationToggle(statusText);
        content.addView(statusText);

        TextView startButton = action("上班打卡     " + (hasText(record == null ? null : record.start) ? record.start : "设置时间"), false);
        startButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        startButton.setEnabled(!future);
        startButton.setAlpha(future ? .55f : 1f);
        content.addView(startButton, params(-1, 48));
        TextView endButton = action("下班打卡     " + (hasText(record == null ? null : record.end) ? record.end : "设置时间"), false);
        endButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        endButton.setEnabled(!future);
        endButton.setAlpha(future ? .55f : 1f);
        content.addView(endButton, marginParams(-1, 48, 0, 9, 0, 0));
        TextView toggleButton = action(workday ? "设为休息日" : "设为工作日", true);
        content.addView(toggleButton, marginParams(-1, 44, 0, 14, 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(date.format(DateTimeFormatter.ofPattern("M月d日  EEEE", Locale.CHINA)))
                .setView(content)
                .setPositiveButton("完成", null)
                .create();
        startButton.setOnClickListener(v -> {
            dialog.dismiss();
            showTimePicker(date, true, true);
        });
        endButton.setOnClickListener(v -> {
            dialog.dismiss();
            showTimePicker(date, false, true);
        });
        toggleButton.setOnClickListener(v -> {
            dialog.dismiss();
            toggleWorkday(date, record);
            showDayDetails(date);
        });
        dialog.show();
    }

    private void showTimePicker(LocalDate date, boolean isStart, boolean returnToDetails) {
        AttendanceRecord current = records.get(date.toString());
        String existing = current == null ? null : (isStart ? current.start : current.end);
        int minutes = parseTime(existing, !isStart);
        boolean existing24 = !isStart && "24:00".equals(existing);
        int hour = minutes >= 0 ? Math.min(23, minutes / 60) : (isStart ? 8 : 18);
        int minute = existing24 ? 59 : minutes >= 0 ? minutes % 60 : (isStart ? 30 : 0);
        int[] selected = {hour, minute};
        boolean[] endOfDay = {existing24};
        boolean[] cleared = {false};

        LinearLayout content = column();
        content.setPadding(dp(20), dp(4), dp(20), dp(8));
        TextView preview = text(existing24 ? "24:00" : String.format(Locale.US, "%02d:%02d", hour, minute), 30, GREEN, true);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, dp(4), 0, dp(4));
        content.addView(preview, params(-1, 52));
        TextView instruction = text("拖动滑块选择时间，也可用 − / + 逐格微调", 11, MUTED, false);
        instruction.setGravity(Gravity.CENTER);
        content.addView(instruction, marginParams(-1, -2, 0, 1, 0, 8));
        Runnable updatePreview = () -> {
            cleared[0] = false;
            preview.setText(endOfDay[0] ? "24:00" : String.format(Locale.US, "%02d:%02d", selected[0], selected[1]));
        };
        addTimeSlider(content, "小时", selected[0], 23, value -> {
            selected[0] = value;
            endOfDay[0] = false;
            updatePreview.run();
        });
        addTimeSlider(content, "分钟（每格 1 分钟）", selected[1], 59, value -> {
            selected[1] = value;
            endOfDay[0] = false;
            updatePreview.run();
        });
        TextView clearButton = action("清除这个时间", false);
        clearButton.setTextSize(12);
        content.addView(clearButton, marginParams(-1, 42, 0, 8, 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isStart ? "设置上班打卡时间" : "设置下班打卡时间")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .setNeutralButton(isStart ? "清除" : "24:00", (d, which) -> {
                    saveTime(date, isStart, isStart ? null : "24:00");
                    if (returnToDetails) showDayDetails(date);
                })
                .create();
        clearButton.setOnClickListener(v -> {
            dialog.dismiss();
            saveTime(date, isStart, null);
            if (returnToDetails) showDayDetails(date);
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = cleared[0] ? null : endOfDay[0] ? "24:00" : String.format(Locale.US, "%02d:%02d", selected[0], selected[1]);
            dialog.dismiss();
            saveTime(date, isStart, value);
            if (returnToDetails) showDayDetails(date);
        }));
        dialog.show();
    }

    private interface OnSliderValueChanged { void onValueChanged(int value); }

    private void addTimeSlider(LinearLayout parent, String label, int initial, int maximum, OnSliderValueChanged listener) {
        LinearLayout section = column();
        LinearLayout titleRow = row();
        titleRow.addView(text(label, 12, INK, true), new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView valueLabel = text(String.format(Locale.US, "%02d", initial), 13, GREEN, true);
        titleRow.addView(valueLabel);
        section.addView(titleRow);

        LinearLayout sliderRow = row();
        TextView decrement = action("−", false);
        decrement.setTextSize(18);
        sliderRow.addView(decrement, params(40, 40));
        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum);
        slider.setProgress(initial);
        sliderRow.addView(slider, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView increment = action("+", false);
        increment.setTextSize(17);
        sliderRow.addView(increment, params(40, 40));
        section.addView(sliderRow);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueLabel.setText(String.format(Locale.US, "%02d", progress));
                listener.onValueChanged(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        decrement.setOnClickListener(v -> slider.setProgress(Math.max(0, slider.getProgress() - 1)));
        increment.setOnClickListener(v -> slider.setProgress(Math.min(maximum, slider.getProgress() + 1)));
        parent.addView(section, marginParams(-1, -2, 0, 5, 0, 3));
    }

    private void saveTime(LocalDate date, boolean isStart, String value) {
        synchronized (databaseLock) {
            AttendanceRecord old = database.loadOne(date.toString());
            String start = old == null ? null : old.start;
            String end = old == null ? null : old.end;
            if (isStart) start = value; else end = value;
            database.save(new AttendanceRecord(date.toString(), start, end, old == null ? null : old.workday));
        }
        render();
        queueSync(false);
    }

    private void toggleWorkday(LocalDate date, AttendanceRecord old) {
        synchronized (databaseLock) {
            AttendanceRecord current = database.loadOne(date.toString());
            boolean value = !isWorkday(date, current);
            database.save(new AttendanceRecord(date.toString(), current == null ? null : current.start,
                    current == null ? null : current.end, value));
        }
        render();
        queueSync(false);
    }

    private boolean isWorkday(LocalDate date, AttendanceRecord record) {
        if (record != null && record.workday != null) return record.workday;
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private int getPaidMinutes(AttendanceRecord record) {
        if (record == null) return -1;
        int start = parseTime(record.start, false);
        int end = parseTime(record.end, true);
        if (start < 0 || end < 0 || end <= start) return -1;
        int total = 0;
        int[][] windows = {{Math.min(start, 8 * 60), 12 * 60}, {13 * 60 + 30, 17 * 60 + 30}, {18 * 60, 24 * 60}};
        for (int[] window : windows) total += Math.max(0, Math.min(end, window[1]) - Math.max(start, window[0]));
        return total;
    }

    private int parseTime(String value, boolean allow24) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) return -1;
        if (allow24 && "24:00".equals(value)) return 24 * 60;
        try {
            int hour = Integer.parseInt(value.substring(0, 2));
            int minute = Integer.parseInt(value.substring(3, 5));
            if (hour > 23 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void toggleDurationUnit() {
        showMinutes = !showMinutes;
        getPreferences(MODE_PRIVATE).edit().putBoolean("show_minutes", showMinutes).apply();
        render();
    }

    private void setDurationToggle(TextView view) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription("点击切换工时单位；当前显示" + (showMinutes ? "分钟" : "小时"));
        view.setOnClickListener(v -> {
            toggleDurationUnit();
            view.setContentDescription("点击切换工时单位；当前显示" + (showMinutes ? "分钟" : "小时"));
        });
    }

    private boolean hasWebDavConfig() {
        return !getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).getString("url", "").trim().isEmpty();
    }

    private void showWebDavSettings() {
        android.content.SharedPreferences preferences = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE);
        EditText urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setHint("https://服务器/WebDAV/工时日记.json");
        urlField.setText(preferences.getString("url", ""));
        EditText usernameField = new EditText(this);
        usernameField.setSingleLine(true);
        usernameField.setHint("用户名（可留空）");
        usernameField.setText(preferences.getString("username", ""));
        EditText passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setHint("密码或应用专用密码");
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        try {
            String encrypted = preferences.getString("password", "");
            if (!encrypted.isEmpty()) passwordField.setText(decryptSecret(encrypted));
        } catch (Exception exception) {
            Toast.makeText(this, "无法读取已保存的密码，请重新填写", Toast.LENGTH_LONG).show();
        }

        LinearLayout content = column();
        content.setPadding(dp(20), dp(2), dp(20), dp(4));
        content.addView(text("填写可读写的 WebDAV 同步文件完整地址。每个日期单独按记录内容比较；不会用修改时间判断新旧。", 11, MUTED, false));
        content.addView(text("同步地址", 11, INK, true), marginParams(-1, -2, 0, 13, 0, 0));
        content.addView(urlField, params(-1, 48));
        content.addView(text("用户名", 11, INK, true), marginParams(-1, -2, 0, 7, 0, 0));
        content.addView(usernameField, params(-1, 48));
        content.addView(text("密码", 11, INK, true), marginParams(-1, -2, 0, 7, 0, 0));
        content.addView(passwordField, params(-1, 48));
        TextView securityHint = text("支持 HTTP 和 HTTPS。HTTP 不加密登录信息，建议使用 HTTPS 或应用专用密码。", 10, ORANGE, false);
        securityHint.setPadding(0, dp(10), 0, 0);
        content.addView(securityHint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("WebDAV 设置")
                .setView(content)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除配置", null)
                .setPositiveButton("保存并同步", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String endpoint = urlField.getText().toString().trim();
                if (!(endpoint.startsWith("https://") || endpoint.startsWith("http://"))) {
                    urlField.setError("请输入 http:// 或 https:// 开头的完整文件地址");
                    return;
                }
                try {
                    URL parsed = new URL(endpoint);
                    if (parsed.getHost().isEmpty()) throw new IllegalArgumentException("缺少服务器主机名");
                    String oldEndpoint = preferences.getString("url", "");
                    android.content.SharedPreferences.Editor editor = preferences.edit()
                            .putString("url", endpoint)
                            .putString("username", usernameField.getText().toString().trim());
                    String password = passwordField.getText().toString();
                    if (password.isEmpty()) editor.remove("password");
                    else editor.putString("password", encryptSecret(password));
                    if (!endpoint.equals(oldEndpoint)) editor.remove("base_hashes").remove("last_generation");
                    editor.apply();
                    dialog.dismiss();
                    setSyncStatus("设置已保存，正在同步…");
                    queueSync(false);
                } catch (Exception exception) {
                    urlField.setError("地址或密码设置无效：" + exception.getMessage());
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                preferences.edit().remove("url").remove("username").remove("password").apply();
                dialog.dismiss();
                setSyncStatus("WebDAV 配置已清除；本机记录仍保留");
            });
        });
        dialog.show();
    }

    private String encryptSecret(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, loadSecretKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + encrypted.length);
        buffer.put((byte) iv.length).put(iv).put(encrypted);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    private String decryptSecret(String value) throws Exception {
        byte[] encoded = Base64.decode(value, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        int ivLength = buffer.get() & 0xFF;
        if (ivLength < 8 || ivLength > 16 || buffer.remaining() <= ivLength) throw new IllegalArgumentException("密码数据无效");
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, loadSecretKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey loadSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(SECRET_ALIAS)) return (SecretKey) keyStore.getKey(SECRET_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(SECRET_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private void queueSync(boolean manual) {
        queueSync(manual, false);
    }

    private void queueSync(boolean manual, boolean startup) {
        if (!hasWebDavConfig()) {
            if (manual) showWebDavSettings();
            return;
        }
        setSyncStatus("正在与 WebDAV 比对并同步…");
        syncExecutor.execute(() -> {
            SyncResult result = null;
            Exception failure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    result = synchronizeWithWebDav();
                    failure = null;
                    break;
                } catch (ConcurrentRemoteChangeException exception) {
                    failure = exception;
                } catch (Exception exception) {
                    failure = exception;
                    break;
                }
            }
            SyncResult completed = result;
            Exception error = failure;
            if (!isFinishing()) runOnUiThread(() -> {
                if (error != null) {
                    String message = "同步失败：" + error.getMessage();
                    setSyncStatus(message);
                    if (manual) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                } else if (completed != null) {
                    finishSync(completed, manual, startup);
                }
            });
        });
    }

    private void finishSync(SyncResult result, boolean manual, boolean startup) {
        if (result.pulled > 0) {
            synchronized (databaseLock) {
                records = database.loadMonth(shownMonth.getYear(), shownMonth.getMonthValue());
            }
            render();
        }
        String message;
        if (result.conflicts.size() > 0) {
            message = "发现 " + result.conflicts.size() + " 条冲突，等待你选择保留哪一边";
        } else if (result.serverRollback) {
            message = "服务器同步版本低于本机上次确认的版本；已按逐日内容比对并恢复本机数据";
        } else if (result.pulled > 0 || result.pushed > 0) {
            message = "同步完成 · 拉取 " + result.pulled + " 条 · 上传 " + result.pushed + " 条";
        } else {
            message = "同步完成 · 本机与服务器记录一致";
        }
        setSyncStatus(message);
        if (result.serverRollback && !isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle("服务器版本较旧")
                    .setMessage("服务器保存的同步版本低于本机上次确认的版本。应用已逐日比较记录内容并用本机数据修复服务器；请检查其他设备是否上传了旧备份。")
                    .setPositiveButton("知道了", null)
                    .show();
        } else if (startup && result.pushed > 0 && result.conflicts.isEmpty() && !isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle("本机记录比服务器新")
                    .setMessage("发现本机有 " + result.pushed + " 条更改尚未保存在服务器，已按记录内容自动补齐到 WebDAV。")
                    .setPositiveButton("知道了", null)
                    .show();
        }
        if (!result.conflicts.isEmpty()) showSyncConflict(result.conflicts.get(0));
        else if (manual) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setSyncStatus(String message) {
        if (syncStatusView != null) {
            syncStatusView.animate().cancel();
            syncStatusView.setText(message);
            syncStatusView.setAlpha(0.55f);
            syncStatusView.animate().alpha(1f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private SyncResult synchronizeWithWebDav() throws Exception {
        WebDavConfig config = loadWebDavConfig();
        RemoteSnapshot remote = fetchRemote(config);
        TreeMap<String, String> baseHashes = loadBaseHashes();
        TreeMap<String, String> nextBase = new TreeMap<>(baseHashes);
        TreeMap<String, AttendanceRecord> remoteRecords = new TreeMap<>(remote.records);
        ArrayList<SyncConflict> conflicts = new ArrayList<>();
        int pulled = 0;
        int pushed = 0;
        boolean serverRollback = remote.generation < getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).getLong("last_generation", 0L);
        if (remote.missing && getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).getLong("last_generation", 0L) > 0L) serverRollback = true;
        boolean needsUpload = remote.missing || serverRollback;

        synchronized (databaseLock) {
            TreeMap<String, AttendanceRecord> localRecords = new TreeMap<>(database.loadAll());
            Set<String> dates = new TreeSet<>();
            dates.addAll(baseHashes.keySet());
            dates.addAll(localRecords.keySet());
            dates.addAll(remoteRecords.keySet());
            for (String date : dates) {
                AttendanceRecord local = localRecords.get(date);
                AttendanceRecord server = remoteRecords.get(date);
                String localHash = hashRecord(date, local);
                String serverHash = hashRecord(date, server);
                String baseHash = baseHashes.get(date);
                if (sameHash(localHash, serverHash)) {
                    setBaseHash(nextBase, date, localHash);
                    continue;
                }
                if (serverRollback) {
                    if (local == null) remoteRecords.remove(date);
                    else remoteRecords.put(date, local);
                    setBaseHash(nextBase, date, localHash);
                    needsUpload = true;
                    pushed++;
                    continue;
                }
                boolean localChanged = !sameHash(localHash, baseHash);
                boolean serverChanged = !sameHash(serverHash, baseHash);
                if (localChanged && serverChanged) {
                    conflicts.add(new SyncConflict(date, local, server, localHash, serverHash));
                } else if (localChanged) {
                    if (local == null) remoteRecords.remove(date);
                    else remoteRecords.put(date, local);
                    setBaseHash(nextBase, date, localHash);
                    needsUpload = true;
                    pushed++;
                } else if (serverChanged) {
                    database.save(server == null
                            ? new AttendanceRecord(date, null, null, null)
                            : server);
                    setBaseHash(nextBase, date, serverHash);
                    pulled++;
                } else {
                    conflicts.add(new SyncConflict(date, local, server, localHash, serverHash));
                }
            }
        }

        long lastGeneration = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).getLong("last_generation", 0L);
        long finalGeneration = remote.generation;
        if (needsUpload) {
            finalGeneration = Math.max(remote.generation, lastGeneration) + 1;
            putRemote(config, remote, encodeRemote(finalGeneration, remoteRecords));
        }
        android.content.SharedPreferences preferences = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE);
        preferences.edit()
                .putString("base_hashes", encodeBaseHashes(nextBase))
                .putLong("last_generation", Math.max(lastGeneration, finalGeneration))
                .apply();
        return new SyncResult(pulled, pushed, serverRollback, conflicts);
    }

    private WebDavConfig loadWebDavConfig() throws Exception {
        android.content.SharedPreferences preferences = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE);
        String endpoint = preferences.getString("url", "").trim();
        if (endpoint.isEmpty()) throw new IllegalStateException("尚未设置 WebDAV 地址");
        String encrypted = preferences.getString("password", "");
        String password = encrypted.isEmpty() ? "" : decryptSecret(encrypted);
        return new WebDavConfig(endpoint, preferences.getString("username", ""), password);
    }

    private TreeMap<String, String> loadBaseHashes() {
        TreeMap<String, String> values = new TreeMap<>();
        String json = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).getString("base_hashes", "{}");
        try {
            JSONObject object = new JSONObject(json);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!object.isNull(key)) values.put(key, object.getString(key));
            }
        } catch (JSONException ignored) { }
        return values;
    }

    private String encodeBaseHashes(Map<String, String> hashes) throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(hashes).entrySet()) object.put(entry.getKey(), entry.getValue());
        return object.toString();
    }

    private void setBaseHash(Map<String, String> hashes, String date, String hash) {
        if (hash == null) hashes.remove(date);
        else hashes.put(date, hash);
    }

    private boolean sameHash(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String hashRecord(String date, AttendanceRecord record) throws Exception {
        if (record == null) return null;
        String workday = record.workday == null ? "" : record.workday ? "1" : "0";
        String canonical = date + "\n" + (record.start == null ? "" : record.start) + "\n"
                + (record.end == null ? "" : record.end) + "\n" + workday;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }

    private RemoteSnapshot fetchRemote(WebDavConfig config) throws Exception {
        HttpURLConnection connection = openWebDavConnection(config);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return new RemoteSnapshot(true, 0L, null, new TreeMap<>());
            if (status < 200 || status >= 300) throw new SyncException("服务器返回 HTTP " + status);
            String etag = connection.getHeaderField("ETag");
            String body = readResponseBody(connection.getInputStream());
            JSONObject root = new JSONObject(body.replace("\uFEFF", "").trim());
            if (root.optInt("schema", 0) != 1) throw new SyncException("WebDAV 文件格式不受支持或版本不兼容");
            JSONObject recordsObject = root.optJSONObject("records");
            if (recordsObject == null) throw new SyncException("服务器同步文件缺少 records 数据");
            TreeMap<String, AttendanceRecord> remoteRecords = new TreeMap<>();
            Iterator<String> keys = recordsObject.keys();
            while (keys.hasNext()) {
                String date = keys.next();
                LocalDate.parse(date);
                JSONObject item = recordsObject.optJSONObject(date);
                if (item == null) throw new SyncException("服务器记录格式无效：" + date);
                String start = blankToNull(item.isNull("start") ? null : item.optString("start", null));
                String end = blankToNull(item.isNull("end") ? null : item.optString("end", null));
                Boolean workday = item.has("workday") && !item.isNull("workday") ? item.getBoolean("workday") : null;
                if (start == null && end == null && workday == null) continue;
                remoteRecords.put(date, new AttendanceRecord(date, start, end, workday));
            }
            return new RemoteSnapshot(false, root.optLong("generation", 0L), etag, remoteRecords);
        } catch (JSONException exception) {
            throw new SyncException("服务器同步文件不是有效的工时日记 JSON", exception);
        } finally {
            connection.disconnect();
        }
    }

    private String encodeRemote(long generation, Map<String, AttendanceRecord> records) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("generation", generation);
        JSONObject recordObject = new JSONObject();
        for (Map.Entry<String, AttendanceRecord> entry : new TreeMap<>(records).entrySet()) {
            AttendanceRecord record = entry.getValue();
            JSONObject item = new JSONObject();
            item.put("start", record.start == null ? JSONObject.NULL : record.start);
            item.put("end", record.end == null ? JSONObject.NULL : record.end);
            item.put("workday", record.workday == null ? JSONObject.NULL : record.workday);
            recordObject.put(entry.getKey(), item);
        }
        root.put("records", recordObject);
        return root.toString();
    }

    private void putRemote(WebDavConfig config, RemoteSnapshot remote, String body) throws Exception {
        HttpURLConnection connection = openWebDavConnection(config);
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!remote.missing && remote.etag != null && !remote.etag.trim().isEmpty() && !remote.etag.startsWith("W/")) {
            connection.setRequestProperty("If-Match", remote.etag);
        } else if (remote.missing) {
            connection.setRequestProperty("If-None-Match", "*");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            if (status == 412) throw new ConcurrentRemoteChangeException();
            if (status < 200 || status >= 300) throw new SyncException("服务器拒绝写入（HTTP " + status + "）");
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openWebDavConnection(WebDavConfig config) throws Exception {
        URL url = new URL(config.url);
        if (!("https".equalsIgnoreCase(url.getProtocol()) || "http".equalsIgnoreCase(url.getProtocol()))) {
            throw new SyncException("WebDAV 地址必须使用 HTTP 或 HTTPS");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        if (!config.username.isEmpty()) {
            String credentials = config.username + ":" + config.password;
            connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        }
        return connection;
    }

    private String readResponseBody(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 2 * 1024 * 1024) throw new SyncException("服务器同步文件超过 2 MB 限制");
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private void showSyncConflict(SyncConflict conflict) {
        if (isFinishing()) return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("记录冲突 · " + conflict.date)
                .setMessage("本机：" + describeRecord(conflict.local) + "\n服务器：" + describeRecord(conflict.server)
                        + "\n\n两边都在上次同步后修改了这一天。请选择保留哪一份；选择本机后会把该记录同步到服务器。")
                .setPositiveButton("保留本机", null)
                .setNegativeButton("采用服务器", null)
                .setNeutralButton("稍后处理", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                resolveSyncConflict(conflict, true);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                dialog.dismiss();
                resolveSyncConflict(conflict, false);
            });
        });
        dialog.show();
    }

    private String describeRecord(AttendanceRecord record) {
        if (record == null) return "已删除";
        String day = record.workday == null ? "默认日历" : record.workday ? "工作日" : "休息日";
        return day + " · 上班 " + (record.start == null ? "--:--" : record.start)
                + " · 下班 " + (record.end == null ? "--:--" : record.end);
    }

    private void resolveSyncConflict(SyncConflict conflict, boolean keepLocal) {
        synchronized (databaseLock) {
            if (keepLocal) {
                TreeMap<String, String> hashes = loadBaseHashes();
                setBaseHash(hashes, conflict.date, conflict.serverHash);
                try {
                    getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).edit().putString("base_hashes", encodeBaseHashes(hashes)).apply();
                } catch (JSONException exception) {
                    Toast.makeText(this, "无法保存冲突处理状态", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                AttendanceRecord current = database.loadAll().get(conflict.date);
                try {
                    String currentHash = hashRecord(conflict.date, current);
                    if (!sameHash(currentHash, conflict.localHash)) {
                        Toast.makeText(this, "本机记录刚刚又被修改，请重新同步后处理", Toast.LENGTH_LONG).show();
                        queueSync(false);
                        return;
                    }
                } catch (Exception exception) {
                    Toast.makeText(this, "无法校验本机记录：" + exception.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                database.save(conflict.server == null
                        ? new AttendanceRecord(conflict.date, null, null, null)
                        : conflict.server);
            }
        }
        records = database.loadMonth(shownMonth.getYear(), shownMonth.getMonthValue());
        render();
        setSyncStatus("冲突已处理，正在同步…");
        queueSync(false);
    }

    private String formatDuration(double minutes) {
        if (showMinutes) {
            if (Math.abs(minutes - Math.rint(minutes)) < 0.0001) {
                return String.format(Locale.US, "%.0f 分钟", minutes);
            }
            return String.format(Locale.US, "%.2f 分钟", minutes);
        }
        return String.format(Locale.US, "%.3f 小时", minutes / 60.0);
    }

    private String formatDurationCompact(int minutes) {
        return showMinutes ? minutes + "分" : String.format(Locale.US, "%.3fh", minutes / 60.0);
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private String weekdayName(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "周一";
            case TUESDAY: return "周二";
            case WEDNESDAY: return "周三";
            case THURSDAY: return "周四";
            case FRIDAY: return "周五";
            case SATURDAY: return "周六";
            default: return "周日";
        }
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "工时记录-" + shownMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (requestCode == REQUEST_EXPORT) writeBackup(uri);
        else if (requestCode == REQUEST_IMPORT) confirmImport(uri);
    }

    private void writeBackup(Uri uri) {
        try (OutputStream stream = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            writer.write("\uFEFF日期,星期,工作日,上班打卡,下班打卡,计薪工时,状态\r\n");
            for (int day = 1; day <= shownMonth.lengthOfMonth(); day++) {
                LocalDate date = shownMonth.withDayOfMonth(day);
                AttendanceRecord record = records.get(date.toString());
                boolean workday = isWorkday(date, record);
                int minutes = getPaidMinutes(record);
                String status = minutes >= 0 ? "已记录" : record != null && (hasText(record.start) || hasText(record.end)) ? "时间需检查或补全" : workday ? "未打卡" : "休息日";
                String hours = minutes < 0 ? "0.000" : String.format(Locale.US, "%.3f", minutes / 60.0);
                String line = csv(date.toString()) + "," + csv(weekdayName(date.getDayOfWeek())) + "," + csv(workday ? "是" : "否") + "," + csv(value(record == null ? null : record.start)) + "," + csv(value(record == null ? null : record.end)) + "," + csv(hours) + "," + csv(status);
                writer.write(line);
                writer.write("\r\n");
            }
            Toast.makeText(this, "CSV 备份已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "保存失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmImport(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("恢复考勤记录？")
                .setMessage("导入 CSV 会覆盖其中相同日期的记录，其他日期会保留。建议先导出一份当前数据。")
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", (dialog, which) -> readBackup(uri))
                .show();
    }

    private void readBackup(Uri uri) {
        int imported = 0;
        try (InputStream stream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            synchronized (databaseLock) {
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) { line = line.replace("\uFEFF", ""); firstLine = false; }
                    ArrayList<String> cells = parseCsv(line);
                    if (cells.size() < 5 || !cells.get(0).matches("\\d{4}-\\d{2}-\\d{2}")) continue;
                    LocalDate.parse(cells.get(0));
                    String start = blankToNull(cells.get(3));
                    String end = blankToNull(cells.get(4));
                    Boolean workday = cells.size() > 2 ? "是".equals(cells.get(2)) || "yes".equalsIgnoreCase(cells.get(2)) : null;
                    database.save(new AttendanceRecord(cells.get(0), start, end, workday));
                    imported++;
                }
                records = database.loadMonth(shownMonth.getYear(), shownMonth.getMonthValue());
            }
            render();
            Toast.makeText(this, "已导入 " + imported + " 条日期记录", Toast.LENGTH_LONG).show();
            if (imported > 0) queueSync(false);
        } catch (Exception exception) {
            Toast.makeText(this, "导入失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private ArrayList<String> parseCsv(String line) {
        ArrayList<String> cells = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (character == ',' && !quoted) {
                cells.add(value.toString());
                value.setLength(0);
            } else value.append(character);
        }
        cells.add(value.toString());
        return cells;
    }

    private String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private String value(String value) { return value == null ? "" : value; }
    private String blankToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }

    private TextView action(String label, boolean primary) {
        TextView view = text(label, 11, primary ? 0xFFFFFFFF : INK, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(roundBackground(primary ? GREEN : CARD, 10, primary ? GREEN : LINE));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.animate().cancel();
                target.animate().scaleX(0.975f).scaleY(0.975f).setDuration(75).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).setDuration(130)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams layoutParams = params(width, height);
        layoutParams.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return layoutParams;
    }

    private void addSpace(LinearLayout parent, int height) {
        parent.addView(new View(this), params(1, height));
    }

    private GradientDrawable roundBackground(int fill, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radius));
        if (stroke != 0) background.setStroke(dp(1), stroke);
        return background;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private static final class SwipeFrameLayout extends FrameLayout {
        interface OnSwipeListener { void onSwipe(boolean left); }

        private final float swipeThreshold;
        private float downX;
        private float downY;
        private int currentPage;
        private OnSwipeListener listener;

        SwipeFrameLayout(Activity activity) {
            super(activity);
            swipeThreshold = 56 * activity.getResources().getDisplayMetrics().density;
        }

        void setOnSwipeListener(OnSwipeListener listener) { this.listener = listener; }
        void setCurrentPage(int currentPage) { this.currentPage = currentPage; }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float deltaX = event.getX() - downX;
                float deltaY = event.getY() - downY;
                if (Math.abs(deltaX) > swipeThreshold && Math.abs(deltaX) > Math.abs(deltaY) * 1.35f) {
                    return (deltaX < 0 && currentPage < 2) || (deltaX > 0 && currentPage > 0);
                }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float deltaX = event.getX() - downX;
                if (listener != null && Math.abs(deltaX) > swipeThreshold) listener.onSwipe(deltaX < 0);
                performClick();
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private static final class AttendanceRecord {
        final String date;
        final String start;
        final String end;
        final Boolean workday;

        AttendanceRecord(String date, String start, String end, Boolean workday) {
            this.date = date;
            this.start = start;
            this.end = end;
            this.workday = workday;
        }
    }

    private static final class WebDavConfig {
        final String url;
        final String username;
        final String password;

        WebDavConfig(String url, String username, String password) {
            this.url = url;
            this.username = username == null ? "" : username;
            this.password = password == null ? "" : password;
        }
    }

    private static final class RemoteSnapshot {
        final boolean missing;
        final long generation;
        final String etag;
        final TreeMap<String, AttendanceRecord> records;

        RemoteSnapshot(boolean missing, long generation, String etag, Map<String, AttendanceRecord> records) {
            this.missing = missing;
            this.generation = generation;
            this.etag = etag;
            this.records = new TreeMap<>(records);
        }
    }

    private static final class SyncConflict {
        final String date;
        final AttendanceRecord local;
        final AttendanceRecord server;
        final String localHash;
        final String serverHash;

        SyncConflict(String date, AttendanceRecord local, AttendanceRecord server, String localHash, String serverHash) {
            this.date = date;
            this.local = local;
            this.server = server;
            this.localHash = localHash;
            this.serverHash = serverHash;
        }
    }

    private static final class SyncResult {
        final int pulled;
        final int pushed;
        final boolean serverRollback;
        final ArrayList<SyncConflict> conflicts;

        SyncResult(int pulled, int pushed, boolean serverRollback, ArrayList<SyncConflict> conflicts) {
            this.pulled = pulled;
            this.pushed = pushed;
            this.serverRollback = serverRollback;
            this.conflicts = conflicts;
        }
    }

    private static class SyncException extends Exception {
        SyncException(String message) { super(message); }
        SyncException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class ConcurrentRemoteChangeException extends SyncException {
        ConcurrentRemoteChangeException() { super("服务器文件在同步过程中被另一台设备修改，请重试"); }
    }

    private static final class AttendanceDatabase extends SQLiteOpenHelper {
        AttendanceDatabase(Activity activity) { super(activity, "attendance.db", null, 1); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE attendance (date TEXT PRIMARY KEY NOT NULL, start_time TEXT, end_time TEXT, workday INTEGER)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

        Map<String, AttendanceRecord> loadMonth(int year, int month) {
            String prefix = String.format(Locale.US, "%04d-%02d-", year, month);
            HashMap<String, AttendanceRecord> result = new HashMap<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query("attendance", new String[]{"date", "start_time", "end_time", "workday"}, "date LIKE ?", new String[]{prefix + "%"}, null, null, "date ASC")) {
                while (cursor.moveToNext()) {
                    Boolean workday = cursor.isNull(3) ? null : cursor.getInt(3) != 0;
                    AttendanceRecord record = new AttendanceRecord(cursor.getString(0), cursor.getString(1), cursor.getString(2), workday);
                    result.put(record.date, record);
                }
            }
            return result;
        }

        Map<String, AttendanceRecord> loadAll() {
            HashMap<String, AttendanceRecord> result = new HashMap<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query("attendance", new String[]{"date", "start_time", "end_time", "workday"}, null, null, null, null, "date ASC")) {
                while (cursor.moveToNext()) {
                    Boolean workday = cursor.isNull(3) ? null : cursor.getInt(3) != 0;
                    AttendanceRecord record = new AttendanceRecord(cursor.getString(0), cursor.getString(1), cursor.getString(2), workday);
                    result.put(record.date, record);
                }
            }
            return result;
        }

        AttendanceRecord loadOne(String date) {
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query("attendance", new String[]{"date", "start_time", "end_time", "workday"}, "date = ?", new String[]{date}, null, null, null)) {
                if (!cursor.moveToFirst()) return null;
                Boolean workday = cursor.isNull(3) ? null : cursor.getInt(3) != 0;
                return new AttendanceRecord(cursor.getString(0), cursor.getString(1), cursor.getString(2), workday);
            }
        }

        void save(AttendanceRecord record) {
            SQLiteDatabase db = getWritableDatabase();
            if (!hasValue(record.start) && !hasValue(record.end) && record.workday == null) {
                db.delete("attendance", "date = ?", new String[]{record.date});
                return;
            }
            ContentValues values = new ContentValues();
            values.put("date", record.date);
            if (record.start == null) values.putNull("start_time"); else values.put("start_time", record.start);
            if (record.end == null) values.putNull("end_time"); else values.put("end_time", record.end);
            if (record.workday == null) values.putNull("workday"); else values.put("workday", record.workday ? 1 : 0);
            db.insertWithOnConflict("attendance", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        private static boolean hasValue(String value) { return value != null && !value.trim().isEmpty(); }
    }
}
