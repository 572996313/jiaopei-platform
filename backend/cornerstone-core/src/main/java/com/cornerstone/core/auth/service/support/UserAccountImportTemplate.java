package com.cornerstone.core.auth.service.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 生成用户账号批量导入的 xlsx 模板。
 *
 * <p>模板与解析必须共用同一套列顺序：这里是唯一一处定义表头文字的地方，
 * {@code UserAccountImportRow} 用列下标读，改了这里的顺序不改那边就会整批错位。</p>
 */
public final class UserAccountImportTemplate {

    /** 数据页必须是第一个工作表，解析按第一个工作表读 */
    public static final String DATA_SHEET = "账号导入";
    public static final String GUIDE_SHEET = "填写说明";

    private static final String[] HEADERS = {
        "登录账号*", "显示名称*", "初始口令*", "手机号", "角色编码*", "状态", "备注"
    };

    /** 列宽单位是 1/256 个字符宽，按表头文字长度手调 */
    private static final int[] COLUMN_WIDTHS = {22, 18, 18, 16, 28, 14, 28};

    /** 状态下拉：服务端只认这两个值，填别的会被逐行判错而不是静默当成启用 */
    private static final String[] STATUS_VALUES = {"active", "disabled"};

    private static final int LAST_EDITABLE_ROW = 5000;

    /** 状态列下标，要与 HEADERS 里的「状态」对齐 */
    private static final int STATUS_COLUMN = 5;

    private static final List<String> GUIDE_LINES = List.of(
        "一、每一行 = 一个要新建的登录账号。这个模板只用来开新号，不用来批量改资料。",
        "     本机构已存在的同号会跳过（预览里提示、不算错误）；同号被其他机构占用时会报错，必须换号——",
        "     登录账号是全系统唯一的，不是本机构内唯一。",
        "",
        "二、列怎么填：",
        "     登录账号：必填，建议用手机号或工号这类本人记得住的串。",
        "     显示名称：必填，登录后展示的名字。",
        "     初始口令：必填，且必须满足系统的密码强度要求。留空不会给你默认口令——",
        "          系统里有个公共初始口令，批量留空等于给所有人发同一个号，所以这一列空着就报错。",
        "     手机号：选填，只用于列表展示和检索，登录不认手机号。",
        "     角色编码：必填，多个用英文逗号分隔，如 PLATFORM_ADMIN。取值见角色管理页的「角色编码」。",
        "     状态：选填，只能填 active（启用）或 disabled（停用），不填按 active。",
        "     备注：选填，会写进账号的备注字段。",
        "",
        "三、归属机构不用填：新建的账号一律挂在当前登录账号所属的机构下。",
        "",
        "四、新建出来的账号会带「首次登录必须改密」标记。",
        "     本人第一次登录会被要求设置自己的口令，之后才进系统；",
        "     机构这边只需要把「登录账号 + 初始口令」两列交给本人，不要替他登录。",
        "",
        "五、先点「预览校验」确认没有红字错误，再点「确认导入」。预览不会写入任何数据。",
        "     任意一行有错误，整批都不落库，改完重新上传即可。"
    );

    private UserAccountImportTemplate() {
    }

    public static byte[] build() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDataSheet(workbook);
            writeGuideSheet(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成账号导入模板失败", exception);
        }
    }

    private static void writeDataSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(DATA_SHEET);
        CellStyle headerStyle = headerStyle(workbook);
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(HEADERS[index]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
        }
        addStatusDropdown(sheet);
        // 冻结表头：客户横向看列的时候要知道自己填的是哪一列
        sheet.createFreezePane(0, 1);
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static void addStatusDropdown(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
            helper.createExplicitListConstraint(STATUS_VALUES);
        CellRangeAddressList range =
            new CellRangeAddressList(1, LAST_EDITABLE_ROW, STATUS_COLUMN, STATUS_COLUMN);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("状态取值不对", "只能填 " + String.join(" 或 ", STATUS_VALUES) + "，留空按 active。");
        sheet.addValidationData(validation);
    }

    private static void writeGuideSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        for (int index = 0; index < GUIDE_LINES.size(); index++) {
            sheet.createRow(index).createCell(0).setCellValue(GUIDE_LINES.get(index));
        }
        sheet.setColumnWidth(0, 110 * 256);
    }
}
