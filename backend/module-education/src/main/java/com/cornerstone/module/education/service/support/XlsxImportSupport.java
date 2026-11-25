package com.cornerstone.module.education.service.support;

import com.alibaba.excel.EasyExcel;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.multipart.MultipartFile;

/**
 * xlsx 批量导入的公共解析与校验工具。
 *
 * <p>学员/班级/分班三份导入与教师导入共用这几个口子：文件校验、读第一个工作表、单次行数上限、
 * 数值单元格还原、日期解析、机构只认登录态。抽出来不是为了少写几行，而是这些口径一旦各写一份，
 * 迟早出现"教师导入认 2026/3/5、学员导入不认"这类漂移。</p>
 */
public final class XlsxImportSupport {

    /** 单次导入上限：与底座账号导入、教师导入保持一致 */
    public static final int MAX_ROWS = 500;

    /** Excel 把学号/手机号一类纯数字存成数值时，EasyExcel 会回科学计数法或带 .0 的形态，需要先还原成整串数字 */
    private static final Pattern NUMERIC_FORM = Pattern.compile("^\\d+(\\.\\d*)?[Ee][-+]?\\d+$|^\\d+\\.0+$");

    /** 日期容忍 2026-03-05 / 2026/3/5 / 2026.3.5：先把 . 和 / 归一成 -，再单次解析 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-M-d");

    private XlsxImportSupport() {
    }

    /**
     * 校验后缀并读取第一个工作表。
     *
     * <p>只支持 xlsx、内容必须在第一个工作表：模板的第二个表是填写说明，客户误把数据填到说明表时，
     * 这里读不到表头会直接报解析失败，比静默导入零行好排查。</p>
     */
    public static <T> List<T> readRows(MultipartFile file, Class<T> head) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要导入的 xlsx 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只支持 xlsx 格式，请先下载模板填写");
        }
        try (InputStream in = file.getInputStream()) {
            return EasyExcel.read(in).head(head).sheet().doReadSync();
        } catch (IOException | RuntimeException exception) {
            // 文件损坏、被加密、表头行缺失都汇到这里，给可操作的提示而不是 500
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "文件解析失败：" + rootMessage(exception) + "。请确认使用系统模板且内容在第一个工作表");
        }
    }

    public static void assertRowLimit(int rowCount) {
        if (rowCount > MAX_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次导入不超过 " + MAX_ROWS + " 行");
        }
    }

    /**
     * 归属机构只从登录态取。批量入口一旦允许传机构参数，等于允许往别人机构里整批写数据，
     * 所以三份导入的 DTO 里都没有机构列、接口也不收 institutionId。
     */
    public static Long requireInstitution(String subject) {
        AuthenticatedUser current = AuthContext.requireUser();
        if (current.orgId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "当前登录账号未绑定机构，无法批量导入" + subject);
        }
        return current.orgId();
    }

    /** 取空白并把被 Excel 存成数值的串还原回数字文本；整串空白归 null */
    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (NUMERIC_FORM.matcher(trimmed).matches()) {
            return new BigDecimal(trimmed).toBigInteger().toString();
        }
        return trimmed;
    }

    /** 日期列解析失败收成行级错误，不抛异常打断整份文件的校验 */
    public static LocalDate parseDate(String raw, String fieldLabel, List<String> errors) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.replace('.', '-').replace('/', '-'), DATE);
        } catch (DateTimeParseException exception) {
            errors.add(fieldLabel + "格式不对：" + raw + "，请按 2026-03-05 填写");
            return null;
        }
    }

    /** 留空的字典列不参与翻译，也绝不进 Map.of 键位——不可变 Map 对 null 键直接抛 NPE */
    public static String labelToValue(Map<String, String> labelToValue, String raw) {
        return raw == null || raw.isBlank() ? raw : labelToValue.getOrDefault(raw.trim(), raw);
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
