package co.gov.dane.edi.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

  @Bean
  SecretKey sessionSigningKey(AppSecurityProperties properties) {
    return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey sessionSigningKey) {
    return NimbusJwtEncoder.withSecretKey(sessionSigningKey).build();
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey sessionSigningKey) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(sessionSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(SessionTokenService.ISSUER));
    return decoder;
  }
}
