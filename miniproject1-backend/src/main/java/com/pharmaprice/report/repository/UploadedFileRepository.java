package com.pharmaprice.report.repository;

import com.pharmaprice.report.domain.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
}
