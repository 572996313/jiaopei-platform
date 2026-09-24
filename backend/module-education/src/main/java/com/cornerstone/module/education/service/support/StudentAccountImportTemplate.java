package com.cornerstone.module.education.service.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 账号批量导入模板的生成器。
 *
 * <p>解析走 EasyExcel，模板生成用裸 POI：这里要的是下拉校验、冻结表头这类格式控制而不是海量行写入，
 * POI 已随 EasyExcel 传递进来，不额外引依赖。</p>
 */
public final class StudentAccountImportTemplate {

    /** 数据页必须是第一个工作表，解析按第一个工作表读 */
    public static final String DATA_SHEET = "账号导入";
    public static final String GUIDE_SHEET = "填写说明";

    private static final String[] HEADERS = {
        "学员编号*", "学员姓名(核对)", "账号类型*", "账号姓名*", "手机号", "登录账号(留空自动取)", "备注"
    };

    /** 列宽单位是 1/256 个字符宽，按表头文字长度手调 */
    private static final int[] COLUMN_WIDTHS = {18, 16, 14, 16, 16, 24, 24};

    private static final String[] ACCOUNT_KINDS = {"家长", "学生本人"};

    private static final List<String> GUIDE_LINES = List.of(
        "一、每一行代表「一个账号绑定一个学员」，不是「一个学员一行」。",
        "     同一个家长有两个孩子，就把这个家长的手机号填两行、学号各一，系统只会建一个账号、绑两条关系。",
        "",
        "二、列怎么填：",
        "     学员编号：必填，须是系统里已存在的学员档案编号（学员管理页可查）。",
        "     学员姓名：选填，填了就必须和档案完全一致，用来防止编号填错绑到别人身上。",
        "     账号类型：必填，只能选「家长」或「学生本人」。选家长＝给监护人建账号；选学生本人＝给学员自己建账号。",
        "     账号姓名：必填，登录后的显示名称，一般填家长姓名或学生本人姓名。",
        "     手机号：选填。学生没有手机就空着，不必硬凑一个号——登录不靠手机号，激活码也是按账号发的。",
        "     登录账号：选填，不填时自动取「手机号，没有手机号就取学员编号」。",
        "          学员编号只在你们机构内唯一，登录账号要全系统唯一，撞上了会提示你在这一列换一个。",
        "     备注：选填，会写进账号的备注字段。",
        "",
        "三、建出来的账号是「待激活」状态，还不能登录。",
        "     导入完成后在同一个页面勾选账号点「生成激活码」，把激活码发给家长；",
        "     家长在登录页点「账号待激活？设置密码」，填激活码和自己的密码即可启用，启用后自动登录。",
        "",
        "四、先点「预览校验」确认没有红字错误，再点「确认导入」。预览不会写入任何数据。",
        "     校验不通过的行会标出原因，整批都不会落库，改完重新上传即可。",
        "",
        "五、账号类型对应的角色由系统固定：家长＝小程序家长端，学生本人＝小程序学生端。"
    );

    private static final List<String> ROSTER_GUIDE_LINES = List.of(
        "这份文件的「学员编号 / 学员姓名」两列已经按系统里**还没有登录账号**的学员填好了，你只需要补后面几列。",
        "     最少填法：只补「账号类型」和「账号姓名」两列就能导。学生本人账号没有手机号也没关系，登录账号会自动取学号。",
        "     一个学员都不漏的做法：这份名册填完直接上传即可，不必再自己抄学号。",
        "     一个孩子有多个家长：复制那一行，改成另一位家长的手机号（同一学号出现两行、账号类型都填家长是允许的）。",
        "     已经开过账号的学员不在这份名册里；确实要给同一学员再加账号，自己在下面追加行、填学号即可。",
        "",
        "以下是通用填写规则：",
        ""
    );


    /** 名册预填行：只带前两列，其余列留空给业务员填 */
    public record RosterRow(String studentNo, String studentName) {
    }

    public static byte[] build() {
        return build(List.of());
    }

    /** rows 为空即空白模板；非空即「待开账号学员名册」，前两列预填、后面几列留给业务员补 */
    public static byte[] build(List<RosterRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDataSheet(workbook, rows);
            writeGuideSheet(workbook, rows.isEmpty());
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成账号导入模板失败", exception);
        }
    }

    private static void writeDataSheet(XSSFWorkbook workbook, List<RosterRow> rows) {
        XSSFSheet sheet = workbook.createSheet(DATA_SHEET);
        CellStyle headerStyle = headerStyle(workbook);
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(HEADERS[index]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
        }
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < rows.size(); index++) {
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(rows.get(index).studentNo());
            row.createCell(1).setCellValue(rows.get(index).studentName());
        }

        // 下拉只铺到 5000 行，够用且不会让文件变大
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
        XSSFDataValidationConstraint constraint =
            (XSSFDataValidationConstraint) helper.createExplicitListConstraint(ACCOUNT_KINDS);
        CellRangeAddressList range = new CellRangeAddressList(1, 5000, 2, 2);
        sheet.addValidationData(helper.createValidation(constraint, range));
    }

    private static void writeGuideSheet(XSSFWorkbook workbook, boolean blankTemplate) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        List<String> lines = new ArrayList<>();
        if (!blankTemplate) {
            lines.addAll(ROSTER_GUIDE_LINES);
        }
        lines.addAll(GUIDE_LINES);
        for (int index = 0; index < lines.size(); index++) {
            Row row = sheet.createRow(index);
            row.createCell(0).setCellValue(lines.get(index));
            row.getCell(0).setCellStyle(style);
        }
        sheet.setColumnWidth(0, 110 * 256);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
