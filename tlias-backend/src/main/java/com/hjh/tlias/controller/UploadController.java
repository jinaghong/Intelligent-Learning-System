package com.hjh.tlias.controller;

import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 文件上传接口。
 *
 * multipart/form-data 是什么：普通的 POST 请求体是一整块数据（JSON 或表单文本），
 * 而「文件 + 可能还有别的字段」这种混合内容要用 multipart 格式——
 * 请求体被切成多个 part，每个 part 自带字段名和文件名。
 * Spring 收到后会把文件 part 自动包装成 MultipartFile 参数传进来。
 *
 * 这个接口本身受登录拦截器保护（不在放行名单里），但上传完成后拿到的
 * /uploads/** 地址是免登录的——浏览器 img 标签加载图片时带不了自定义请求头，
 * 那条链路必须能匿名读（详见 WebMvcConfig 里的放行名单）。
 */
@RestController
@RequiredArgsConstructor
public class UploadController {

    /** 白名单只放图片格式。不校验扩展名的话，别人可以传 .jsp / .html 上来当免费图床甚至钓鱼页 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final FileStorageService fileStorageService;

    /**
     * required = false 是刻意的：表单里压根没带 file 字段时，
     * 让请求进到方法里由我们给「请选择要上传的文件」；
     * 如果写 required = true，缺失时 Spring 会抛 MissingServletRequestPartException，
     * 前端收到的会是「系统繁忙」——用户只是忘了选文件，不该得到服务器故障的提示。
     */
    // 上传也记日志，但切面的 serializeArgs 会跳过 MultipartFile——
    // 否则整个文件的字节都会被序列化进 oper_log.method_params
    @Log
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.isEmpty()) {
            throw new BusinessException("不能上传空文件");
        }

        String filename = file.getOriginalFilename();
        if (!ALLOWED_EXTENSIONS.contains(extensionOf(filename))) {
            throw new BusinessException("仅支持 jpg / jpeg / png / gif / webp 格式的图片");
        }

        // 存哪（本地磁盘 or OSS）、URL 怎么拼，由 FileStorageService 的实现决定，
        // 控制器不关心——这就是 P1b 抽这层接口的意义
        return Result.success(fileStorageService.upload(file.getBytes(), filename));
    }

    /** 从文件名里取扩展名并转小写；没有扩展名返回 null（后面会被白名单挡下） */
    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

}
