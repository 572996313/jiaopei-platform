package com.cornerstone.module.education;

import com.cornerstone.module.education.fee.dto.FeeLedgerItemView;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder.ItemRef;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder.StudentBlock;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收费出勤表矩阵导出：把生成的 xlsx 读回来核对版式与数值。
 *
 * <p>这张表是给园长看的，块高、合并、小计、总计任何一处偏了都会让整张表失去可读性，
 * 所以不看 builder 的入参而看文件本身。样例取源表的典型情形：保教费按 5.5 个月、
 * 8 月半月 750、伙食费按天、项目 3 个（延时费只在配置里有）。</p>
 */
class FeeMatrixExportTest {

    private static final long ITEM_TUITION = 1L;
    private static final long ITEM_MEAL = 2L;
    private static final long ITEM_DELAY = 3L;

    private static final int COL_ITEM = 5;
    private static final int COL_MONTHS = 6;
    private static final int COL_STANDARD = 7;
    private static final int COL_RECEIVABLE = 10;
    private static final int COL_PAID = 11;
    private static final int COL_UNPAID = 12;
    private static final int COL_ATTENDANCE = 13;
    private static final int COL_FIRST_MONTH = 14;
    private static final int COL_TOTAL_OCCURRENCE = 20;
    private static final int COL_BALANCE = 21;

    @Test
    void 每个学员块高三加二且项目一拆成收费行与发生行() throws IOException {
        List<List<String>> text = textRows(build());

        // 表头：学号…出勤天数 + 6 个月 + 合计发生/结余/备注
        assertEquals("8月", text.get(2).get(COL_FIRST_MONTH));
        assertEquals("1月", text.get(2).get(COL_FIRST_MONTH + 5));
        assertEquals("合计发生", text.get(2).get(COL_TOTAL_OCCURRENCE));
        assertEquals("结余", text.get(2).get(COL_BALANCE));

        // 学员 A 的块起于第 4 行（下标 3），高 5 行
        assertEquals("S001", text.get(3).get(0));
        assertEquals("张三", text.get(3).get(1));
        assertEquals("保教费", text.get(3).get(COL_ITEM));
        assertEquals("伙食费", text.get(5).get(COL_ITEM));
        // 延时费没建台账行，但配置里存在，必须只写项目名占位（源表的延时费行）
        assertEquals("延时费", text.get(6).get(COL_ITEM));
        assertEquals("小计", text.get(7).get(COL_ITEM));
        // 学员 B 紧随其后
        assertEquals("S002", text.get(8).get(0));

        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("收费出勤表", sheet.getSheetName());
            // 学号与出勤天数在学员块内纵向合并
            assertTrue(sheet.getMergedRegions().stream().anyMatch(region ->
                    region.getFirstRow() == 3 && region.getLastRow() == 7
                        && region.getFirstColumn() == 0 && region.getLastColumn() == 0),
                "学号应在学员块内纵向合并");
            assertTrue(sheet.getMergedRegions().stream().anyMatch(region ->
                    region.getFirstRow() == 3 && region.getLastRow() == 7
                        && region.getFirstColumn() == COL_ATTENDANCE
                        && region.getLastColumn() == COL_ATTENDANCE),
                "出勤天数应在学员块内纵向合并");
        }
    }

    @Test
    void 收费与发生额按源表口径写入() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(0);

            // 保教费收费行：5.5 个月 × 1500 = 8250
            assertEquals(5.5, number(sheet, 3, COL_MONTHS), 0.001);
            assertEquals(1500.0, number(sheet, 3, COL_STANDARD), 0.001);
            assertEquals(8250.0, number(sheet, 3, COL_RECEIVABLE), 0.001);
            assertEquals(8250.0, number(sheet, 3, COL_PAID), 0.001);
            assertEquals(0.0, number(sheet, 3, COL_UNPAID), 0.001);
            assertEquals(90.0, number(sheet, 3, COL_ATTENDANCE), 0.001);

            // 保教费发生行：8 月半月 750，9 月起整月 1500
            assertEquals(750.0, number(sheet, 4, COL_FIRST_MONTH), 0.001);
            assertEquals(1500.0, number(sheet, 4, COL_FIRST_MONTH + 1), 0.001);

            // 伙食费：按天，8 月 18 天 × 30 = 540
            assertEquals(540.0, number(sheet, 5, COL_FIRST_MONTH), 0.001);
            assertEquals(600.0, number(sheet, 5, COL_FIRST_MONTH + 1), 0.001);

            // 小计：合计发生 8250 + 1140 = 9390；结余 8250 − 9390 = −1140
            assertEquals(9390.0, number(sheet, 7, COL_TOTAL_OCCURRENCE), 0.001);
            assertEquals(-1140.0, number(sheet, 7, COL_BALANCE), 0.001);
        }
    }

    @Test
    void 表尾与学员块同构并带总计() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(0);

            assertEquals("合计", text(sheet, 13, 0));
            assertEquals("保教费", text(sheet, 13, COL_ITEM));
            // 保教费 8 月 = 750(甲) + 500(乙) = 1250；9~1 月每月 = 1500 + 1000 = 2500
            assertEquals(1250.0, number(sheet, 14, COL_FIRST_MONTH), 0.001);
            assertEquals(2500.0, number(sheet, 14, COL_FIRST_MONTH + 1), 0.001);
            // 伙食费 8 月 540，9 月 600 + 600 = 1200
            assertEquals(540.0, number(sheet, 15, COL_FIRST_MONTH), 0.001);
            assertEquals(1200.0, number(sheet, 15, COL_FIRST_MONTH + 1), 0.001);
            assertEquals("延时费", text(sheet, 16, COL_ITEM));

            assertEquals("总计", text(sheet, 17, 0));
            assertEquals(14250.0, number(sheet, 17, COL_RECEIVABLE), 0.001);
            assertEquals(14250.0, number(sheet, 17, COL_PAID), 0.001);
            assertEquals(15490.0, number(sheet, 17, COL_TOTAL_OCCURRENCE), 0.001);
            assertEquals(-1240.0, number(sheet, 17, COL_BALANCE), 0.001);
        }
    }

    @Test
    void 月份列由学期起止动态生成而不是写死六个月() throws IOException {
        byte[] workbook = FeeMatrixExportBuilder.build("示例幼儿园", "国兴校区", "雅趣小班", "26 年下半年",
            List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            List.of(new ItemRef(ITEM_TUITION, "保教费")),
            List.of(studentA()));
        try (XSSFWorkbook book = open(workbook)) {
            Sheet sheet = book.getSheetAt(0);
            assertEquals("9月", text(sheet, 2, COL_FIRST_MONTH));
            assertEquals("10月", text(sheet, 2, COL_FIRST_MONTH + 1));
            // 两个月时「合计发生」紧跟在第 16 列
            assertEquals("合计发生", text(sheet, 2, COL_FIRST_MONTH + 2));
        }
    }

    // ------------------------------------------------------------------ 样例数据

    private byte[] build() {
        return FeeMatrixExportBuilder.build("示例幼儿园", "国兴校区", "雅趣小班", "26 年下半年",
            List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10),
                YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)),
            List.of(new ItemRef(ITEM_TUITION, "保教费"),
                new ItemRef(ITEM_MEAL, "伙食费"),
                new ItemRef(ITEM_DELAY, "延时费")),
            List.of(studentA(), studentB()));
    }

    private StudentBlock studentA() {
        List<FeeLedgerItemView> items = List.of(
            item(11L, ITEM_TUITION, "保教费", "monthly",
                "5.5", "1500", "0", "0", "0", "8250", "8250", "8250", "0"),
            item(12L, ITEM_MEAL, "伙食费", "daily",
                "5.5", "0", "30", "0", "0", "0", "0", "1140", "-1140"));
        List<FeeMonthlyView> monthly = new ArrayList<>();
        monthly.add(monthly(101L, 11L, ITEM_TUITION, "保教费", "monthly", 2026, 8, "750"));
        for (int month = 9; month <= 12; month++) {
            monthly.add(monthly((long) (100 + month), 11L, ITEM_TUITION, "保教费", "monthly", 2026, month, "1500"));
        }
        monthly.add(monthly(201L, 11L, ITEM_TUITION, "保教费", "monthly", 2027, 1, "1500"));
        monthly.add(monthly(202L, 12L, ITEM_MEAL, "伙食费", "daily", 2026, 8, "540"));
        monthly.add(monthly(203L, 12L, ITEM_MEAL, "伙食费", "daily", 2026, 9, "600"));
        return new StudentBlock("S001", "张三", "张父", "13800000001", "13900000001", "上学期有半托",
            new BigDecimal("90"), items, monthly,
            money("8250"), money("8250"), money("0"), money("9390"), money("-1140"));
    }

    private StudentBlock studentB() {
        List<FeeLedgerItemView> items = List.of(
            item(21L, ITEM_TUITION, "保教费", "monthly",
                "6", "1000", "0", "0", "0", "6000", "6000", "5500", "500"),
            item(22L, ITEM_MEAL, "伙食费", "daily",
                "6", "0", "30", "0", "0", "0", "0", "600", "-600"));
        List<FeeMonthlyView> monthly = new ArrayList<>();
        monthly.add(monthly(301L, 21L, ITEM_TUITION, "保教费", "monthly", 2026, 8, "500"));
        for (int month = 9; month <= 12; month++) {
            monthly.add(monthly((long) (300 + month), 21L, ITEM_TUITION, "保教费", "monthly", 2026, month, "1000"));
        }
        monthly.add(monthly(401L, 21L, ITEM_TUITION, "保教费", "monthly", 2027, 1, "1000"));
        monthly.add(monthly(402L, 22L, ITEM_MEAL, "伙食费", "daily", 2026, 9, "600"));
        return new StudentBlock("S002", "李四", "李母", "13800000002", null, null,
            new BigDecimal("60"), items, monthly,
            money("6000"), money("6000"), money("0"), money("6100"), money("-100"));
    }

    private FeeLedgerItemView item(Long id, Long feeItemId, String name, String billingMode,
                                   String months, String standard, String daily, String opening,
                                   String deduction, String receivable, String paid,
                                   String totalOccurrence, String balance) {
        BigDecimal unpaid = money(receivable).subtract(money(paid));
        return new FeeLedgerItemView(id, feeItemId, "F" + feeItemId, name, billingMode,
            money(months), money(standard), money(daily), money(opening), money(deduction),
            money(receivable), money(paid), unpaid,
            money(totalOccurrence), money(balance), "PRICE", null);
    }

    private FeeMonthlyView monthly(Long id, Long ledgerItemId, Long feeItemId, String name,
                                   String billingMode, int year, int month, String amount) {
        return new FeeMonthlyView(id, ledgerItemId, feeItemId, name, billingMode,
            LocalDate.of(year, month, 1), new BigDecimal("1.00"),
            money(amount), money(amount), false, null);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ 读回工具

    private XSSFWorkbook open(byte[] workbook) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(workbook));
    }

    private List<List<String>> textRows(byte[] workbook) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook book = open(workbook)) {
            Sheet sheet = book.getSheetAt(0);
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> values = new ArrayList<>();
                int lastColumn = row == null ? -1 : row.getLastCellNum();
                for (int column = 0; column < lastColumn; column++) {
                    values.add(text(sheet, rowIndex, column));
                }
                rows.add(values);
            }
        }
        return rows;
    }

    private String text(Sheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        assertNotNull(row, "行 " + rowIndex + " 不存在");
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private double number(Sheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        assertNotNull(row, "行 " + rowIndex + " 不存在");
        Cell cell = row.getCell(column);
        assertNotNull(cell, "单元格 " + rowIndex + "," + column + " 不存在");
        return cell.getNumericCellValue();
    }
}
