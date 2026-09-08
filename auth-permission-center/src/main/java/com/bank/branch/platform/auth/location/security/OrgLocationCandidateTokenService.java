package com.bank.branch.platform.auth.location.security;

import com.bank.branch.platform.auth.location.OrgLocationProperties;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocodeCandidate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 地址解析候选确认令牌服务。
 *
 * <p>令牌把操作员、机构、地址、城市、坐标、供应商和匹配精度全部纳入签名载荷，
 * 客户端无法通过改 JSON 字段伪造 VERIFIED 或精度。令牌内容不写日志。</p>
 */
@Component
public class OrgLocationCandidateTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final int MIN_SIGNING_SECRET_BYTES = 32;
    private static final long MAX_CANDIDATE_TTL_SECONDS = 900;
    private final OrgLocationProperties properties;
    private final Clock clock;

    @Autowired
    public OrgLocationCandidateTokenService(OrgLocationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public OrgLocationCandidateTokenService(OrgLocationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 当前是否有可用于签名的外部密钥。 */
    public boolean isAvailable() {
        if (properties.getSigningSecret() == null || properties.getSigningSecret().isBlank()) {
            return false;
        }
        long ttl = properties.getCandidateTtlSeconds();
        return properties.getSigningSecret().getBytes(StandardCharsets.UTF_8).length >= MIN_SIGNING_SECRET_BYTES
                && ttl > 0 && ttl <= MAX_CANDIDATE_TTL_SECONDS;
    }

    /** 为服务端已经过滤的候选签发短期令牌。 */
    public String issue(String operatorEmpId, String orgCode, String address, String cityCode,
                        OrgLocationGeocodeCandidate candidate) {
        if (!isAvailable()) {
            throw new IllegalStateException("位置候选签名密钥未配置");
        }
        long expiresAt = Instant.now(clock).getEpochSecond() + properties.getCandidateTtlSeconds();
        String payload = join(VERSION, operatorEmpId, orgCode, address, cityCode,
                candidate.provider(), candidate.formattedAddress(), candidate.cityCode(),
                decimal(candidate.lng()), decimal(candidate.lat()), candidate.coordSys(),
                candidate.matchLevel(), Long.toString(expiresAt));
        return encode(payload) + "." + encode(sign(payload));
    }

    /** 验证令牌签名、有效期和所有绑定字段，并返回令牌中的候选。 */
    public Optional<OrgLocationGeocodeCandidate> verify(String token, String operatorEmpId,
                                                        String orgCode, String address, String cityCode,
                                                        OrgLocationGeocodeCandidate expected) {
        if (!isAvailable() || token == null || token.isBlank() || expected == null) {
            return Optional.empty();
        }
        return verifyPayload(token, operatorEmpId, orgCode)
                .filter(payload -> same(payload.address(), address)
                        && same(payload.cityCode(), cityCode)
                        && sameCandidate(payload.candidate(), expected))
                .map(OrgLocationTokenPayload::candidate);
    }

    /** 只校验签名、操作者和机构，供确认接口从签名载荷恢复候选字段。 */
    public Optional<OrgLocationTokenPayload> verify(String token, String operatorEmpId,
                                                    String orgCode) {
        if (!isAvailable() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        return verifyPayload(token, operatorEmpId, orgCode);
    }

    private Optional<OrgLocationTokenPayload> verifyPayload(String token, String operatorEmpId,
                                                             String orgCode) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                return Optional.empty();
            }
            String payload = decode(parts[0]);
            byte[] actualSignature = decodeBytes(parts[1]);
            byte[] expectedSignature = sign(payload);
            if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
                return Optional.empty();
            }
            String[] values = payload.split("\\u001f", -1);
            if (values.length != 13 || !VERSION.equals(values[0])) {
                return Optional.empty();
            }
            long expiresAt = Long.parseLong(values[12]);
            if (Instant.now(clock).getEpochSecond() >= expiresAt) {
                return Optional.empty();
            }
            if (!same(values[1], operatorEmpId) || !same(values[2], orgCode)) {
                return Optional.empty();
            }
            BigDecimal lng = new BigDecimal(values[8]);
            BigDecimal lat = new BigDecimal(values[9]);
            OrgLocationGeocodeCandidate candidate = new OrgLocationGeocodeCandidate(
                    values[5], values[6], values[7], lng, lat, values[10], values[11]);
            return Optional.of(new OrgLocationTokenPayload(values[3], values[4], candidate, expiresAt));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean sameCandidate(OrgLocationGeocodeCandidate left,
                                         OrgLocationGeocodeCandidate right) {
        return same(left.provider(), right.provider())
                && same(left.formattedAddress(), right.formattedAddress())
                && same(left.cityCode(), right.cityCode())
                && same(decimal(left.lng()), decimal(right.lng()))
                && same(decimal(left.lat()), decimal(right.lat()))
                && same(left.coordSys(), right.coordSys())
                && same(left.matchLevel(), right.matchLevel());
    }

    /** 已签名的确认载荷。 */
    public record OrgLocationTokenPayload(String address, String cityCode,
                                          OrgLocationGeocodeCandidate candidate,
                                          long expiresAt) {
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("位置候选令牌签名失败", ex);
        }
    }

    private static String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append('\u001f');
            }
            result.append(normalize(values[i]));
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String decode(String value) {
        return new String(decodeBytes(value), StandardCharsets.UTF_8);
    }

    private static byte[] decodeBytes(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
