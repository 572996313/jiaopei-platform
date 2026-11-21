package com.cornerstone.core.file.storage;

import com.cornerstone.core.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地存储实现的纯文件系统测试：仓库无 {@code @SpringBootTest}，这里直接 new 实例。
 *
 * <p>重点守两条：读回字节与写入一致；对象键不能越出根目录（路径穿越是这条实现唯一的安全边界）。</p>
 */
class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void putThenGetReturnsSameBytes() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        byte[] content = "教培文件内容".getBytes(StandardCharsets.UTF_8);

        storage.put("1/2026/11/a.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        try (InputStream in = storage.get("1/2026/11/a.txt")) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
        // 对象键里的目录要真的建出来，且只会落在根目录之下
        assertThat(Files.isRegularFile(root.resolve("1/2026/11/a.txt"))).isTrue();
    }

    @Test
    void deleteRemovesObject() {
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        byte[] content = {1, 2, 3};
        storage.put("a.bin", new ByteArrayInputStream(content), content.length, null);

        storage.delete("a.bin");

        assertThatThrownBy(() -> storage.get("a.bin"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("文件不存在");
    }

    @Test
    void pathTraversalObjectKeyIsRejected() {
        LocalFileStorage storage = new LocalFileStorage(root.toString().concat("/data"));
        byte[] content = {1};

        assertThatThrownBy(() -> storage.put("../escape.txt", new ByteArrayInputStream(content), 1, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非法的对象键");
        assertThatThrownBy(() -> storage.get("../../etc/passwd"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非法的对象键");
        assertThatThrownBy(() -> storage.delete("../escape.txt"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非法的对象键");
    }

    @Test
    void blankObjectKeyIsRejected() {
        LocalFileStorage storage = new LocalFileStorage(root.toString());

        assertThatThrownBy(() -> storage.get(" "))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非法的对象键");
    }
}
