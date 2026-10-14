-- Datos FICTICIOS. Nunca cargar aquí datos reales de servidores públicos (Ley 1581 de 2012).
DELETE FROM EDIN_MUESTRA;
DELETE FROM GRAL_USUARIOS_ADM;
DELETE FROM FRM_PREGUNTA;
DELETE FROM FRM_PRE_GRUPO;
DELETE FROM FRM_CAP_PROY;
DELETE FROM FRM_CAP_PERIODO;
DELETE FROM FRM_SALTO;
DELETE FROM FRM_OBJETO;
DELETE FROM FRM_OBJ_PRE;
DELETE FROM FRM_RESPUESTA;
DELETE FROM FRM_RTA_GRUPO;
DELETE FROM FRM_ENCABEZADO;

INSERT INTO EDIN_MUESTRA (ANIO, COD_ENT, COD_DEPTO, C_C, NOM_APE, ATV, TC1, LGN, PSW) VALUES
  ('2026', '899999', '11', '1000000001', 'Persona Ficticia Uno', 1, 1, 'PRUEBA01', 'Clave#01'),
  ('2026', '899999', '11', '1000000002', 'Persona Ficticia Dos', 0, 1, 'PRUEBA02', 'Clave#02'),
  ('2025', '899999', '11', '1000000003', 'Persona Ficticia Tres', 1, 1, 'PRUEBA03', 'Clave#03'),
  ('2026', '899999', '11', '1000000004', 'Persona Ficticia Cuatro', 1, 1, 'PRUEBA04', 'Clave#04');

INSERT INTO GRAL_USUARIOS_ADM (COD_USUA, CLAVE, NOMBRE, CARGO, ROL, COD_DPTO, PROY) VALUES
  ('adm.nacional', 'Nac#2026x', 'Administrador Ficticio', 'Coordinador', 1, NULL, 'EDIN'),
  ('adm.bogota', 'Bog#2026x', 'Territorial Ficticio', 'Asistente', 2, '11', 'EDIN'),
  ('adm.edid', 'Edid#2026', 'Otro Proyecto', 'Asistente', 1, NULL, 'EDID');

INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES
  (1, 'Nivel del empleo que desempe&ntilde;a en la entidad durante el 200X', 'P'),
  (2, 'Nivel', 'L'),
  (3, 'Edad en a&ntilde;os cumplidos', 'L'),
  (4, 'Pregunta nueva <b>sin</b> columna', 'L');
INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, COD_PRE, GRUPO, ORDEN) VALUES
  (1, 1, 1, 1), (2, 2, 1, 2), (3, 3, 1, 3), (4, 4, 1, 4);
INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, COD_PRE_GRUPO, CAPITULO) VALUES
  (101, 1, 'B1'), (102, 2, 'B1'), (103, 3, 'B1'), (104, 4, 'B1');
INSERT INTO FRM_CAP_PERIODO (COD_CAP_PROY, PROY, ANIO, NUMERAL) VALUES
  (101, 'EDIN', '2026', 'B2.'), (102, 'EDIN', '2026', 'B02*.'), (103, 'EDIN', '2026', 'B06*.'),
  (104, 'EDIN', '2026', 'B99*.'), (101, 'EDIN', '2025', 'B2-2025.');
INSERT INTO FRM_SALTO (COD_CAP_PROY, SALTO, VALOR_SALTO) VALUES (102, 2, 3);
INSERT INTO FRM_OBJETO (COD_OBJ, DESCRIPCION) VALUES (1, 'COMBO'), (2, 'CHECK'), (5, 'TEXTO');
INSERT INTO FRM_OBJ_PRE (COD_CAP_PROY, COD_OBJ, GRUPO) VALUES (102, 1, 10), (103, 5, 11), (104, 1, 10);
INSERT INTO FRM_RESPUESTA (COD_RTA, VALOR, RESPUESTA) VALUES
  (1, '1', 'Asistencial'), (2, '2', 'T&eacute;cnico'), (3, '3', 'Profesional');
-- ORDEN '10' verifica el orden numérico (TO_NUMBER), igual que el PHP heredado.
INSERT INTO FRM_RTA_GRUPO (GRUPO, COD_RTA, ORDEN) VALUES (10, 3, '10'), (10, 1, '1'), (10, 2, '2');
INSERT INTO FRM_ENCABEZADO (COD_ENC, COD_CAP_PROY, TIPO, ENCABEZADO, ANIO) VALUES
  (1, 101, 'E', 'Ubicaci&oacute;n laboral', '2026'),
  (2, 101, 'S', '<b>Datos del empleo</b>', '2026'),
  (3, 101, 'E', 'Encabezado 2025', '2025');
