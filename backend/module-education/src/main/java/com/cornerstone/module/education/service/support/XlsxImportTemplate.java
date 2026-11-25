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
 * 学员 / 班级 / 分班 三份批量导入的 xlsx 模板。
 *
 * <p>模板与解析必须共用同一套列顺序：这里是唯一一处定义表头文字的地方，
 * 三个 {@code *ImportRow} 用列下标读，改了这里的顺序不改那边就会整批错位。</p>
 *
 * <p>字典列（性别/学员状态/报名渠道）的下拉值由调用方从系统字典现取现传，不在这里写死：
 * 字典条目可被运维扩充，模板里写死会让新值被 Excel 拦住。班级状态、名册状态是代码判定值、
 * 不入字典，下拉只能给代码，说明页负责用中文解释每个代码的含义。</p>
 */
public final class XlsxImportTemplate {

    /** 数据页必须是第一个工作表，解析按第一个工作表读 */
    public static final String STUDENT_SHEET = "学员档案导入";
    public static final String CLASS_SHEET = "班级档案导入";
    public static final String CLASS_STUDENT_SHEET = "分班导入";
    public static final String GUIDE_SHEET = "填写说明";

    /** 班级状态是代码判定值，模板下拉与导入都只认这组代码，中文含义写在说明页 */
    private static final List<String> CLASS_STATUS_CODES =
        List.of("planning", "active", "inactive", "closed");

    /** Excel 显式下拉的公式串上限是 255 字符，字典条目多了必须放弃下拉而不是让模板下载直接报错 */
    private static final int DROPDOWN_VALUE_BUDGET = 200;

    private static final int LAST_EDITABLE_ROW = 5000;

    private XlsxImportTemplate() {
    }

    // ------------------------------------------------------------------ 学员

    private static final String[] STUDENT_HEADERS = {
        "学号", "学员姓名*", "性别", "出生日期", "监护人姓名", "监护人电话", "状态", "入学日期", "备注"
    };
    private static final int[] STUDENT_WIDTHS = {18, 16, 12, 16, 16, 18, 12, 16, 28};
    private static final int STUDENT_GENDER_COLUMN = 2;
    private static final int STUDENT_STATUS_COLUMN = 6;

    public static byte[] student(List<String> genderNames, List<String> statusNames) {
        List<String> guide = List.of(
            "一、每一行 = 一个要新建的学员档案。本机构已存在同学号的学员会跳过（预览里提示、不算错误）；",
            "     要改已有学员的资料，请到学员页的「编辑」。",
            "",
            "二、列怎么填：",
            "     学号：选填。留空时导入阶段按编号规则自动生成；填了就必须本机构内唯一。",
            "     学员姓名：必填。",
            "     性别：选填，填 " + String.join("/", genderNames) + " 之一（系统字典 gender 启用条目的中文名）。",
            "     出生日期：选填，文本形式填 2026-03-05，不能晚于今天。",
            "     监护人姓名 / 监护人电话：选填，只做联系人登记。",
            "     状态：选填，填 " + String.join("/", statusNames)
                + " 之一（系统字典 student_status 启用条目的中文名），留空按在读。",
            "     入学日期：选填，不能晚于今天。",
            "     备注：选填。",
            "",
            "三、学员档案是机构级的，没有校区列：校区由所在班级推导。要把学员放进班，",
            "     请用「分班与名册」页的「批量分班」，或在本页导入完成后到名册页新增入班。",
            "",
            "四、本模板不创建登录账号。学员/家长的小程序账号走小程序自助注册，或到用户管理页开通后绑定。",
            "",
            "五、先点「预览校验」确认没有红字错误，再点「确认导入」。预览不会写入任何数据。",
            "     任意一行有错误，整批都不落库，改完重新上传即可。单次不超过 500 行。"
        );
        return build(STUDENT_SHEET, STUDENT_HEADERS, STUDENT_WIDTHS,
            List.of(new Dropdown(STUDENT_GENDER_COLUMN, "性别取值不对", genderNames),
                new Dropdown(STUDENT_STATUS_COLUMN, "学员状态取值不对", statusNames)),
            guide);
    }

    // ------------------------------------------------------------------ 班级

    private static final String[] CLASS_HEADERS = {
        "班级编码", "班级名称*", "校区编码*", "学期编码", "课程编码", "班型编码",
        "主讲教师编码", "容量上限", "每周应上节数", "开班日期", "状态", "备注"
    };
    private static final int[] CLASS_WIDTHS = {18, 20, 16, 16, 16, 14, 16, 12, 16, 16, 12, 28};
    private static final int CLASS_STATUS_COLUMN = 10;

    public static byte[] teachingClass() {
        List<String> guide = List.of(
            "一、每一行 = 一个要新建的班级主档。同一校区下已存在同编码的班级会跳过（预览里提示、不算错误）；",
            "     同一校区下班级名称不能重复，重名会报错。要改已有班级，请到「班级档案」页编辑。",
            "",
            "二、列怎么填：",
            "     班级编码：选填。留空时导入阶段按编号规则自动生成；填了就必须同一校区内唯一。",
            "     班级名称：必填，同一校区内唯一。",
            "     校区编码：必填，取值见「校区管理」页的校区编码，写错会报错并列出本机构可用编码。",
            "     学期编码：选填，见学期维护页；留空表示暂不归属学期。",
            "     课程编码 / 班型编码 / 主讲教师编码：选填，分别见课程、班型、教师页的编码。",
            "     容量上限：选填，填 0 或留空表示不限人数。",
            "     每周应上节数：选填，正整数，用于自动排课与课时完成度；留空表示未设定。",
            "     开班日期：选填，文本形式填 2026-03-05；可以填未来的日期（规划中的班）。",
            "     状态：选填，填 planning / active / inactive / closed 之一，",
            "          分别对应 规划中 / 启用 / 停用 / 已结课，留空按 planning。",
            "     备注：选填。",
            "",
            "三、本模板只建班级，不建校区 / 学期 / 课程 / 班型 / 教师：这些要先在对应页面维护好。",
            "     班级编码、名称、名额都在所属校区内判定，跨校区可以有同名班、同编码班。",
            "",
            "四、先点「预览校验」确认没有红字错误，再点「确认导入」。预览不会写入任何数据。",
            "     任意一行有错误，整批都不落库，改完重新上传即可。单次不超过 500 行。"
        );
        return build(CLASS_SHEET, CLASS_HEADERS, CLASS_WIDTHS,
            List.of(new Dropdown(CLASS_STATUS_COLUMN, "班级状态取值不对", CLASS_STATUS_CODES)),
            guide);
    }

    // ------------------------------------------------------------------ 分班

    private static final String[] CLASS_STUDENT_HEADERS = {
        "班级编码*", "校区编码", "学号*", "学员姓名(核对)", "来源渠道", "入班日期", "备注"
    };
    private static final int[] CLASS_STUDENT_WIDTHS = {18, 16, 18, 16, 16, 16, 28};
    private static final int CLASS_STUDENT_CHANNEL_COLUMN = 4;

    public static byte[] classStudent(List<String> channelNames) {
        List<String> guide = List.of(
            "一、每一行 = 一个学员加入一个班（入班即报名）。学员与班级都必须先存在：",
            "     学员请先用「学员管理」页的「导入学员」建好，班级见「班级档案」页。",
            "",
            "二、列怎么填：",
            "     班级编码：必填，见「班级档案」页。",
            "     校区编码：选填。同一个班级编码在多个校区都存在时必填，用来区分是哪个校区的班。",
            "     学号：必填，见「学员管理」页。",
            "     学员姓名：选填，只用于核对；填了之后和学号对不上会报错，防止串行。",
            "     来源渠道：选填，填 " + String.join("/", channelNames)
                + " 之一（系统字典 enrollment_channel 启用条目的中文名）。",
            "     入班日期：选填，文本形式填 2026-03-05，留空按今天。",
            "     备注：选填。",
            "",
            "三、重复处理：该学员已经在这个班里时跳过（预览里提示、不算错误），重传整份文件不会整批失败；",
            "     同一份文件里同一班级同一学号出现多行会报错。",
            "",
            "四、容量在预览阶段按「现有在班人数 + 本文件已接受的行数」预检，超出班级容量上限的行会报错。",
            "",
            "五、先点「预览校验」确认没有红字错误，再点「确认导入」。预览不会写入任何数据。",
            "     任意一行有错误，整批都不落库，改完重新上传即可。单次不超过 500 行。"
        );
        return build(CLASS_STUDENT_SHEET, CLASS_STUDENT_HEADERS, CLASS_STUDENT_WIDTHS,
            List.of(new Dropdown(CLASS_STUDENT_CHANNEL_COLUMN, "来源渠道取值不对", channelNames)),
            guide);
    }

    // ------------------------------------------------------------------ 生成

    private record Dropdown(int column, String title, List<String> values) {
    }

    private static byte[] build(String dataSheetName, String[] headers, int[] widths,
                                List<Dropdown> dropdowns, List<String> guideLines) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDataSheet(workbook, dataSheetName, headers, widths, dropdowns);
            writeGuideSheet(workbook, guideLines);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("生成批量导入模板失败", exception);
        }
    }

    private static void writeDataSheet(XSSFWorkbook workbook, String sheetName, String[] headers,
                                       int[] widths, List<Dropdown> dropdowns) {
        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle headerStyle = headerStyle(workbook);
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, widths[index] * 256);
        }
        for (Dropdown dropdown : dropdowns) {
            addDropdown(sheet, dropdown);
        }
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

    private static void addDropdown(Sheet sheet, Dropdown dropdown) {
        List<String> values = dropdown.values();
        if (values.isEmpty() || String.join(",", values).length() > DROPDOWN_VALUE_BUDGET) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
            helper.createExplicitListConstraint(values.toArray(new String[0]));
        CellRangeAddressList range =
            new CellRangeAddressList(1, LAST_EDITABLE_ROW, dropdown.column(), dropdown.column());
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox(dropdown.title(),
            "只能填：" + String.join("、", values) + "，留空按说明页处理。");
        sheet.addValidationData(validation);
    }

    private static void writeGuideSheet(XSSFWorkbook workbook, List<String> lines) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        for (int index = 0; index < lines.size(); index++) {
            sheet.createRow(index).createCell(0).setCellValue(lines.get(index));
        }
        sheet.setColumnWidth(0, 110 * 256);
    }
}
