package com.pharmaprice.report.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.upload-dir 설정을 바인딩한다.
 */
@ConfigurationProperties(prefix = "app")
public record UploadProperties(
    String uploadDir
) {
}
