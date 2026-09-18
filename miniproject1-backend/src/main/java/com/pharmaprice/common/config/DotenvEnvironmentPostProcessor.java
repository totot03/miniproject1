package com.pharmaprice.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 저장소 루트의 {@code .env}를 읽어 낮은 우선순위 PropertySource로 등록한다.
 *
 * <p>이 프로젝트는 Docker를 쓰지 않고 {@code ./mvnw spring-boot:run}이나 IDE의
 * "Run" 버튼으로 직접 기동하는데, 둘 다 OS 환경변수나 IDE 실행 설정에 의존하지 않으면
 * {@code application.yml}의 {@code ${JWT_SECRET}} 같은 플레이스홀더가 비어 있는 채로
 * 넘어가 원인을 알기 어려운 오류(예: WeakKeyException)로 이어진다. 작업 디렉터리가
 * {@code miniproject1-backend/}(mvnw·대부분의 IDE 프로젝트 루트)이든 저장소 루트이든
 * 동작하도록 상위 두 단계까지 {@code .env}를 찾는다.</p>
 *
 * <p>OS 환경변수·JVM 시스템 프로퍼티가 이미 있으면 그 값이 우선한다
 * ({@code addLast}로 등록하기 때문). {@code .env} 파일이 없으면 조용히 건너뛴다.</p>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvFile";
    private static final List<String> SEARCH_DIRS = List.of(".", "..", "../..");
    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = findEnvFile();
        if (envFile == null) {
            return;
        }
        Map<String, Object> values = parse(envFile);
        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
            log.info("환경변수 파일 로드됨: " + envFile.toAbsolutePath());
        }
    }

    private Path findEnvFile() {
        for (String dir : SEARCH_DIRS) {
            Path candidate = Path.of(dir, ".env").normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> parse(Path envFile) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = line.substring(0, separator).strip();
                String value = stripInlineComment(line.substring(separator + 1)).strip();
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException e) {
            log.warn(".env 파일을 읽는 중 오류가 발생해 무시한다: " + envFile, e);
        }
        return values;
    }

    private String stripInlineComment(String value) {
        int commentStart = value.indexOf(" #");
        return commentStart < 0 ? value : value.substring(0, commentStart);
    }
}
