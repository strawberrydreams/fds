package kdt.fds.common.repository;

import kdt.fds.common.entity.FdsConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FdsConfigRepository extends JpaRepository<FdsConfig, String> {
}