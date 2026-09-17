package com.pharmaprice.report.service;

/**
 * 업로드 파일의 실제 저장소 접근을 추상화한다.
 *
 * <p>지금은 {@link LocalFileStorageService}(로컬 디스크) 하나뿐이지만, S3 전환 시
 * 이 인터페이스는 그대로 두고 구현체만 교체한다({@code docs/ROADMAP.md} T-27).
 * {@code stored_path}는 절대경로가 아니라 이 인터페이스가 주고받는 상대 저장 키다.</p>
 */
public interface FileStorageService {

    /** {@code content}를 저장하고 상대 저장 키(예: {@code "2026/09/{uuid}.jpg"})를 반환한다. */
    String store(byte[] content, String extension);

    /** {@link #store}가 반환한 저장 키로 파일 내용을 읽는다. */
    byte[] load(String storagePath);
}
