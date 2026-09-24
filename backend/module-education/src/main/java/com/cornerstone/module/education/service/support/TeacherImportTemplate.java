package com.cornerstone.module.education.service.support;

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
 * 生成教师档案批量导入的 xlsx 模板。
 *
 * <p>模板与解析必须共用同一套列顺序：这里是唯一一处定义表头文字的地方，
 * {@code TeacherImportRow} 用列下标读，改了这里的顺序不改那边就会整批错位。</p>
 *
 * <p>性别/用工类型两列的下拉值由调用方从系统字典现取现传，不在这里写死：
 * 见 AGENTS「枚举值/字典规范」，字典条目可被运维扩充，模板里写死会让新值被 Excel 拦住。</p>
 */
public final class TeacherImportTemplate {

    /** 数据页必须是第一个工作表，解析按第一个工作表读 */
    public static final String DATA_SHEET = "教师档案导入";
    public static final String GUIDE_SHEET = "填写说明";

    private static final String[] HEADERS = {
        "教师编码*", "教师姓名*", "手机号", "校区编码", "性别", "职称",
        "用工类型", "入职日期", "状态", "备注"
    };

    /** 列宽单位是 1/256 个字符宽，按表头文字长度手调 */
    private static final int[] COLUMN_WIDTHS = {20, 16, 16, 18, 12, 14, 16, 16, 12, 28};

    private static final int GENDER_COLUMN = 4;
    private static final int EMPLOYMENT_COLUMN = 6;
    private static final int STATUS_COLUMN = 8;

    /** 状态由代码分支判定，不进字典（见 AGENTS「枚举值/字典规范」边界），所以在这里写死是安全的 */
    private static final String[] STATUS_VALUES = {"active", "suspended", "left"};

    private static final int LAST_EDITABLE_ROW = 5000;

    /** Excel 显式下拉的公式串上限是 255 字符，字典条目多了必须放弃下拉而不是让模板下载直接报错 */
    private static final int DROPDOWN_VALUE_BUDGET = 200;

    private TeacherImportTemplate() {
    }

    public static byte[] build(List<String> genderNames, List<String> employmentNames) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDataSheet(workbook, genderNames, employmentNames);
            writeGuideSheet(workbook, genderNames, employmentNames);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成教师档案导入模板失败", exception);
        }
    }

    private static void writeDataSheet(XSSFWorkbook workbook, List<String> genderNames,
                                       List<String> employmentNames) {
        Sheet sheet = workbook.createSheet(DATA_SHEET);
        CellStyle headerStyle = headerStyle(workbook);
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(HEADERS[index]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
        }
        addDropdown(sheet, GENDER_COLUMN, "性别取值不对", genderNames);
        addDropdown(sheet, EMPLOYMENT_COLUMN, "用工类型取值不对", employmentNames);
        addDropdown(sheet, STATUS_COLUMN, "状态取值不对", List.of(STATUS_VALUES));
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

    private static void addDropdown(Sheet sheet, int column, String title, List<String> values) {
        if (values.isEmpty() || String.join(",", values).length() > DROPDOWN_VALUE_BUDGET) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
            helper.createExplicitListConstraint(values.toArray(new String[0]));
        CellRangeAddressList range =
            new CellRangeAddressList(1, LAST_EDITABLE_ROW, column, column);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox(title, "只能填：" + String.join("、", values) + "，留空按说明页处理。");
        sheet.addValidationData(validation);
    }

    private static void writeGuideSheet(XSSFWorkbook workbook, List<String> genderNames,
                                        List<String> employmentNames) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        List<String> lines = List.of(
            "一、每一行 = 一个要新建的教师档案。这个模板只用来批量建档，不用来批量改资料。",
            "     本机构已存在同编码的教师会跳过（预览里提示、不算错误）；",
            "     要用这个文件更新已有教师的资料，请走教师页的「编辑」。",
            "",
            "二、列怎么填：",
            "     教师编码：必填，本机构内唯一，建议用校内在用的工号。",
            "     教师姓名：必填。",
            "     手机号：选填，但强烈建议填——「匹配账号」就是按手机号把档案和本机构已有的登录账号绑起来，",
            "          没有手机号只能到编辑页手动改绑。手机号在本机构内不能和别的教师重复。",
            "     校区编码：选填，留空表示暂不归属校区。取值见「校区管理」页的校区编码，写错会报错。",
            "     性别：选填，填 " + String.join("/", genderNames) + " 之一（系统字典 gender 启用条目的中文名）。",
            "     职称：选填，自由文本。",
            "     用工类型：选填，填 " + String.join("/", employmentNames)
                + " 之一（系统字典 employment_type 启用条目的中文名），留空按全职。",
            "     入职日期：选填，文本形式填 2026-03-05，不能晚于今天。",
            "     状态：选填，只能填 active（在职）、suspended（暂停）、left（离职），留空按 active。",
            "     备注：选填。",
            "",
            "三、归属机构不用填：新建的档案一律挂在当前登录账号所属的机构下。",
            "",
            "四、这个模板不建登录账号。开账号请走「系统设置 → 用户管理 → 批量导入账号」，",
            "     账号建好后回到教师页点「匹配账号」，两边手机号一致的会自动绑定。",
            "",
            "五、先点「预览校验」确认没有红字错误，再点「确认导入」。预览不会写入任何数据。",
            "     任意一行有错误，整批都不落库，改完重新上传即可。单次不超过 500 行。"
        );
        for (int index = 0; index < lines.size(); index++) {
            sheet.createRow(index).createCell(0).setCellValue(lines.get(index));
        }
        sheet.setColumnWidth(0, 110 * 256);
    }
}
