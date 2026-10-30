package com.navan.travel.repository;

import com.navan.travel.model.entity.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    Optional<TransactionEntity> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    Page<TransactionEntity> findAll(Pageable pageable);
}
