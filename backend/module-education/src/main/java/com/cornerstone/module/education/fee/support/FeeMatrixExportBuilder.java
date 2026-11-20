package com.cornerstone.module.education.fee.support;

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
 * 收费出勤表矩阵导出：与财务手工维护的 xlsx 同构（POI 手工画，不用 EasyExcel）。
 *
 * <p>为什么不能一维导出凑合：这张表是给园长和财务"一眼看一个班"的，靠合并单元格把
 * 学员、家长、收费信息压成一块，块内再按项目分行。EasyExcel 只能写规整的一维表，
 * 这里必须自己算每一行的偏移与合并区域。</p>
 *
 * <p>版式（与源表对齐）：</p>
 * <pre>
 *   第 1 行  标题：机构 + 校区 + 班级 + 学期 + 收费出勤表
 *   第 2 行  单位：元
 *   第 3 行  表头：学号 姓名 家长 电话 支付宝 | 收费项目 收费月数 收费标准 上期结余 其他抵扣
 *            应收金额 实际收款 未收费 出勤天数 | 8月 9月 … 1月 | 合计发生 结余 备注
 *   学员块   块高 = 项目数 + 2：项目 1 占两行（收费行 + 发生行），其余项目各一行（收费与发生同行），
 *            最后一行小计；学号/姓名/家长/电话/支付宝/出勤天数/备注在块内纵向合并
 *   表尾     与学员块同构：项目 1 两行、其余项目各一行，最后一行总计
 * </pre>
 *
 * <p>月份列不写死 6 个月，由学期起止生成；写的是计算后的数值，不写公式——财务要在 Excel 里
 * 自己筛选取和，公式跨 workbook 会变成一堆 #REF!。</p>
 */
public final class FeeMatrixExportBuilder {

    private static final String SHEET_NAME = "收费出勤表";
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("M月");

    // 固定列
    private static final int COL_STUDENT_NO = 0;
    private static final int COL_STUDENT_NAME = 1;
    private static final int COL_PARENT_NAME = 2;
    private static final int COL_PARENT_PHONE = 3;
    private static final int COL_ALIPAY_PHONE = 4;
    private static final int COL_ITEM = 5;
    private static final int COL_MONTHS = 6;
    private static final int COL_STANDARD = 7;
    private static final int COL_OPENING = 8;
    private static final int COL_DEDUCTION = 9;
    private static final int COL_RECEIVABLE = 10;
    private static final int COL_PAID = 11;
    private static final int COL_UNPAID = 12;
    private static final int COL_ATTENDANCE = 13;
    private static final int FIRST_MONTH_COL = 14;

    private static final int HEADER_ROW = 2;

    private FeeMatrixExportBuilder() {
    }

    /** 项目列定义：导出时必须把"启用了但该学员没建行"的项目也占一行（源表的延时费）。 */
    public record ItemRef(Long feeItemId, String itemName) {
    }

    /** 一个学员的导出数据，金额与发生额全部由服务端算好。 */
    public record StudentBlock(
        String studentNo,
        String studentName,
        String parentName,
        String parentPhone,
        String alipayPhone,
        String remark,
        BigDecimal attendanceDays,
        List<FeeLedgerItemView> items,
        List<FeeMonthlyView> monthly,
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
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Styles styles = new Styles(workbook);
            int lastColumn = FIRST_MONTH_COL + months.size() + 2;
            writeTitle(sheet, styles, lastColumn, institutionName, campusName, className, termName);
            writeHeader(sheet, styles, months);

            int rowCursor = HEADER_ROW + 1;
            for (StudentBlock student : students) {
                rowCursor = writeStudentBlock(sheet, styles, months, itemRefs, student, rowCursor);
            }
            rowCursor = writeFooter(sheet, styles, months, itemRefs, students, rowCursor);

            sheet.createFreezePane(0, 3);
            setColumnWidths(sheet, months.size());
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成收费出勤表失败", exception);
        }
    }

    private static void writeTitle(Sheet sheet, Styles styles, int lastColumn,
                                   String institutionName, String campusName,
                                   String className, String termName) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28);
        Cell title = titleRow.createCell(0);
        title.setCellValue(joinTitle(institutionName, campusName, className, termName));
        title.setCellStyle(styles.title);
        merge(sheet, 0, 0, 0, lastColumn);

        Row unitRow = sheet.createRow(1);
        Cell unit = unitRow.createCell(0);
        unit.setCellValue("单位：元");
        unit.setCellStyle(styles.normal);
        merge(sheet, 1, 1, 0, lastColumn);
    }

    private static String joinTitle(String institutionName, String campusName,
                                    String className, String termName) {
        StringBuilder title = new StringBuilder();
        for (String part : new String[]{institutionName, campusName, className, termName}) {
            if (part != null && !part.isBlank()) {
                title.append(part).append(' ');
            }
        }
        return title.append("收费出勤表").toString();
    }

    private static void writeHeader(Sheet sheet, Styles styles, List<YearMonth> months) {
        Row header = sheet.createRow(HEADER_ROW);
        String[] fixed = {"学号", "学员姓名", "家长姓名", "家长电话", "支付宝绑定电话",
            "收费项目", "收费月数", "收费标准", "上期结余", "其他抵扣",
            "应收金额", "实际收款", "未收费", "出勤天数"};
        for (int index = 0; index < fixed.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(fixed[index]);
            cell.setCellStyle(styles.header);
        }
        for (int index = 0; index < months.size(); index++) {
            Cell cell = header.createCell(FIRST_MONTH_COL + index);
            cell.setCellValue(months.get(index).format(MONTH_LABEL));
            cell.setCellStyle(styles.header);
        }
        int tail = FIRST_MONTH_COL + months.size();
        for (String label : new String[]{"合计发生", "结余", "备注"}) {
            Cell cell = header.createCell(tail++);
            cell.setCellValue(label);
            cell.setCellStyle(styles.header);
        }
    }

    private static int writeStudentBlock(Sheet sheet, Styles styles, List<YearMonth> months,
                                         List<ItemRef> itemRefs, StudentBlock student, int blockStart) {
        int itemCount = Math.max(itemRefs.size(), 1);
        int blockHeight = itemCount + 2;
        Map<Long, FeeLedgerItemView> itemById = new HashMap<>();
        student.items().forEach(item -> itemById.put(item.feeItemId(), item));
        // 月份发生额按项目拆开：key = 项目ID + 月
        Map<String, BigDecimal> itemMonth = new HashMap<>();
        for (FeeMonthlyView row : student.monthly()) {
            YearMonth month = YearMonth.from(row.billingMonth());
            itemMonth.merge(row.feeItemId() + "|" + month, nz(row.occurrenceAmount()), BigDecimal::add);
        }

        for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
            ItemRef ref = itemIndex < itemRefs.size() ? itemRefs.get(itemIndex) : null;
            FeeLedgerItemView item = ref == null ? null : itemById.get(ref.feeItemId());
            if (itemIndex == 0) {
                // 项目 1：收费行 + 发生行
                Row chargeRow = sheet.createRow(blockStart);
                writeChargeCells(styles, chargeRow, item, ref);
                writeAttendance(styles, chargeRow, student.attendanceDays());
                Row occurrenceRow = sheet.createRow(blockStart + 1);
                writeMonthCells(styles, occurrenceRow, months, item, itemMonth);
            } else {
                Row row = sheet.createRow(blockStart + 1 + itemIndex);
                writeChargeCells(styles, row, item, ref);
                writeMonthCells(styles, row, months, item, itemMonth);
            }
        }
        int subtotalRowIndex = blockStart + itemCount + 1;
        Row subtotal = sheet.createRow(subtotalRowIndex);
        setText(subtotal, COL_ITEM, "小计", styles.bold);
        setMoney(subtotal, COL_RECEIVABLE, student.receivable(), styles.money);
        setMoney(subtotal, COL_PAID, student.paid(), styles.money);
        setMoney(subtotal, COL_UNPAID, student.unpaid(), styles.money);
        for (int index = 0; index < months.size(); index++) {
            setMoney(subtotal, FIRST_MONTH_COL + index, totalOfMonth(student, months.get(index)), styles.money);
        }
        int tail = FIRST_MONTH_COL + months.size();
        setMoney(subtotal, tail, student.totalOccurrence(), styles.money);
        setMoney(subtotal, tail + 1, student.balance(), styles.money);

        // 纵向合并：学员/家长/出勤/备注在整块内合并，避免每行重复
        int blockEnd = blockStart + blockHeight - 1;
        setText(sheet.getRow(blockStart), COL_STUDENT_NO, student.studentNo(), styles.text);
        setText(sheet.getRow(blockStart), COL_STUDENT_NAME, student.studentName(), styles.text);
        setText(sheet.getRow(blockStart), COL_PARENT_NAME, student.parentName(), styles.text);
        setText(sheet.getRow(blockStart), COL_PARENT_PHONE, student.parentPhone(), styles.text);
        setText(sheet.getRow(blockStart), COL_ALIPAY_PHONE, student.alipayPhone(), styles.text);
        setText(sheet.getRow(blockStart), tail + 2, student.remark(), styles.text);
        mergeIfMultiple(sheet, blockStart, blockEnd, COL_STUDENT_NO);
        mergeIfMultiple(sheet, blockStart, blockEnd, COL_STUDENT_NAME);
        mergeIfMultiple(sheet, blockStart, blockEnd, COL_PARENT_NAME);
        mergeIfMultiple(sheet, blockStart, blockEnd, COL_PARENT_PHONE);
        mergeIfMultiple(sheet, blockStart, blockEnd, COL_ALIPAY_PHONE);
        mergeIfMultiple(sheet, blockStart, blockEnd, COL_ATTENDANCE);
        mergeIfMultiple(sheet, blockStart, blockEnd, tail + 2);
        return blockEnd + 1;
    }

    private static void writeChargeCells(Styles styles, Row row,
                                         FeeLedgerItemView item, ItemRef ref) {
        String itemName = item != null ? item.itemName() : (ref == null ? "" : ref.itemName());
        setText(row, COL_ITEM, itemName, styles.text);
        if (item == null) {
            for (int column = COL_MONTHS; column <= COL_UNPAID; column++) {
                setText(row, column, "", styles.text);
            }
            return;
        }
        setQty(row, COL_MONTHS, item.billableMonths(), styles.qty);
        setMoney(row, COL_STANDARD, "daily".equals(item.billingMode())
            ? item.dailyRate() : item.monthlyStandard(), styles.money);
        setMoney(row, COL_OPENING, item.openingBalance(), styles.money);
        setMoney(row, COL_DEDUCTION, item.otherDeduction(), styles.money);
        setMoney(row, COL_RECEIVABLE, item.receivable(), styles.money);
        setMoney(row, COL_PAID, item.paid(), styles.money);
        setMoney(row, COL_UNPAID, item.unpaid(), styles.money);
    }

    private static void writeMonthCells(Styles styles, Row row, List<YearMonth> months,
                                        FeeLedgerItemView item, Map<String, BigDecimal> itemMonth) {
        for (int index = 0; index < months.size(); index++) {
            BigDecimal amount = item == null ? null
                : itemMonth.get(item.feeItemId() + "|" + months.get(index));
            setMoney(row, FIRST_MONTH_COL + index, amount, styles.money);
        }
    }

    private static void writeAttendance(Styles styles, Row row, BigDecimal days) {
        setQty(row, COL_ATTENDANCE, days, styles.qty);
    }

    private static int writeFooter(Sheet sheet, Styles styles, List<YearMonth> months,
                                   List<ItemRef> itemRefs, List<StudentBlock> students, int startRow) {
        int itemCount = Math.max(itemRefs.size(), 1);
        int rowCursor = startRow;
        for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
            ItemRef ref = itemIndex < itemRefs.size() ? itemRefs.get(itemIndex) : null;
            String name = ref == null ? "" : ref.itemName();
            if (itemIndex == 0) {
                Row labelRow = sheet.createRow(rowCursor++);
                setText(labelRow, COL_STUDENT_NO, "合计", styles.bold);
                setText(labelRow, COL_ITEM, name, styles.bold);
                Row monthRow = sheet.createRow(rowCursor++);
                writeFooterMonthCells(monthRow, styles, months, ref, students);
            } else {
                Row row = sheet.createRow(rowCursor++);
                setText(row, COL_ITEM, name, styles.bold);
                writeFooterMonthCells(row, styles, months, ref, students);
            }
        }
        Row total = sheet.createRow(rowCursor);
        setText(total, COL_STUDENT_NO, "总计", styles.bold);
        setMoney(total, COL_RECEIVABLE, sum(students, StudentBlock::receivable), styles.money);
        setMoney(total, COL_PAID, sum(students, StudentBlock::paid), styles.money);
        setMoney(total, COL_UNPAID, sum(students, StudentBlock::unpaid), styles.money);
        for (int index = 0; index < months.size(); index++) {
            BigDecimal monthTotal = BigDecimal.ZERO;
            for (StudentBlock student : students) {
                monthTotal = monthTotal.add(totalOfMonth(student, months.get(index)));
            }
            setMoney(total, FIRST_MONTH_COL + index, monthTotal, styles.money);
        }
        int tail = FIRST_MONTH_COL + months.size();
        setMoney(total, tail, sum(students, StudentBlock::totalOccurrence), styles.money);
        setMoney(total, tail + 1, sum(students, StudentBlock::balance), styles.money);
        // 表尾第一行的「合计」横跨前五列（学号/姓名/家长/电话/支付宝），与学员块的纵向合并呼应
        merge(sheet, startRow, startRow, COL_STUDENT_NO, COL_ALIPAY_PHONE);
        return rowCursor + 1;
    }

    private static void writeFooterMonthCells(Row row, Styles styles, List<YearMonth> months,
                                              ItemRef ref, List<StudentBlock> students) {
        for (int index = 0; index < months.size(); index++) {
            YearMonth month = months.get(index);
            BigDecimal total = BigDecimal.ZERO;
            if (ref != null) {
                for (StudentBlock student : students) {
                    for (FeeMonthlyView rowView : student.monthly()) {
                        if (ref.feeItemId().equals(rowView.feeItemId())
                            && YearMonth.from(rowView.billingMonth()).equals(month)) {
                            total = total.add(nz(rowView.occurrenceAmount()));
                        }
                    }
                }
            }
            setMoney(row, FIRST_MONTH_COL + index, total, styles.money);
        }
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

    private static BigDecimal sum(List<StudentBlock> students,
                                  java.util.function.Function<StudentBlock, BigDecimal> getter) {
        return students.stream().map(getter).map(FeeMatrixExportBuilder::nz)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void setColumnWidths(Sheet sheet, int monthCount) {
        int[] fixed = {14, 12, 12, 15, 16, 14, 10, 12, 12, 12, 12, 12, 12, 10};
        for (int index = 0; index < fixed.length; index++) {
            sheet.setColumnWidth(index, fixed[index] * 256);
        }
        for (int index = 0; index < monthCount; index++) {
            sheet.setColumnWidth(FIRST_MONTH_COL + index, 10 * 256);
        }
        int tail = FIRST_MONTH_COL + monthCount;
        sheet.setColumnWidth(tail, 14 * 256);
        sheet.setColumnWidth(tail + 1, 13 * 256);
        sheet.setColumnWidth(tail + 2, 26 * 256);
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
        private final CellStyle normal;
        private final CellStyle text;
        private final CellStyle bold;
        private final CellStyle money;
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
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            normal = workbook.createCellStyle();
            normal.setAlignment(HorizontalAlignment.LEFT);

            text = bordered(workbook);
            text.setVerticalAlignment(VerticalAlignment.CENTER);
            text.setWrapText(true);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold = bordered(workbook);
            bold.setFont(boldFont);
            bold.setVerticalAlignment(VerticalAlignment.CENTER);

            money = bordered(workbook);
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            money.setVerticalAlignment(VerticalAlignment.CENTER);

            qty = bordered(workbook);
            qty.setDataFormat(workbook.createDataFormat().getFormat("0.##"));
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
