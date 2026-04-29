package event_planer.project.repository;

import event_planer.project.entity.TokenShortCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenShortCodeRepository extends JpaRepository<TokenShortCode, Long> {
    Optional<TokenShortCode> findByInviteToken(String inviteToken);

    Optional<TokenShortCode> findByShortCode(String shortCode);
}
