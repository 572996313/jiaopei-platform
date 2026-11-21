package com.cornerstone.module.education.fee.support;

import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import com.cornerstone.module.education.fee.dto.FeeLedgerItemView;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 收费出勤表导出：不再把家长、收费、出勤压进同一个学员块，而是拆成三个 sheet。
 *
 * <p>为什么拆：原版每个学员一个「项目数 + 2」行的大块，家长、收费、逐月发生、小计全挤在
 * 同一行里，任何一列口径变化都要重算纵向合并；财务核对时也很难只看其中一部分。现在按
 * 财务手工表的三个部分拆开，每个 sheet 都是规整表格，便于筛选、核对和线下再合并：</p>
 *
 * <pre>
 *   家长情况  学号 / 姓名 / 家长关系 / 家长姓名 / 联系电话 / 支付宝绑定电话 / 主联系人
 *   收费内容  学号 / 姓名 / 收费内容 / 收费月数 / 收费标准 / 上期结余 / 其他抵扣 /
 *             应收金额 / 实际收款 / 未收费 / 结余 / 备注（每生项目行 + 小计行）
 *   出勤明细  学号 / 姓名 / 项目 / 日标准 / 各月 / 合计发生
 *             每个学员先一行「出勤天数」，下面按项目列逐月发生额，最后一行小计
 * </pre>
 *
 * <p>每个 sheet 顶部都保留标题行和「班级 + 单位：元」信息行；金额写计算后的数值，不写公式，
 * 避免财务在 Excel 里跨 sheet 汇总时出现 #REF!。</p>
 */
public final class FeeMatrixExportBuilder {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("M月");
    private static final int TITLE_ROW = 0;
    private static final int META_ROW = 1;
    private static final int HEADER_ROW = 2;

    private FeeMatrixExportBuilder() {
    }

    /** 项目列定义：配置里启用但该学员没建行的项目也要占位（源表的延时费）。 */
    public record ItemRef(Long feeItemId, String itemName) {
    }

    /** 家长联系人：关系值存字典原始值，展示名由本类翻译。 */
    public record ParentContact(String relationType, String parentName, String parentPhone,
                                String alipayPhone, boolean primary) {
    }

    /** 一个学员的导出数据，金额与发生额全部由服务端算好。 */
    public record StudentBlock(
        String studentNo,
        String studentName,
        List<ParentContact> parents,
        String remark,
        List<FeeLedgerItemView> items,
        List<FeeMonthlyView> monthly,
        List<FeeAttendanceView> attendance,
        BigDecimal receivable,
        BigDecimal paid,
        BigDecimal unpaid,
        BigDecimal totalOccurrence,
        BigDecimal balance
    ) {
    }

    public static byte[] build(String institutionName, String campusName, String className,
                               String termName, List<YearMonth> months, List<ItemRef> itemRefs,
                               List<StudentBlock> students) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeParentSheet(workbook, styles, institutionName, campusName, className, termName, students);
            writeChargeSheet(workbook, styles, institutionName, campusName, className, termName,
                itemRefs, students);
            writeAttendanceSheet(workbook, styles, institutionName, campusName, className, termName,
                months, itemRefs, students);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成收费出勤表失败", exception);
        }
    }

    // ------------------------------------------------------------------ 第一部分：家长情况

    private static void writeParentSheet(XSSFWorkbook workbook, Styles styles, String institutionName,
                                         String campusName, String className, String termName,
                                         List<StudentBlock> students) {
        Sheet sheet = workbook.createSheet("家长情况");
        String[] header = {"学号", "学员姓名", "家长关系", "家长姓名", "联系电话", "支付宝绑定电话", "主联系人"};
        int lastColumn = header.length - 1;
        writeTitle(sheet, styles, lastColumn, joinTitle(institutionName, campusName, className, termName, "家长情况表"));
        writeMeta(sheet, styles, lastColumn, className);
        writeHeader(sheet, styles, header);

        int rowCursor = HEADER_ROW + 1;
        for (StudentBlock student : students) {
            List<ParentContact> parents = student.parents() == null ? List.of() : student.parents();
            int contactCount = Math.max(parents.size(), 1);
            int blockStart = rowCursor;
            for (int index = 0; index < contactCount; index++) {
                ParentContact parent = index < parents.size() ? parents.get(index) : null;
                Row row = sheet.createRow(rowCursor++);
                if (parent != null) {
                    setText(row, 2, relationLabel(parent.relationType()), styles.text);
                    setText(row, 3, parent.parentName(), styles.text);
                    setText(row, 4, parent.parentPhone(), styles.text);
                    setText(row, 5, parent.alipayPhone(), styles.text);
                    setText(row, 6, parent.primary() ? "是" : "", styles.text);
                } else {
                    // 没维护联系人也要把格子画出来，否则这一行没有边框，打印出来像断了一截
                    for (int column = 2; column <= 6; column++) {
                        setText(row, column, "", styles.text);
                    }
                }
            }
            setText(sheet.getRow(blockStart), 0, student.studentNo(), styles.text);
            setText(sheet.getRow(blockStart), 1, student.studentName(), styles.text);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 0);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 1);
        }
        setColumnWidths(sheet, new int[]{14, 12, 10, 14, 15, 16, 10});
        sheet.createFreezePane(2, HEADER_ROW + 1);
    }

    // ------------------------------------------------------------------ 第二部分：收费内容

    private static void writeChargeSheet(XSSFWorkbook workbook, Styles styles, String institutionName,
                                         String campusName, String className, String termName,
                                         List<ItemRef> itemRefs, List<StudentBlock> students) {
        Sheet sheet = workbook.createSheet("收费内容");
        String[] header = {"学号", "学员姓名", "收费内容", "收费月数", "收费标准", "上期结余",
            "其他抵扣", "应收金额", "实际收款", "未收费", "结余", "备注"};
        int lastColumn = header.length - 1;
        writeTitle(sheet, styles, lastColumn, joinTitle(institutionName, campusName, className, termName, "收费内容表"));
        writeMeta(sheet, styles, lastColumn, className);
        writeHeader(sheet, styles, header);

        int rowCursor = HEADER_ROW + 1;
        for (StudentBlock student : students) {
            Map<Long, FeeLedgerItemView> itemById = new HashMap<>();
            student.items().forEach(item -> itemById.put(item.feeItemId(), item));
            int itemCount = Math.max(itemRefs.size(), 1);
            int blockStart = rowCursor;
            for (int index = 0; index < itemCount; index++) {
                ItemRef ref = index < itemRefs.size() ? itemRefs.get(index) : null;
                FeeLedgerItemView item = ref == null ? null : itemById.get(ref.feeItemId());
                Row row = sheet.createRow(rowCursor++);
                writeChargeCells(styles, row, item, ref);
            }
            Row subtotal = sheet.createRow(rowCursor++);
            setText(subtotal, 2, "小计", styles.bold);
            setText(subtotal, 3, "", styles.text);
            setText(subtotal, 4, "", styles.text);
            setMoney(subtotal, 5, sumItems(student.items(), FeeLedgerItemView::openingBalance), styles.money);
            setMoney(subtotal, 6, sumItems(student.items(), FeeLedgerItemView::otherDeduction), styles.money);
            setMoney(subtotal, 7, student.receivable(), styles.money);
            setMoney(subtotal, 8, student.paid(), styles.money);
            setMoney(subtotal, 9, student.unpaid(), styles.money);
            setMoney(subtotal, 10, student.balance(), styles.money);

            setText(sheet.getRow(blockStart), 0, student.studentNo(), styles.text);
            setText(sheet.getRow(blockStart), 1, student.studentName(), styles.text);
            setText(sheet.getRow(blockStart), 11, student.remark(), styles.text);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 0);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 1);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 11);
        }
        writeChargeFooter(sheet, styles, rowCursor, students);
        setColumnWidths(sheet, new int[]{14, 12, 16, 12, 14, 14, 14, 14, 14, 14, 14, 26});
        sheet.createFreezePane(2, HEADER_ROW + 1);
    }

    private static void writeChargeCells(Styles styles, Row row, FeeLedgerItemView item, ItemRef ref) {
        String itemName = item != null ? item.itemName() : (ref == null ? "" : ref.itemName());
        setText(row, 2, itemName, styles.text);
        if (item == null) {
            for (int column = 3; column <= 10; column++) {
                setText(row, column, "", styles.text);
            }
            return;
        }
        setQty(row, 3, item.billableMonths(), styles.qty);
        // 收费内容里的「收费标准」参与应收计算，按月标准展示；按天项目的日标准在出勤明细里单独一列
        setMoney(row, 4, item.monthlyStandard(), styles.price);
        setMoney(row, 5, item.openingBalance(), styles.money);
        setMoney(row, 6, item.otherDeduction(), styles.money);
        setMoney(row, 7, item.receivable(), styles.money);
        setMoney(row, 8, item.paid(), styles.money);
        setMoney(row, 9, item.unpaid(), styles.money);
        setMoney(row, 10, item.balance(), styles.money);
    }

    private static void writeChargeFooter(Sheet sheet, Styles styles, int rowIndex,
                                          List<StudentBlock> students) {
        Row total = sheet.createRow(rowIndex);
        // 先把整行格子画出来，再填值，避免表尾缺格导致边框断开
        for (int column = 0; column <= 11; column++) {
            setText(total, column, "", styles.bold);
        }
        setText(total, 0, "总计", styles.bold);
        merge(sheet, rowIndex, rowIndex, 0, 1);
        setMoney(total, 5, sumStudents(students, FeeLedgerItemView::openingBalance), styles.money);
        setMoney(total, 6, sumStudents(students, FeeLedgerItemView::otherDeduction), styles.money);
        setMoney(total, 7, sum(students, StudentBlock::receivable), styles.money);
        setMoney(total, 8, sum(students, StudentBlock::paid), styles.money);
        setMoney(total, 9, sum(students, StudentBlock::unpaid), styles.money);
        setMoney(total, 10, sum(students, StudentBlock::balance), styles.money);
    }

    // ------------------------------------------------------------------ 第三部分：出勤明细

    private static void writeAttendanceSheet(XSSFWorkbook workbook, Styles styles, String institutionName,
                                             String campusName, String className, String termName,
                                             List<YearMonth> months, List<ItemRef> itemRefs,
                                             List<StudentBlock> students) {
        Sheet sheet = workbook.createSheet("出勤明细");
        int monthStartColumn = 4;
        int totalColumn = monthStartColumn + months.size();
        String[] header = new String[monthStartColumn + months.size() + 1];
        header[0] = "学号";
        header[1] = "学员姓名";
        header[2] = "项目";
        header[3] = "日标准";
        for (int index = 0; index < months.size(); index++) {
            header[monthStartColumn + index] = months.get(index).format(MONTH_LABEL);
        }
        header[totalColumn] = "合计发生";
        writeTitle(sheet, styles, totalColumn, joinTitle(institutionName, campusName, className, termName, "出勤费用表"));
        writeMeta(sheet, styles, totalColumn, className);
        writeHeader(sheet, styles, header);

        int rowCursor = HEADER_ROW + 1;
        for (StudentBlock student : students) {
            int blockStart = rowCursor;
            Map<YearMonth, BigDecimal> daysByMonth = new HashMap<>();
            for (FeeAttendanceView attendance : student.attendance()) {
                daysByMonth.merge(YearMonth.from(attendance.billingMonth()),
                    nz(attendance.attendanceDays()), BigDecimal::add);
            }
            Map<String, BigDecimal> occurrence = new HashMap<>();
            for (FeeMonthlyView row : student.monthly()) {
                occurrence.merge(itemMonthKey(row.feeItemId(), YearMonth.from(row.billingMonth())),
                    nz(row.occurrenceAmount()), BigDecimal::add);
            }

            // 第一行固定「出勤天数」，下面的费用项都按这一行对齐
            Row attendance = sheet.createRow(rowCursor++);
            setText(attendance, 2, "出勤天数", styles.text);
            setText(attendance, 3, "", styles.text);
            BigDecimal attendanceTotal = BigDecimal.ZERO;
            for (int index = 0; index < months.size(); index++) {
                BigDecimal days = daysByMonth.getOrDefault(months.get(index), BigDecimal.ZERO);
                setQty(attendance, monthStartColumn + index, days, styles.qty);
                attendanceTotal = attendanceTotal.add(days);
            }
            setQty(attendance, totalColumn, attendanceTotal, styles.qty);

            Map<Long, FeeLedgerItemView> itemById = new HashMap<>();
            student.items().forEach(item -> itemById.put(item.feeItemId(), item));
            int itemCount = Math.max(itemRefs.size(), 1);
            for (int index = 0; index < itemCount; index++) {
                ItemRef ref = index < itemRefs.size() ? itemRefs.get(index) : null;
                FeeLedgerItemView item = ref == null ? null : itemById.get(ref.feeItemId());
                Row row = sheet.createRow(rowCursor++);
                writeAttendanceItem(styles, row, months, monthStartColumn, totalColumn, ref, item, occurrence);
            }

            Row subtotal = sheet.createRow(rowCursor++);
            setText(subtotal, 2, "小计", styles.bold);
            setText(subtotal, 3, "", styles.text);
            for (int index = 0; index < months.size(); index++) {
                setMoney(subtotal, monthStartColumn + index, totalOfMonth(student, months.get(index)), styles.money);
            }
            setMoney(subtotal, totalColumn, student.totalOccurrence(), styles.money);

            setText(sheet.getRow(blockStart), 0, student.studentNo(), styles.text);
            setText(sheet.getRow(blockStart), 1, student.studentName(), styles.text);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 0);
            mergeIfMultiple(sheet, blockStart, rowCursor - 1, 1);
        }
        writeAttendanceFooter(sheet, styles, rowCursor, months, monthStartColumn, totalColumn, itemRefs, students);
        int[] widths = new int[totalColumn + 1];
        widths[0] = 14;
        widths[1] = 12;
        widths[2] = 16;
        widths[3] = 12;
        for (int index = monthStartColumn; index < totalColumn; index++) {
            widths[index] = 12;
        }
        widths[totalColumn] = 16;
        setColumnWidths(sheet, widths);
        sheet.createFreezePane(2, HEADER_ROW + 1);
    }

    private static void writeAttendanceItem(Styles styles, Row row, List<YearMonth> months,
                                            int monthStartColumn, int totalColumn,
                                            ItemRef ref, FeeLedgerItemView item,
                                            Map<String, BigDecimal> occurrence) {
        String itemName = item != null ? item.itemName() : (ref == null ? "" : ref.itemName());
        setText(row, 2, itemName, styles.text);
        if (item == null) {
            for (int index = 3; index <= totalColumn; index++) {
                setText(row, index, "", styles.text);
            }
            return;
        }
        setMoney(row, 3, "daily".equals(item.billingMode()) ? item.dailyRate() : null, styles.price);
        BigDecimal itemTotal = BigDecimal.ZERO;
        for (int index = 0; index < months.size(); index++) {
            BigDecimal amount = occurrence.getOrDefault(
                itemMonthKey(item.feeItemId(), months.get(index)), BigDecimal.ZERO);
            setMoney(row, monthStartColumn + index, amount, styles.money);
            itemTotal = itemTotal.add(amount);
        }
        setMoney(row, totalColumn, itemTotal, styles.money);
    }

    private static void writeAttendanceFooter(Sheet sheet, Styles styles, int startRow,
                                              List<YearMonth> months, int monthStartColumn,
                                              int totalColumn, List<ItemRef> itemRefs,
                                              List<StudentBlock> students) {
        int rowCursor = startRow;
        Row daysTotal = sheet.createRow(rowCursor++);
        fillRow(daysTotal, totalColumn, styles.bold);
        setText(daysTotal, 2, "出勤天数", styles.bold);
        for (int index = 0; index < months.size(); index++) {
            YearMonth month = months.get(index);
            BigDecimal total = BigDecimal.ZERO;
            for (StudentBlock student : students) {
                for (FeeAttendanceView attendance : student.attendance()) {
                    if (YearMonth.from(attendance.billingMonth()).equals(month)) {
                        total = total.add(nz(attendance.attendanceDays()));
                    }
                }
            }
            setQty(daysTotal, monthStartColumn + index, total, styles.qty);
        }
        setQty(daysTotal, totalColumn, sumAttendanceDays(students), styles.qty);

        for (ItemRef ref : itemRefs) {
            Row row = sheet.createRow(rowCursor++);
            fillRow(row, totalColumn, styles.bold);
            setText(row, 2, ref.itemName(), styles.bold);
            BigDecimal itemTotal = BigDecimal.ZERO;
            for (int index = 0; index < months.size(); index++) {
                YearMonth month = months.get(index);
                BigDecimal total = BigDecimal.ZERO;
                for (StudentBlock student : students) {
                    for (FeeMonthlyView monthly : student.monthly()) {
                        if (ref.feeItemId().equals(monthly.feeItemId())
                            && YearMonth.from(monthly.billingMonth()).equals(month)) {
                            total = total.add(nz(monthly.occurrenceAmount()));
                        }
                    }
                }
                setMoney(row, monthStartColumn + index, total, styles.money);
                itemTotal = itemTotal.add(total);
            }
            setMoney(row, totalColumn, itemTotal, styles.money);
        }
        if (rowCursor - 1 > startRow) {
            setText(sheet.getRow(startRow), 0, "合计", styles.bold);
            merge(sheet, startRow, rowCursor - 1, 0, 0);
        } else {
            setText(sheet.getRow(startRow), 0, "合计", styles.bold);
        }

        Row total = sheet.createRow(rowCursor);
        fillRow(total, totalColumn, styles.bold);
        setText(total, 0, "总计", styles.bold);
        merge(sheet, rowCursor, rowCursor, 0, 1);
        for (int index = 0; index < months.size(); index++) {
            YearMonth month = months.get(index);
            BigDecimal monthTotal = BigDecimal.ZERO;
            for (StudentBlock student : students) {
                monthTotal = monthTotal.add(totalOfMonth(student, month));
            }
            setMoney(total, monthStartColumn + index, monthTotal, styles.money);
        }
        setMoney(total, totalColumn, sum(students, StudentBlock::totalOccurrence), styles.money);
    }

    // ------------------------------------------------------------------ 通用版式

    private static void writeTitle(Sheet sheet, Styles styles, int lastColumn, String titleText) {
        Row titleRow = sheet.createRow(TITLE_ROW);
        titleRow.setHeightInPoints(28);
        Cell title = titleRow.createCell(0);
        title.setCellValue(titleText);
        title.setCellStyle(styles.title);
        merge(sheet, TITLE_ROW, TITLE_ROW, 0, lastColumn);
    }

    /** 源表第二行：左边班级，右边单位。财务打印后要靠这一行认出是哪张表。 */
    private static void writeMeta(Sheet sheet, Styles styles, int lastColumn, String className) {
        Row metaRow = sheet.createRow(META_ROW);
        setText(metaRow, 0, className == null ? "" : className, styles.meta);
        if (lastColumn > 0) {
            merge(sheet, META_ROW, META_ROW, 0, Math.min(2, lastColumn));
        }
        Cell unit = metaRow.createCell(lastColumn);
        unit.setCellValue("单位：元");
        unit.setCellStyle(styles.textRight);
    }

    private static void writeHeader(Sheet sheet, Styles styles, String[] header) {
        Row row = sheet.createRow(HEADER_ROW);
        for (int index = 0; index < header.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(header[index] == null ? "" : header[index]);
            cell.setCellStyle(styles.header);
        }
    }

    private static String joinTitle(String institutionName, String campusName, String className,
                                    String termName, String suffix) {
        StringBuilder title = new StringBuilder();
        for (String part : new String[]{institutionName, campusName, className, termName}) {
            if (part != null && !part.isBlank()) {
                title.append(part).append(' ');
            }
        }
        return title.append(suffix).toString();
    }

    private static String relationLabel(String relationType) {
        if (relationType == null || relationType.isBlank()) {
            return "联系人";
        }
        return switch (relationType) {
            case "father" -> "父亲";
            case "mother" -> "母亲";
            case "guardian" -> "监护人";
            case "relative" -> "其他亲属";
            default -> relationType;
        };
    }

    private static String itemMonthKey(Long feeItemId, YearMonth month) {
        return feeItemId + "|" + month;
    }

    private static BigDecimal totalOfMonth(StudentBlock student, YearMonth month) {
        BigDecimal total = BigDecimal.ZERO;
        for (FeeMonthlyView row : student.monthly()) {
            if (YearMonth.from(row.billingMonth()).equals(month)) {
                total = total.add(nz(row.occurrenceAmount()));
            }
        }
        return total;
    }

    private static BigDecimal sumAttendanceDays(List<StudentBlock> students) {
        BigDecimal total = BigDecimal.ZERO;
        for (StudentBlock student : students) {
            for (FeeAttendanceView attendance : student.attendance()) {
                total = total.add(nz(attendance.attendanceDays()));
            }
        }
        return total;
    }

    private static BigDecimal sumItems(List<FeeLedgerItemView> items,
                                       java.util.function.Function<FeeLedgerItemView, BigDecimal> getter) {
        return items.stream().map(getter).map(FeeMatrixExportBuilder::nz)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumStudents(List<StudentBlock> students,
                                          java.util.function.Function<FeeLedgerItemView, BigDecimal> getter) {
        BigDecimal total = BigDecimal.ZERO;
        for (StudentBlock student : students) {
            total = total.add(sumItems(student.items(), getter));
        }
        return total;
    }

    private static BigDecimal sum(List<StudentBlock> students,
                                  java.util.function.Function<StudentBlock, BigDecimal> getter) {
        return students.stream().map(getter).map(FeeMatrixExportBuilder::nz)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void setColumnWidths(Sheet sheet, int[] widths) {
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private static void mergeIfMultiple(Sheet sheet, int firstRow, int lastRow, int column) {
        if (lastRow > firstRow) {
            merge(sheet, firstRow, lastRow, column, column);
        }
    }

    private static void merge(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
    }

    private static void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void fillRow(Row row, int lastColumn, CellStyle style) {
        for (int column = 0; column <= lastColumn; column++) {
            setText(row, column, "", style);
        }
    }

    private static void setMoney(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static void setQty(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 样式在循环外创建一次并复用：逐格 new CellStyle 会撞 xlsx 的 64000 个样式上限。 */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle meta;
        private final CellStyle text;
        private final CellStyle textRight;
        private final CellStyle bold;
        private final CellStyle money;
        private final CellStyle price;
        private final CellStyle qty;

        private Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header = bordered(workbook);
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font metaFont = workbook.createFont();
            metaFont.setBold(true);
            meta = bordered(workbook);
            meta.setFont(metaFont);
            meta.setAlignment(HorizontalAlignment.CENTER);
            meta.setVerticalAlignment(VerticalAlignment.CENTER);

            text = bordered(workbook);
            text.setVerticalAlignment(VerticalAlignment.CENTER);
            text.setWrapText(true);

            textRight = bordered(workbook);
            textRight.setAlignment(HorizontalAlignment.RIGHT);
            textRight.setVerticalAlignment(VerticalAlignment.CENTER);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold = bordered(workbook);
            bold.setFont(boldFont);
            bold.setVerticalAlignment(VerticalAlignment.CENTER);

            money = bordered(workbook);
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.####"));
            money.setVerticalAlignment(VerticalAlignment.CENTER);

            price = bordered(workbook);
            price.setDataFormat(workbook.createDataFormat().getFormat("#,##0.######"));
            price.setVerticalAlignment(VerticalAlignment.CENTER);

            qty = bordered(workbook);
            qty.setDataFormat(workbook.createDataFormat().getFormat("0.####"));
            qty.setVerticalAlignment(VerticalAlignment.CENTER);
        }

        private static CellStyle bordered(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }
    }
}
