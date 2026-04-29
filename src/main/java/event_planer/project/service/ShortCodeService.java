package event_planer.project.service;

import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import event_planer.project.entity.TokenShortCode;
import event_planer.project.repository.TokenShortCodeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing short codes that map to invite tokens.
 * Avoids sharing long UUIDs which can trigger WAF/CDN issues.
 */
@Service
@RequiredArgsConstructor
public class ShortCodeService {

    private final TokenShortCodeRepository tokenShortCodeRepository;
    private static final Logger logger = LoggerFactory.getLogger(ShortCodeService.class);
    private static final Random random = new Random();
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Gets or creates a short code for the given invite token.
     * If a mapping already exists, it's returned; otherwise a new one is created.
     */
    @Transactional
    public String getOrCreateShortCode(String inviteToken) {
        return tokenShortCodeRepository.findByInviteToken(inviteToken)
                .map(TokenShortCode::getShortCode)
                .orElseGet(() -> createShortCode(inviteToken));
    }

    /**
     * Creates a new short code for the given token.
     * Retries up to 5 times if collision occurs.
     */
    private String createShortCode(String inviteToken) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generateRandomCode(8);
            if (!tokenShortCodeRepository.findByShortCode(code).isPresent()) {
                TokenShortCode mapping = TokenShortCode.builder()
                        .inviteToken(inviteToken)
                        .shortCode(code)
                        .build();
                tokenShortCodeRepository.save(mapping);
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique short code after 5 attempts");
    }

    /**
     * Resolves a short code to its invite token.
     * Updates lastAccessedAt for optional analytics/cleanup.
     */
    @Transactional
    public String resolveShortCode(String shortCode) {
        TokenShortCode mapping = tokenShortCodeRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new event_planer.project.exception.ResourceNotFoundException(
                        "Short code not found: " + shortCode));
        logger.info("Short code lookup: {} -> token={}", shortCode, mapping.getInviteToken());
        mapping.setLastAccessedAt(LocalDateTime.now());
        tokenShortCodeRepository.save(mapping);
        return mapping.getInviteToken();
    }

    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
