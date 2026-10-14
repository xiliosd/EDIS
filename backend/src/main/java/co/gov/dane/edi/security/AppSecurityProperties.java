package co.gov.dane.edi.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record AppSecurityProperties(
    // HS256 exige una clave de al menos 256 bits.
    @NotBlank @Size(min = 32) String jwtSecret,
    @Min(5) @Max(240) int sessionMinutes,
    boolean cookieSecure,
    @NotBlank String allowedOrigin,
    @Min(1) int maxLoginAttempts,
    @Min(1) int lockoutMinutes,
    boolean swaggerPublic) {

  public Duration sessionDuration() {
    return Duration.ofMinutes(sessionMinutes);
  }

  @Override
  public String toString() {
    return "AppSecurityProperties[sessionMinutes=" + sessionMinutes + "]";
  }
}
