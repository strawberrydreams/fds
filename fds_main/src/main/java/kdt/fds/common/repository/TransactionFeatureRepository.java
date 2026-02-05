package kdt.fds.common.repository;

import kdt.fds.common.entity.TransactionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionFeatureRepository extends JpaRepository<TransactionFeature, Long> {
}