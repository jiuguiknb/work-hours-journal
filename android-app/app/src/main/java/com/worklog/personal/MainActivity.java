package com.worklog.personal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA);
    private LocalDate shownMonth = LocalDate.now().withDayOfMonth(1);
    private LocalDate today;
    private AttendanceDatabase database;
    private Map<String, AttendanceRecord> records = new HashMap<>();
    private LinearLayout daysList;
    private TextView monthTitle;
    private TextView actualHours;
    private TextView averageHours;
    private TextView shortfallHours;
    private TextView monthlyGoal;
    private TextView progressLabel;
    private LinearLayout progressTrack;
    private View progressFill;
    private View progressRemainder;
    private TextView progressDays;
    private TextView progressPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        today = LocalDate.now();
        database = new AttendanceDatabase(this);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(createPage());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        today = LocalDate.now();
        if (monthTitle != null) render();
    }

    private View createPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout page = column();
        page.setPadding(dp(18), dp(18), dp(18), dp(34));
        scroll.addView(page);

        LinearLayout top = row();
        TextView brand = text("▦  工时日记", 14, GREEN, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView export = action("导出备份", true);
        export.setOnClickListener(v -> exportBackup());
        top.addView(export, marginParams(-2, 42, 6, 0, 0, 0));
        TextView backupButton = action("恢复备份", false);
        backupButton.setOnClickListener(v -> chooseBackup());
        top.addView(backupButton, params(-2, 42));
        page.addView(top);

        addSpace(page, 22);
        page.addView(text("MY WORK LOG", 10, GREEN, true));
        TextView headline = text("把每一天，记得清清楚楚。", 25, INK, true);
        headline.setPadding(0, dp(5), 0, dp(2));
        page.addView(headline);
        page.addView(text("记录保存在手机本地，可随时导出备份。", 12, MUTED, false));

        addSpace(page, 17);
        LinearLayout monthRow = row();
        TextView previous = action("‹", false);
        previous.setTextSize(21);
        previous.setOnClickListener(v -> { shownMonth = shownMonth.minusMonths(1); render(); });
        monthRow.addView(previous, params(44, 42));
        monthTitle = text("", 15, INK, true);
        monthTitle.setGravity(Gravity.CENTER);
        monthRow.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView next = action("›", false);
        next.setTextSize(21);
        next.setOnClickListener(v -> { shownMonth = shownMonth.plusMonths(1); render(); });
        monthRow.addView(next, params(44, 42));
        TextView todayButton = action("今天", true);
        todayButton.setOnClickListener(v -> { shownMonth = today.withDayOfMonth(1); render(); });
        monthRow.addView(todayButton, marginParams(-2, 42, 8, 0, 0, 0));
        page.addView(monthRow);

        addSpace(page, 13);
        LinearLayout statsTop = row();
        actualHours = addStat(statsTop, "已记工时", "0 小时", "本月计薪时段", GREEN);
        averageHours = addStat(statsTop, "工作日均工时", "0 小时", "按已过工作日平均", GREEN);
        page.addView(statsTop);
        LinearLayout statsBottom = row();
        shortfallHours = addStat(statsBottom, "当前欠工时", "0 小时", "截至今天的目标差额", ORANGE);
        monthlyGoal = addStat(statsBottom, "本月目标", "0 小时", "工作日 × 8 小时", GREEN);
        LinearLayout.LayoutParams statsBottomParams = params(-1, -2);
        statsBottomParams.topMargin = dp(9);
        page.addView(statsBottom, statsBottomParams);

        addSpace(page, 12);
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
        page.addView(progressCard, marginParams(-1, -2, 0, 12, 0, 0));

        addSpace(page, 24);
        page.addView(text("每日记录", 17, INK, true));
        TextView tip = text("按实际上下班时间记录 · 可提前打卡 · 下班可填至 24:00", 11, MUTED, false);
        tip.setPadding(0, dp(3), 0, dp(10));
        page.addView(tip);
        daysList = column();
        page.addView(daysList);

        addSpace(page, 19);
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
        TextView localHint = text("数据在手机本地 SQLite 数据库中 · 换机前请先导出 CSV", 10, MUTED, false);
        localHint.setGravity(Gravity.CENTER);
        page.addView(localHint);
        return scroll;
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
        records = database.loadMonth(shownMonth.getYear(), shownMonth.getMonthValue());
        renderSummary();
        renderDays();
    }

    private void renderSummary() {
        int daysInMonth = shownMonth.lengthOfMonth();
        int elapsedThrough;
        if (shownMonth.isBefore(today.withDayOfMonth(1))) elapsedThrough = daysInMonth;
        else if (shownMonth.getYear() == today.getYear() && shownMonth.getMonthValue() == today.getMonthValue()) elapsedThrough = today.getDayOfMonth();
        else elapsedThrough = 0;

        int actual = 0;
        int expectedDays = 0;
        int fullMonthDays = 0;
        int pending = 0;
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = shownMonth.withDayOfMonth(day);
            AttendanceRecord record = records.get(date.toString());
            boolean workday = isWorkday(date, record);
            if (workday) fullMonthDays++;
            if (day > elapsedThrough) continue;
            int paid = getPaidMinutes(record);
            if (workday) {
                expectedDays++;
                if (paid < 0) pending++;
            }
            if (paid >= 0) actual += paid;
        }
        int expected = expectedDays * TARGET_MINUTES;
        int goal = fullMonthDays * TARGET_MINUTES;
        int deficit = Math.max(0, expected - actual);
        double average = expectedDays == 0 ? 0 : (double) actual / expectedDays;
        int percent = expected == 0 ? 0 : Math.min(100, (int) Math.round(100.0 * actual / expected));
        actualHours.setText(formatHours(actual) + " 小时");
        averageHours.setText(formatHours((int) Math.round(average)) + " 小时");
        shortfallHours.setText(formatHours(deficit) + " 小时");
        monthlyGoal.setText(formatHours(goal) + " 小时");
        progressLabel.setText(percent + "%");
        float fraction = expected == 0 ? 0 : Math.min(1f, (float) actual / expected);
        LinearLayout.LayoutParams fillParams = (LinearLayout.LayoutParams) progressFill.getLayoutParams();
        fillParams.width = 0;
        fillParams.weight = fraction;
        progressFill.setLayoutParams(fillParams);
        LinearLayout.LayoutParams remainderParams = (LinearLayout.LayoutParams) progressRemainder.getLayoutParams();
        remainderParams.width = 0;
        remainderParams.weight = Math.max(0.001f, 1f - fraction);
        progressRemainder.setLayoutParams(remainderParams);
        progressDays.setText((expectedDays - pending) + "/" + expectedDays + " 个工作日已记录");
        progressPending.setText(pending + " 天待补录");
    }

    private void renderDays() {
        daysList.removeAllViews();
        for (int day = 1; day <= shownMonth.lengthOfMonth(); day++) {
            LocalDate date = shownMonth.withDayOfMonth(day);
            AttendanceRecord record = records.get(date.toString());
            boolean workday = isWorkday(date, record);
            int paid = getPaidMinutes(record);
            boolean invalid = record != null && hasText(record.start) && hasText(record.end) && paid < 0;

            LinearLayout card = column();
            card.setPadding(dp(13), dp(11), dp(13), dp(12));
            card.setBackground(roundBackground(CARD, 12, LINE));
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
            dateText.addView(text(date.getMonthValue() + " 月 " + day + " 日", 12, INK, true));
            dateText.addView(text(workday ? "工作日 · 目标 8 小时" : "休息日", 10, MUTED, false), marginParams(-1, -2, 0, 3, 0, 0));
            heading.addView(dateText, new LinearLayout.LayoutParams(0, -2, 1));

            String totalLabel = paid >= 0 ? formatHours(paid) + " 小时"
                    : invalid ? "时间需检查"
                    : record != null && hasText(record.start) && !hasText(record.end) ? "待填下班时间"
                    : record != null && !hasText(record.start) && hasText(record.end) ? "待填上班时间"
                    : "未记录";
            int totalColor = invalid ? 0xFFAE5345
                    : (record != null && (hasText(record.start) || hasText(record.end)) && paid < 0 ? ORANGE : GREEN);
            TextView dayTotal = text(totalLabel, 15, totalColor, true);
            dayTotal.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
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
        String status = paid >= 0 ? "当天计薪工时 " + formatHours(paid) + " 小时"
                : dayDetail(record, workday, paid, invalid);
        TextView statusText = text(status, 12, invalid ? 0xFFAE5345 : (paid >= 0 ? GREEN : MUTED), true);
        statusText.setPadding(0, 0, 0, dp(12));
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
        int hour = minutes >= 0 ? minutes / 60 : (isStart ? 8 : 18);
        int minute = minutes >= 0 ? minutes % 60 : (isStart ? 30 : 0);
        TimePickerDialog dialog = new TimePickerDialog(this, (picker, selectedHour, selectedMinute) -> {
            String value = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute);
            saveTime(date, isStart, value);
            if (returnToDetails) showDayDetails(date);
        }, hour, minute, true);
        dialog.setTitle(isStart ? "设置上班打卡时间" : "设置下班打卡时间");
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "清除", (d, which) -> {
            saveTime(date, isStart, null);
            if (returnToDetails) showDayDetails(date);
        });
        if (!isStart) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "24:00", (d, which) -> {
                saveTime(date, false, "24:00");
                if (returnToDetails) showDayDetails(date);
            });
        }
        dialog.show();
    }

    private void saveTime(LocalDate date, boolean isStart, String value) {
        AttendanceRecord old = records.get(date.toString());
        String start = old == null ? null : old.start;
        String end = old == null ? null : old.end;
        if (isStart) start = value; else end = value;
        database.save(new AttendanceRecord(date.toString(), start, end, old == null ? null : old.workday));
        render();
    }

    private void toggleWorkday(LocalDate date, AttendanceRecord old) {
        boolean value = !isWorkday(date, old);
        database.save(new AttendanceRecord(date.toString(), old == null ? null : old.start, old == null ? null : old.end, value));
        render();
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

    private String formatHours(int minutes) {
        return String.format(Locale.US, "%.1f", minutes / 60.0);
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
                String hours = minutes < 0 ? "0.00" : String.format(Locale.US, "%.2f", minutes / 60.0);
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
            render();
            Toast.makeText(this, "已导入 " + imported + " 条日期记录", Toast.LENGTH_LONG).show();
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
