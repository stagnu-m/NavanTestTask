package com.navan.travel.repository;

import com.navan.travel.model.entity.InvoiceLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, Long> {

    boolean existsByTransactionId(String transactionId);

    List<InvoiceLineEntity> findByInvoiceId(String invoiceId);
}
