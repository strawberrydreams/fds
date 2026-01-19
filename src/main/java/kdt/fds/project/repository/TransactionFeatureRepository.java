package kdt.fds.project.repository;

import kdt.fds.project.entity.TransactionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionFeatureRepository extends JpaRepository<TransactionFeature, Long> {
}