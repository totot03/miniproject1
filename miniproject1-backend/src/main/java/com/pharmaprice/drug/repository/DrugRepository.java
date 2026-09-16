package com.pharmaprice.drug.repository;

import com.pharmaprice.drug.domain.Drug;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugRepository extends JpaRepository<Drug, Long> {

    /** T-07 시드 변환 스크립트의 멱등성 확인에 쓴다. */
    Optional<Drug> findByItemSeq(String itemSeq);
}
