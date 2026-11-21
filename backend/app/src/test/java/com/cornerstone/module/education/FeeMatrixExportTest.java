package com.cornerstone.module.education;

import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import com.cornerstone.module.education.fee.dto.FeeLedgerItemView;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder.ItemRef;
import com.cornerstone.module.education.fee.support.FeeMatrixExportBuilder.ParentContact;
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
 * 收费出勤表导出：拆成「家长情况 / 收费内容 / 出勤明细」三个 sheet 后，把文件读回来核对版式与数值。
 *
 * <p>三块表各有各的合并规则：家长块按联系人数纵向合并，收费块按项目数 + 小计合并，
 * 出勤块固定「出勤天数」占第一行。任何一处偏移都会让财务对不上，所以不看 builder 入参，
 * 只看生成出来的单元格。</p>
 */
class FeeMatrixExportTest {

    private static final long ITEM_TUITION = 1L;
    private static final long ITEM_MEAL = 2L;
    private static final long ITEM_DELAY = 3L;

    private static final int HEADER_ROW = 2;
    private static final int MONTH_START_COLUMN = 4;
    private static final int TOTAL_COLUMN = 10;

    @Test
    void 拆成家长收费出勤三个sheet且每个都带班级与单位() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertEquals("家长情况", workbook.getSheetAt(0).getSheetName());
            assertEquals("收费内容", workbook.getSheetAt(1).getSheetName());
            assertEquals("出勤明细", workbook.getSheetAt(2).getSheetName());

            assertEquals("国兴校区 雅趣小班 26 年下半年 家长情况表", text(workbook.getSheetAt(0), 0, 0));
            assertEquals("国兴校区 雅趣小班 26 年下半年 收费内容表", text(workbook.getSheetAt(1), 0, 0));
            assertEquals("国兴校区 雅趣小班 26 年下半年 出勤费用表", text(workbook.getSheetAt(2), 0, 0));

            int[] lastColumns = {6, 11, TOTAL_COLUMN};
            for (int index = 0; index < lastColumns.length; index++) {
                Sheet sheet = workbook.getSheetAt(index);
                assertEquals("雅趣小班", text(sheet, 1, 0), "第二行左侧应是班级");
                assertEquals("单位：元", text(sheet, 1, lastColumns[index]), "第二行右侧应是单位");
            }
        }
    }

    @Test
    void 家长情况把全部联系人列出来并标记主联系人() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("学号", text(sheet, HEADER_ROW, 0));
            assertEquals("家长关系", text(sheet, HEADER_ROW, 2));
            assertEquals("支付宝绑定电话", text(sheet, HEADER_ROW, 5));
            assertEquals("主联系人", text(sheet, HEADER_ROW, 6));

            // 张三有父亲、母亲两行，主联系人排在前面并带支付宝绑定电话
            assertEquals("S001", text(sheet, 3, 0));
            assertEquals("张三", text(sheet, 3, 1));
            assertEquals("父亲", text(sheet, 3, 2));
            assertEquals("张父", text(sheet, 3, 3));
            assertEquals("13800000001", text(sheet, 3, 4));
            assertEquals("13900000001", text(sheet, 3, 5));
            assertEquals("是", text(sheet, 3, 6));

            assertEquals("母亲", text(sheet, 4, 2));
            assertEquals("张母", text(sheet, 4, 3));
            assertEquals("13800000002", text(sheet, 4, 4));
            assertEquals("", text(sheet, 4, 5));
            assertEquals("", text(sheet, 4, 6));

            // 李四只有一个联系人，不产生多余空行
            assertEquals("S002", text(sheet, 5, 0));
            assertEquals("母亲", text(sheet, 5, 2));
            assertEquals("是", text(sheet, 5, 6));

            assertTrue(hasMerge(sheet, 3, 4, 0, 0), "学号应在张三的家长块内纵向合并");
            assertTrue(hasMerge(sheet, 3, 4, 1, 1), "姓名应在张三的家长块内纵向合并");
        }
    }

    @Test
    void 收费内容按项目行加小计且结余与备注都在第二部分() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(1);
            assertEquals("收费内容", text(sheet, HEADER_ROW, 2));
            assertEquals("结余", text(sheet, HEADER_ROW, 10));
            assertEquals("备注", text(sheet, HEADER_ROW, 11));

            // 张三：保教费、伙食费、延时费（未建行占位）、小计
            assertEquals("保教费", text(sheet, 3, 2));
            assertEquals(5.5, number(sheet, 3, 3), 0.001);
            assertEquals(1500.0, number(sheet, 3, 4), 0.001);
            assertEquals(8250.0, number(sheet, 3, 7), 0.001);
            assertEquals(9000.0, number(sheet, 3, 8), 0.001);
            assertEquals(-750.0, number(sheet, 3, 9), 0.001);
            assertEquals(8250.0, number(sheet, 3, 10), 0.001);

            assertEquals("伙食费", text(sheet, 4, 2));
            assertEquals(600.0, number(sheet, 4, 4), 0.001, "收费内容的收费标准按月标准展示");
            assertEquals(3600.0, number(sheet, 4, 7), 0.001);
            assertEquals(0.0, number(sheet, 4, 9), 0.001);
            assertEquals(3330.0, number(sheet, 4, 10), 0.001);

            assertEquals("延时费", text(sheet, 5, 2));
            assertEquals("", text(sheet, 5, 4));

            assertEquals("小计", text(sheet, 6, 2));
            assertEquals(11850.0, number(sheet, 6, 7), 0.001);
            assertEquals(12600.0, number(sheet, 6, 8), 0.001);
            assertEquals(-750.0, number(sheet, 6, 9), 0.001);
            assertEquals(11580.0, number(sheet, 6, 10), 0.001);
            assertEquals("上学期有半托", text(sheet, 3, 11));
            assertTrue(hasMerge(sheet, 3, 6, 11, 11), "备注应在学员块内纵向合并");

            assertEquals("S002", text(sheet, 7, 0));
            assertEquals("小计", text(sheet, 10, 2));

            assertEquals("总计", text(sheet, 11, 0));
            assertEquals(21450.0, number(sheet, 11, 7), 0.001);
            assertEquals(22200.0, number(sheet, 11, 8), 0.001);
            assertEquals(-750.0, number(sheet, 11, 9), 0.001);
            assertEquals(19580.0, number(sheet, 11, 10), 0.001);
        }
    }

    @Test
    void 出勤明细第一行固定出勤天数下面才是费用项() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(2);
            assertEquals("项目", text(sheet, HEADER_ROW, 2));
            assertEquals("日标准", text(sheet, HEADER_ROW, 3));
            assertEquals("8月", text(sheet, HEADER_ROW, MONTH_START_COLUMN));
            assertEquals("合计发生", text(sheet, HEADER_ROW, TOTAL_COLUMN));

            // 张三块：出勤天数 → 保教费 → 伙食费 → 延时费 → 小计
            assertEquals("S001", text(sheet, 3, 0));
            assertEquals("出勤天数", text(sheet, 3, 2));
            assertEquals(9.0, number(sheet, 3, MONTH_START_COLUMN), 0.001);
            assertEquals(9.0, number(sheet, 3, TOTAL_COLUMN), 0.001);
            assertEquals(0.0, number(sheet, 3, MONTH_START_COLUMN + 1), 0.001);

            assertEquals("保教费", text(sheet, 4, 2));
            assertEquals("", text(sheet, 4, 3), "按月项目不显示日标准");
            assertEquals(750.0, number(sheet, 4, MONTH_START_COLUMN), 0.001);
            assertEquals(750.0, number(sheet, 4, TOTAL_COLUMN), 0.001);

            assertEquals("伙食费", text(sheet, 5, 2));
            assertEquals(30.0, number(sheet, 5, 3), 0.001);
            assertEquals(270.0, number(sheet, 5, MONTH_START_COLUMN), 0.001);

            assertEquals("延时费", text(sheet, 6, 2));
            assertEquals("", text(sheet, 6, MONTH_START_COLUMN));

            assertEquals("小计", text(sheet, 7, 2));
            assertEquals(1020.0, number(sheet, 7, MONTH_START_COLUMN), 0.001);
            assertEquals(1020.0, number(sheet, 7, TOTAL_COLUMN), 0.001);

            // 李四块：9 月有出勤
            assertEquals("S002", text(sheet, 8, 0));
            assertEquals(10.0, number(sheet, 8, MONTH_START_COLUMN), 0.001);
            assertEquals(20.0, number(sheet, 8, MONTH_START_COLUMN + 1), 0.001);
            assertEquals(30.0, number(sheet, 8, TOTAL_COLUMN), 0.001);
            assertEquals("小计", text(sheet, 12, 2));
            assertEquals(1600.0, number(sheet, 12, TOTAL_COLUMN), 0.001);
        }
    }

    @Test
    void 出勤明细表尾按项目汇总再给总计() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            Sheet sheet = workbook.getSheetAt(2);

            assertEquals("合计", text(sheet, 13, 0));
            assertEquals("出勤天数", text(sheet, 13, 2));
            assertEquals(19.0, number(sheet, 13, MONTH_START_COLUMN), 0.001);
            assertEquals(20.0, number(sheet, 13, MONTH_START_COLUMN + 1), 0.001);
            assertEquals(39.0, number(sheet, 13, TOTAL_COLUMN), 0.001);

            assertEquals("保教费", text(sheet, 14, 2));
            assertEquals(1250.0, number(sheet, 14, MONTH_START_COLUMN), 0.001);
            assertEquals(500.0, number(sheet, 14, MONTH_START_COLUMN + 1), 0.001);
            assertEquals(1750.0, number(sheet, 14, TOTAL_COLUMN), 0.001);

            assertEquals("伙食费", text(sheet, 15, 2));
            assertEquals(270.0, number(sheet, 15, MONTH_START_COLUMN), 0.001);
            assertEquals(600.0, number(sheet, 15, MONTH_START_COLUMN + 1), 0.001);

            assertEquals("总计", text(sheet, 17, 0));
            assertEquals(1520.0, number(sheet, 17, MONTH_START_COLUMN), 0.001);
            assertEquals(1100.0, number(sheet, 17, MONTH_START_COLUMN + 1), 0.001);
            assertEquals(2620.0, number(sheet, 17, TOTAL_COLUMN), 0.001);
        }
    }

    @Test
    void 金额与单价按四位六位小数展示且整数不带小数点() throws IOException {
        try (XSSFWorkbook workbook = open(build())) {
            // 整数走整型格式：Excel 的 "0.####" 会渲染成 "600."，财务看到很别扭
            Sheet charge = workbook.getSheetAt(1);
            assertEquals("#,##0", numberFormat(charge, 3, 4), "整数月标准不应保留小数格式");
            assertEquals("#,##0", numberFormat(charge, 3, 7), "整数应收金额不应保留小数格式");
            assertEquals("#,##0", numberFormat(charge, 3, 10), "整数结余不应保留小数格式");

            Sheet attendance = workbook.getSheetAt(2);
            assertEquals("#,##0", numberFormat(attendance, 5, 3), "整数日标准不应保留小数格式");
            assertEquals("#,##0", numberFormat(attendance, 5, MONTH_START_COLUMN), "整数发生额不应保留小数格式");

            // 有小数时才启用 4/6 位格式，且写入值不丢精度（旧 DECIMAL(x,2) 会在入库时就截断）
            byte[] precise = FeeMatrixExportBuilder.build("国兴校区", "雅趣小班", "26 年下半年",
                List.of(YearMonth.of(2026, 8)),
                List.of(new ItemRef(ITEM_TUITION, "保教费")),
                List.of(new StudentBlock("S009", "尾差学员", List.of(),
                    null,
                    List.of(preciseItem(91L, ITEM_TUITION, "保教费", "monthly", "1.3333", "1000.123456", "0",
                        "0", "0", "1333.4578", "1333.4578", "0", "1333.4578", "0")),
                    List.of(), List.of(),
                    money("1333.4578"), money("1333.4578"), money("0"),
                    money("1333.4578"), money("0"))));
            try (XSSFWorkbook one = open(precise)) {
                Sheet preciseCharge = one.getSheetAt(1);
                assertEquals("#,##0.######", numberFormat(preciseCharge, 3, 4), "非整数月标准展示 6 位小数");
                assertEquals("#,##0.####", numberFormat(preciseCharge, 3, 7), "非整数应收展示 4 位小数");
                assertEquals("0.####", numberFormat(preciseCharge, 3, 3), "非整数月数展示 4 位小数");
                assertEquals(1000.123456, number(preciseCharge, 3, 4), 1e-9);
                assertEquals(1333.4578, number(preciseCharge, 3, 7), 1e-9);
            }
        }
    }

    // ------------------------------------------------------------------ 样例数据

    private byte[] build() {
        return FeeMatrixExportBuilder.build("国兴校区", "雅趣小班", "26 年下半年",
            List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10),
                YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)),
            List.of(new ItemRef(ITEM_TUITION, "保教费"),
                new ItemRef(ITEM_MEAL, "伙食费"),
                new ItemRef(ITEM_DELAY, "延时费")),
            List.of(studentA(), studentB()));
    }

    private StudentBlock studentA() {
        List<FeeLedgerItemView> items = List.of(
            item(11L, ITEM_TUITION, "保教费", "monthly", "5.5", "1500", "0",
                "0", "0", "8250", "9000", "-750", "750", "8250"),
            item(12L, ITEM_MEAL, "伙食费", "daily", "6", "600", "30",
                "0", "0", "3600", "3600", "0", "270", "3330"));
        List<FeeMonthlyView> monthly = new ArrayList<>();
        monthly.add(monthly(101L, 11L, ITEM_TUITION, "保教费", "monthly", 2026, 8, "750"));
        monthly.add(monthly(201L, 12L, ITEM_MEAL, "伙食费", "daily", 2026, 8, "270"));
        List<FeeAttendanceView> attendance = new ArrayList<>();
        attendance.add(attendance(301L, 2026, 8, "9"));
        return new StudentBlock("S001", "张三",
            List.of(new ParentContact("father", "张父", "13800000001", "13900000001", true),
                new ParentContact("mother", "张母", "13800000002", null, false)),
            "上学期有半托", items, monthly, attendance,
            money("11850"), money("12600"), money("-750"), money("1020"), money("11580"));
    }

    private StudentBlock studentB() {
        List<FeeLedgerItemView> items = List.of(
            item(21L, ITEM_TUITION, "保教费", "monthly", "6", "1000", "0",
                "0", "0", "6000", "6000", "0", "1000", "5000"),
            item(22L, ITEM_MEAL, "伙食费", "daily", "6", "600", "30",
                "0", "0", "3600", "3600", "0", "600", "3000"));
        List<FeeMonthlyView> monthly = new ArrayList<>();
        monthly.add(monthly(401L, 21L, ITEM_TUITION, "保教费", "monthly", 2026, 8, "500"));
        monthly.add(monthly(402L, 21L, ITEM_TUITION, "保教费", "monthly", 2026, 9, "500"));
        monthly.add(monthly(403L, 22L, ITEM_MEAL, "伙食费", "daily", 2026, 9, "600"));
        List<FeeAttendanceView> attendance = new ArrayList<>();
        attendance.add(attendance(501L, 2026, 8, "10"));
        attendance.add(attendance(502L, 2026, 9, "20"));
        return new StudentBlock("S002", "李四",
            List.of(new ParentContact("mother", "李母", "13800000003", "13800000003", true)),
            null, items, monthly, attendance,
            money("9600"), money("9600"), money("0"), money("1600"), money("8000"));
    }

    private FeeLedgerItemView item(Long id, Long feeItemId, String name, String billingMode,
                                   String months, String standard, String daily, String opening,
                                   String deduction, String receivable, String paid, String unpaid,
                                   String totalOccurrence, String balance) {
        return new FeeLedgerItemView(id, feeItemId, "F" + feeItemId, name, billingMode,
            money(months), money(standard), money(daily), money(opening), money(deduction),
            money(receivable), money(paid), money(unpaid),
            money(totalOccurrence), money(balance), "PRICE", null);
    }

    /** 与 money() 同构，但按新口径保留 4 位小数，用于验证尾差不被截断。 */
    private FeeLedgerItemView preciseItem(Long id, Long feeItemId, String name, String billingMode,
                                          String months, String standard, String daily, String opening,
                                          String deduction, String receivable, String paid, String unpaid,
                                          String totalOccurrence, String balance) {
        return new FeeLedgerItemView(id, feeItemId, "F" + feeItemId, name, billingMode,
            precise(months), precise(standard), precise(daily), precise(opening), precise(deduction),
            precise(receivable), precise(paid), precise(unpaid),
            precise(totalOccurrence), precise(balance), "PRICE", null);
    }

    private static BigDecimal precise(String value) {
        return new BigDecimal(value).setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private FeeMonthlyView monthly(Long id, Long ledgerItemId, Long feeItemId, String name,
                                   String billingMode, int year, int month, String amount) {
        return new FeeMonthlyView(id, ledgerItemId, feeItemId, name, billingMode,
            LocalDate.of(year, month, 1), new BigDecimal("1.00"),
            money(amount), money(amount), false, null);
    }

    private FeeAttendanceView attendance(Long id, int year, int month, String days) {
        return new FeeAttendanceView(id, LocalDate.of(year, month, 1), money(days), money(days),
            false, "AUTO", null);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ 读回工具

    private XSSFWorkbook open(byte[] workbook) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(workbook));
    }

    private boolean hasMerge(Sheet sheet, int firstRow, int lastRow, int firstColumn, int lastColumn) {
        return sheet.getMergedRegions().stream().anyMatch(region ->
            region.getFirstRow() == firstRow && region.getLastRow() == lastRow
                && region.getFirstColumn() == firstColumn && region.getLastColumn() == lastColumn);
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

    private String numberFormat(Sheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        assertNotNull(row, "行 " + rowIndex + " 不存在");
        Cell cell = row.getCell(column);
        assertNotNull(cell, "单元格 " + rowIndex + "," + column + " 不存在");
        return cell.getCellStyle().getDataFormatString();
    }
}
