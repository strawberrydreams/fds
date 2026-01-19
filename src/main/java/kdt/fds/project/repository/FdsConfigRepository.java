package kdt.fds.project.repository;

import kdt.fds.project.entity.FdsConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FdsConfigRepository extends JpaRepository<FdsConfig, String> {
}