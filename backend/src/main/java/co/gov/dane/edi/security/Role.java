package co.gov.dane.edi.security;

import java.util.EnumSet;
import java.util.Set;

public enum Role {
  ENCUESTADO,
  ADMIN_NACIONAL,
  ADMIN_TERRITORIAL,
  ADMIN_OPERATIVO,
  ADMIN_CUESTIONARIO;

  public static final Set<Role> ADMINISTRATORS =
      EnumSet.of(ADMIN_NACIONAL, ADMIN_TERRITORIAL, ADMIN_OPERATIVO, ADMIN_CUESTIONARIO);

  public String authority() {
    return "ROLE_" + name();
  }
}
