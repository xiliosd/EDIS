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
DELETE FROM EDIN_20_CAP_B_UBICA;
DELETE FROM EDIN_20_CAP_C_EXP_PERS;
DELETE FROM EDIN_20_CAP_D_GEST_TAL;
DELETE FROM EDIN_20_CAP_E_USO_TECN;
DELETE FROM EDIN_20_CAP_F_GEST_ENT;
DELETE FROM EDIN_20_CAP_G_REN_CUEN;
DELETE FROM EDIN_20_CAP_H_PREV_PRACT;

INSERT INTO EDIN_MUESTRA (ANIO, COD_ENT, COD_DEPTO, C_C, NOM_APE, ATV, TC1, LGN, PSW) VALUES
  ('2026', '899999', '11', '1000000001', 'Persona Ficticia Uno', 1, 1, 'PRUEBA01', 'Clave#01'),
  ('2026', '899999', '11', '1000000002', 'Persona Ficticia Dos', 0, 1, 'PRUEBA02', 'Clave#02'),
  ('2025', '899999', '11', '1000000003', 'Persona Ficticia Tres', 1, 1, 'PRUEBA03', 'Clave#03'),
  ('2026', '899999', '11', '1000000004', 'Persona Ficticia Cuatro', 1, 1, 'PRUEBA04', 'Clave#04'),
  ('2026', '899999', '11', '1000000005', 'Persona Ficticia Cinco', 1, 1, 'PRUEBA05', 'Clave#05'),
  ('2026', '899999', '11', '1000000006', 'Persona Ficticia Seis', 1, 1, 'PRUEBA06', 'Clave#06');

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

-- Cuestionario FICTICIO de las páginas C1–H1: un combo Sí/No por página (dos en C1, con salto genérico).
INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES
  (201, '&iquest;Pregunta ficticia C01A?', 'L'), (202, 'Pregunta ficticia C01B', 'L'),
  (203, 'Pregunta ficticia C13', 'L'), (204, 'Pregunta ficticia D01A', 'L'),
  (205, 'Pregunta ficticia D09', 'L'), (206, 'Pregunta ficticia E01A', 'L'),
  (207, 'Pregunta ficticia F01A', 'L'), (208, 'Pregunta ficticia G01A', 'L'),
  (209, 'Pregunta ficticia H01', 'L'), (210, 'Antig&uuml;edad ficticia D05 (a&ntilde;os)', 'L');
INSERT INTO FRM_PRE_GRUPO (COD_PRE_GRUPO, COD_PRE, GRUPO, ORDEN) VALUES
  (201, 201, 1, 1), (202, 202, 1, 2), (203, 203, 1, 1), (204, 204, 1, 1), (205, 205, 1, 1),
  (206, 206, 1, 1), (207, 207, 1, 1), (208, 208, 1, 1), (209, 209, 1, 1), (210, 210, 1, 2);
INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, COD_PRE_GRUPO, CAPITULO) VALUES
  (301, 201, 'C1'), (302, 202, 'C1'), (303, 203, 'C2'), (304, 204, 'D1'), (305, 205, 'D2'),
  (306, 206, 'E1'), (307, 207, 'F1'), (308, 208, 'G1'), (309, 209, 'H1'), (310, 210, 'D1');
INSERT INTO FRM_CAP_PERIODO (COD_CAP_PROY, PROY, ANIO, NUMERAL) VALUES
  (301, 'EDIN', '2026', 'C01A.'), (302, 'EDIN', '2026', 'C01B.'), (303, 'EDIN', '2026', 'C13*.'),
  (304, 'EDIN', '2026', 'D01A.'), (305, 'EDIN', '2026', 'D09*.'), (306, 'EDIN', '2026', 'E01A.'),
  (307, 'EDIN', '2026', 'F01A.'), (308, 'EDIN', '2026', 'G01A.'), (309, 'EDIN', '2026', 'H01*.'),
  (310, 'EDIN', '2026', 'D05*.');
INSERT INTO FRM_OBJ_PRE (COD_CAP_PROY, COD_OBJ, GRUPO) VALUES
  (301, 1, 20), (302, 1, 20), (303, 1, 20), (304, 1, 20), (305, 1, 20), (306, 1, 20), (307, 1, 20),
  (308, 1, 20), (309, 1, 20), (310, 5, 11);
-- Salto genérico heredado: si C01A = 2 (No), se deshabilita el combo siguiente (C01B).
INSERT INTO FRM_SALTO (COD_CAP_PROY, SALTO, VALOR_SALTO) VALUES (301, 1, 2);
INSERT INTO FRM_RESPUESTA (COD_RTA, VALOR, RESPUESTA) VALUES (21, '1', 'S&iacute;'), (22, '2', 'No');
INSERT INTO FRM_RTA_GRUPO (GRUPO, COD_RTA, ORDEN) VALUES (20, 21, '1'), (20, 22, '2');
