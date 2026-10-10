package com.hjh.tlias.storage;

/**
 * 文件存储统一入口。
 *
 * 上传头像的代码只需要调用 upload()，拿回一个 URL。
 * 文件到底存在哪，是「本地磁盘」还是「阿里云 OSS」，由 @Profile 决定：
 *   - 默认（本地开发）：LocalFileStorageServiceImpl，文件落在项目目录的 uploads/ 下
 *   - 激活 oss profile：OssFileStorageServiceImpl，文件传到云上
 * 两种实现的返回格式完全一致——都是一个能直接放进前端 <img src> 的完整 URL，
 * 所以前端和业务代码感知不到存储换过。
 */
public interface FileStorageService {

    /**
     * 保存一个文件。
     *
     * @param content          文件内容
     * @param originalFilename 原始文件名（用来取扩展名，决定存出来的文件叫 .png 还是 .txt）
     * @return 公开可访问的完整 URL
     */
    String upload(byte[] content, String originalFilename);

}
