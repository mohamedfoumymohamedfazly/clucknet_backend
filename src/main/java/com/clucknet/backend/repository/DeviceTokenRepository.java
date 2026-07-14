package com.clucknet.backend.repository;

import com.clucknet.backend.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    List<DeviceToken> findByUserId(Long userId);
    
    @Transactional
    void deleteByToken(String token);
    
    @Transactional
    void deleteByUserId(Long userId);
}
