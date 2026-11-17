package com.shafting.weighing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PackageStructureTest {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+);");
    private static final Set<String> BUSINESS_MODULES = Set.of(
            "alarm", "control", "curve", "device", "measurement", "record", "security", "task");

    @Test
    void shouldPlaceBackendClassesInResponsibilityPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> sourceFiles;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            sourceFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path sourceFile : sourceFiles) {
            Path relativePath = Path.of("src/main/java/com/shafting/weighing").relativize(sourceFile);
            if (relativePath.getNameCount() == 2
                    && BUSINESS_MODULES.contains(relativePath.getName(0).toString())) {
                violations.add(sourceFile.getFileName() + " 不应直接放在业务模块根包");
            }
            String source = Files.readString(sourceFile);
            Matcher matcher = PACKAGE_PATTERN.matcher(source);
            if (!matcher.find()) {
                violations.add(sourceFile + " 缺少 package 声明");
                continue;
            }
            String requiredSuffix = requiredPackageSuffix(sourceFile, source);
            if (requiredSuffix != null && !matcher.group(1).endsWith(requiredSuffix)) {
                violations.add(sourceFile.getFileName() + " 应放入 " + requiredSuffix + " 包");
            }
        }

        assertThat(violations).isEmpty();
    }

    private String requiredPackageSuffix(Path sourceFile, String source) {
        String fileName = sourceFile.getFileName().toString();
        if (fileName.endsWith("Controller.java")) {
            return ".controller";
        }
        if (fileName.endsWith("Mapper.java")) {
            return ".mapper";
        }
        if (fileName.endsWith("Service.java")) {
            return ".service";
        }
        if (fileName.endsWith("Dto.java")) {
            return ".dto";
        }
        if (fileName.endsWith("Properties.java")) {
            return ".config";
        }
        if (source.contains("@TableName")) {
            return ".entity";
        }
        return null;
    }
}
