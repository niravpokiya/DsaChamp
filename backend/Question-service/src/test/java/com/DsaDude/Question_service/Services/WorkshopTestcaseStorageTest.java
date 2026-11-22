package com.DsaDude.Question_service.Services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkshopTestcaseStorageTest {
    @TempDir
    Path storage;

    @Test
    void appendsWithoutReplacingExistingTestsAndKeepsRetriesIdempotent() throws Exception {
        TestcaseService service = new TestcaseService();
        ReflectionTestUtils.setField(service, "basePath", storage.toString());
        Path problem = Files.createDirectory(storage.resolve("two-sum"));
        Path sample = Files.writeString(problem.resolve("sample1.in"), "existing");
        Files.writeString(problem.resolve("sample1.out"), "existing");

        assertFalse(service.hasCompleteHiddenTests("two-sum"));
        assertTrue(service.appendWorkshopTestcase("two-sum", "1\n", "42\n"));
        assertTrue(service.hasCompleteHiddenTests("two-sum"));
        assertFalse(service.appendWorkshopTestcase("two-sum", "1\n", "42\n"));
        assertThrows(IllegalArgumentException.class, () -> service.appendWorkshopTestcase("two-sum", "1\n", "0"));
        assertEquals("existing", Files.readString(sample));
        try (var files = Files.list(problem)) {
            assertEquals(4, files.count());
        }
        Files.writeString(problem.resolve("orphan.in"), "missing output");
        assertFalse(service.hasCompleteHiddenTests("two-sum"));
    }
}
