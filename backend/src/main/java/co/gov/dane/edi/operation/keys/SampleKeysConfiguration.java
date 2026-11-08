package co.gov.dane.edi.operation.keys;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Las claves de la muestra se generan con un generador criptográficamente seguro. */
@Configuration
public class SampleKeysConfiguration {

  @Bean
  public CredentialGenerator credentialGenerator() {
    return new CredentialGenerator(new SecureRandom());
  }
}
