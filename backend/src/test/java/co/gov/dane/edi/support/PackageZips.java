package co.gov.dane.edi.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Lee y arma ZIP en memoria para las pruebas de paquetes de promoción. */
public final class PackageZips {

  private PackageZips() {}

  /** Entradas en el orden en que aparecen en el ZIP. */
  public static Map<String, byte[]> read(byte[] zip) {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        entries.put(entry.getName(), input.readAllBytes());
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return entries;
  }

  /** Un ZIP con estas entradas, en este orden. */
  public static byte[] write(Map<String, byte[]> entries) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream output = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        output.putNextEntry(new ZipEntry(entry.getKey()));
        output.write(entry.getValue());
        output.closeEntry();
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return bytes.toByteArray();
  }

  /**
   * Un ZIP con dos entradas del mismo nombre: ZipOutputStream no lo permite, así que se escribe con
   * un nombre del mismo largo y luego se reemplaza en los bytes (el CRC no incluye el nombre).
   */
  public static byte[] withDuplicate(Map<String, byte[]> entries, String name) {
    String placeholder = "#".repeat(name.length());
    Map<String, byte[]> withPlaceholder = new LinkedHashMap<>(entries);
    withPlaceholder.put(placeholder, entries.get(name));
    byte[] zip = write(withPlaceholder);
    byte[] from = placeholder.getBytes(StandardCharsets.US_ASCII);
    byte[] to = name.getBytes(StandardCharsets.US_ASCII);
    for (int index = 0; index + from.length <= zip.length; index++) {
      boolean matches = true;
      for (int offset = 0; offset < from.length && matches; offset++) {
        matches = zip[index + offset] == from[offset];
      }
      if (matches) {
        System.arraycopy(to, 0, zip, index, to.length);
      }
    }
    return zip;
  }

  public static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
