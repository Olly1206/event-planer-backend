package event_planer.project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import event_planer.project.entity.TokenShortCode;

public interface TokenShortCodeRepository extends JpaRepository<TokenShortCode, Long> {
    Optional<TokenShortCode> findByInviteToken(String inviteToken);

    Optional<TokenShortCode> findByShortCode(String shortCode);
}
