package com.pharmaprice.report.service;

import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.dto.UploadedFileContent;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code docs/ROADMAP.md} T-27.
 */
public interface UploadService {

    UploadResponse upload(Long userId, MultipartFile file);

    UploadedFileContent getFileContent(Long fileId, Long requesterId, UserRole requesterRole);
}
