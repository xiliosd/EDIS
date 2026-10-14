package co.gov.dane.edi.service.error;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String detail) {
    super(detail);
  }
}
