package com.hjh.tlias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.config.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1b 文件上传的验收测试（先写、先跑红，再实现）。
 *
 * 覆盖三段链路：
 *   上传（POST /upload，需要登录）→ 落盘到本地磁盘 → 通过 URL 读回（GET /uploads/**，免登录）
 *
 * 两个刻意的设计：
 *
 * 1）properties 把落盘目录指到 target/test-uploads，不污染项目根目录的 uploads/。
 *    放 target 下的好处是 mvn clean 顺手就清了，测试产物天然和正式文件隔离。
 *
 * 2）类上有 @Transactional，但它只回滚数据库改动（第 6 条用例会把头像 URL
 *    存进员工记录）。磁盘上写出去的文件不归事务管——好在目录在 target/ 里，
 *    本来就是一次性的。
 */
@SpringBootTest(properties = "storage.local.dir=target/test-uploads")
@AutoConfigureMockMvc
@Transactional
class UploadFlowTest {

    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    private String token;

    @BeforeEach
    void loginFirst() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = objectMapper.readTree(body).path("data").path("token").asText();
    }

    // ------------------------------------------------------------------
    //  登录保护
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 不带 token 上传：401，和别的业务接口一样被拦截器拦下")
    void uploadWithoutTokenShouldBeRejected() throws Exception {
        mockMvc.perform(multipart("/upload").file(png("avatar.png")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("未登录，请先登录"));
    }

    // ------------------------------------------------------------------
    //  上传成功 + 读回
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2. 合法 PNG：返回公网 URL，且不带 token 也能按 URL 读回原字节")
    void uploadPngThenFetchByUrl() throws Exception {
        byte[] original = pngBytes();

        String url = upload("avatar.png", original);
        System.out.println("\n[2] 上传成功，返回 URL = " + url);

        // URL 形态：base-url + /uploads/yyyy/MM/dd/uuid.png（日期分段是给文件分类的习惯做法）
        assertTrue(url.matches(".+/uploads/\\d{4}/\\d{2}/\\d{2}/.+\\.png"),
                "URL 应该形如 .../uploads/2026/10/10/xxxx.png，实际是 " + url);

        // 按返回的 URL 再读一次。这一步同时证明三件事：
        //   a) 静态资源映射真的把 /uploads/** 映到了磁盘目录
        //   b) 拦截器确实放行了 /uploads/**（否则这里会是 401）
        //   c) 存进去的字节和拿出来的字节完全一致（没编坏）
        byte[] fetched = mockMvc.perform(get(pathOf(url)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(original, fetched, "读回的字节应该和上传的一模一样");
    }

    // ------------------------------------------------------------------
    //  非法入参
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3. txt 文件：拒绝，且提示支持哪些格式")
    void txtFileShouldBeRejected() throws Exception {
        String msg = uploadExpectingError("伪装.txt", "hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("\n[3] txt 被拒，提示 = " + msg);
        assertTrue(msg.contains("jpg"), "提示里应该说清支持的格式");
    }

    @Test
    @DisplayName("4. 空文件：拒绝，不能存出一个 0 字节的『图片』")
    void emptyFileShouldBeRejected() throws Exception {
        String msg = uploadExpectingError("empty.png", new byte[0]);
        System.out.println("\n[4] 空文件被拒，提示 = " + msg);
        assertTrue(msg.contains("空"), "提示应该点明是空文件问题");
    }

    @Test
    @DisplayName("5. 表单里没有 file 字段：给出人话提示，而不是 400 或系统繁忙")
    void missingFilePartShouldBeRejected() throws Exception {
        String body = mockMvc.perform(multipart("/upload").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String msg = objectMapper.readTree(body).path("msg").asText();
        System.out.println("\n[5] 没带 file 字段，提示 = " + msg);
        assertTrue(msg.contains("选择"), "提示应该告诉用户去选文件");
    }

    // ------------------------------------------------------------------
    //  头像闭环（P1b「含员工头像上传」的验收点）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6. 上传拿到的 URL 存进 emp.image，员工详情能原样读回")
    void avatarUrlShouldPersistOnEmp() throws Exception {
        String url = upload("avatar.png", pngBytes());

        // 先读出李伟当前的信息，把必填字段原样回填进请求体——
        // PUT 是「整对象提交」，缺了 username / name 这些必填字段会被参数校验打回。
        // 先读后写还有个好处：不需要在测试里硬编码种子数据的细节
        JsonNode before = readData(getWith("/emps/1"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 1);
        body.put("username", USERNAME);
        body.put("name", before.path("name").asText());
        body.put("gender", before.path("gender").asInt());
        body.put("job", before.path("job").asInt());
        body.put("entryDate", before.path("entryDate").asText());
        body.put("image", url);

        mockMvc.perform(put("/emps")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        JsonNode after = readData(getWith("/emps/1"));
        System.out.println("\n[6] 员工头像字段 = " + after.path("image").asText());
        assertEquals(url, after.path("image").asText(), "详情读回的头像 URL 应该和上传返回值一致");
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    /** 上传一张图，返回响应里 data 字段（也就是 URL） */
    private String upload(String filename, byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, content))
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").asText();
    }

    /** 上传一个应该被拒的文件，断言 code=0 并返回 msg */
    private String uploadExpectingError(String filename, byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, content))
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("msg").asText();
    }

    /** 发一次带 token 的 GET，返回未断言的执行结果 */
    private ResultActions getWith(String uri) throws Exception {
        return mockMvc.perform(get(uri).header(jwtProperties.getTokenName(), token));
    }

    /** 对刚发出的请求断言 code=1，并返回响应里的 data 节点 */
    private JsonNode readData(ResultActions actions) throws Exception {
        String body = actions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /** 从完整 URL 里抠出 MockMvc 能直接用的路径 */
    private String pathOf(String url) {
        // 返回的 URL 里带 context-path（http://localhost:8080/api/uploads/...），
        // 但 MockMvc 的请求不经过真实 servlet 容器，也不认识 context-path，
        // 所以把 /api 前缀去掉再发请求
        return URI.create(url).getPath().replaceFirst("^/api", "");
    }

    private MockMultipartFile png(String filename) throws Exception {
        return new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, pngBytes());
    }

    /**
     * 用 ImageIO 现场生成一张 1x1 的合法 PNG。
     *
     * 为什么不用一段写死的 byte 数组？因为手抄的二进制常量没人能看出对错，
     * 改坏一个字节测试照样跑（反正后端不解析图片内容）。
     * 用代码生成则是货真价实的 PNG——字节、Content-Type 都是真的。
     */
    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

}
