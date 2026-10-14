package co.gov.dane.edi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EdiApplication {

  public static void main(String[] args) {
    SpringApplication.run(EdiApplication.class, args);
  }
}
