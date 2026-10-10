package com.hjh.tlias.util;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 导出的公共工具。
 *
 * 只做「把一批对象写成一个 xlsx 塞进 HTTP 响应」这一件事。
 * 具体的列由传入的 clazz 上的 @ExcelProperty 决定，所以这个类不关心
 * 导的是什么业务数据 —— 以后班级、学员要导出，直接复用。
 */
public final class ExcelUtil {

    /** 工具类不该被 new，私有构造就是明确表达这一点 */
    private ExcelUtil() {
    }

    /**
     * 把 rows 写成 xlsx 并作为附件下载。
     *
     * @param rows     要写的数据，空列表会导出一份只有表头的文件
     * @param clazz    数据类，靠它上面的 @ExcelProperty 决定表头和列
     * @param fileName 文件名（不含扩展名），支持中文
     */
    public static <T> void write(HttpServletResponse response, List<T> rows,
                                 Class<T> clazz, String fileName) throws IOException {

        // 这一串 MIME 类型就是 .xlsx 的标准写法，写错浏览器可能直接预览而不是下载
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        // 这里**不能**调 response.setCharacterEncoding("utf-8")。
        // 实测踩过：setCharacterEncoding 会把 ";charset=utf-8" 追加到 Content-Type 后面，
        // 变成 "application/vnd...sheet;charset=utf-8"，前端按精确值判断类型就会失配。
        // 而且 xlsx 是二进制，字符编码对它本来就没意义 ——
        // 文件名里的中文是靠下面那行 RFC 5987 编码解决的，不靠 setCharacterEncoding

        // 文件名的编码是这个工具类里唯一需要动脑的地方。
        //
        // HTTP 响应头只能放 ASCII，所以中文文件名不能直接写进 Content-Disposition。
        // 标准做法是 RFC 5987 的 filename*=utf-8''<百分号编码> 形式。
        //
        // 两个容易踩的细节：
        //   - URLEncoder 是给「查询参数」设计的，它把空格编成 +，
        //     而响应头里 + 就是加号本身，得手工换回 %20
        //   - 不写 filename* 而写老的 filename= 的话，中文会变乱码或被截断
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded + ".xlsx");

        // doWrite 会自己接管并关闭输出流，不要在外面再关一次
        EasyExcel.write(response.getOutputStream(), clazz)
                .sheet("数据")
                .doWrite(rows);
    }

}
