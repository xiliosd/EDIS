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
DELETE FROM FRM_PRE_AYUDA;
DELETE FROM FRM_ENC_AYUDA;
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
-- CAPITULO guarda el código de página; PROY, ANIO y NUMERAL repiten los del periodo 2026.
INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, COD_PRE_GRUPO, CAPITULO, NUMERAL, PROY, ANIO) VALUES
  (101, 1, 'B1', 'B2.', 'EDIN', '2026'), (102, 2, 'B1', 'B02*.', 'EDIN', '2026'),
  (103, 3, 'B1', 'B06*.', 'EDIN', '2026'), (104, 4, 'B1', 'B99*.', 'EDIN', '2026');
INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, COD_CAP_PROY, PROY, ANIO, NUMERAL) VALUES
  (1, 101, 'EDIN', 2026, 'B2.'), (2, 102, 'EDIN', 2026, 'B02*.'), (3, 103, 'EDIN', 2026, 'B06*.'),
  (4, 104, 'EDIN', 2026, 'B99*.'), (5, 101, 'EDIN', 2025, 'B2-2025.');
INSERT INTO FRM_SALTO (COD_SAL, COD_CAP_PROY, SALTO, VALOR_SALTO) VALUES (1, 102, 2, 3);
INSERT INTO FRM_OBJETO (COD_OBJ, DESCRIPCION) VALUES (1, 'COMBO'), (2, 'CHECK'), (5, 'TEXTO');
INSERT INTO FRM_OBJ_PRE (COD_OBJ_PRE, COD_GRUPO, GRUPO, COD_OBJ, COD_CAP_PROY) VALUES
  (1, 10, 10, 1, 102), (2, 11, 11, 5, 103), (3, 10, 10, 1, 104);
INSERT INTO FRM_RESPUESTA (COD_RTA, VALOR, RESPUESTA) VALUES
  (1, '1', 'Asistencial'), (2, '2', 'T&eacute;cnico'), (3, '3', 'Profesional');
-- ORDEN '10' verifica el orden numérico (TO_NUMBER), igual que el PHP heredado.
INSERT INTO FRM_RTA_GRUPO (COD_GRUPO, GRUPO, COD_RTA, ORDEN, VALOR) VALUES
  (1, 10, 3, '10', '3'), (2, 10, 1, '1', '1'), (3, 10, 2, '2', '2');
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
INSERT INTO FRM_CAP_PROY (COD_CAP_PROY, COD_PRE_GRUPO, CAPITULO, NUMERAL, PROY, ANIO) VALUES
  (301, 201, 'C1', 'C01A.', 'EDIN', '2026'), (302, 202, 'C1', 'C01B.', 'EDIN', '2026'),
  (303, 203, 'C2', 'C13*.', 'EDIN', '2026'), (304, 204, 'D1', 'D01A.', 'EDIN', '2026'),
  (305, 205, 'D2', 'D09*.', 'EDIN', '2026'), (306, 206, 'E1', 'E01A.', 'EDIN', '2026'),
  (307, 207, 'F1', 'F01A.', 'EDIN', '2026'), (308, 208, 'G1', 'G01A.', 'EDIN', '2026'),
  (309, 209, 'H1', 'H01*.', 'EDIN', '2026'), (310, 210, 'D1', 'D05*.', 'EDIN', '2026');
INSERT INTO FRM_CAP_PERIODO (ID_PERIODO, COD_CAP_PROY, PROY, ANIO, NUMERAL) VALUES
  (11, 301, 'EDIN', 2026, 'C01A.'), (12, 302, 'EDIN', 2026, 'C01B.'),
  (13, 303, 'EDIN', 2026, 'C13*.'), (14, 304, 'EDIN', 2026, 'D01A.'),
  (15, 305, 'EDIN', 2026, 'D09*.'), (16, 306, 'EDIN', 2026, 'E01A.'),
  (17, 307, 'EDIN', 2026, 'F01A.'), (18, 308, 'EDIN', 2026, 'G01A.'),
  (19, 309, 'EDIN', 2026, 'H01*.'), (20, 310, 'EDIN', 2026, 'D05*.');
INSERT INTO FRM_OBJ_PRE (COD_OBJ_PRE, COD_GRUPO, GRUPO, COD_OBJ, COD_CAP_PROY) VALUES
  (11, 20, 20, 1, 301), (12, 20, 20, 1, 302), (13, 20, 20, 1, 303), (14, 20, 20, 1, 304),
  (15, 20, 20, 1, 305), (16, 20, 20, 1, 306), (17, 20, 20, 1, 307), (18, 20, 20, 1, 308),
  (19, 20, 20, 1, 309), (20, 11, 11, 5, 310);
-- Salto genérico heredado: si C01A = 2 (No), se deshabilita el combo siguiente (C01B).
INSERT INTO FRM_SALTO (COD_SAL, COD_CAP_PROY, SALTO, VALOR_SALTO) VALUES (2, 301, 1, 2);
INSERT INTO FRM_RESPUESTA (COD_RTA, VALOR, RESPUESTA) VALUES (21, '1', 'S&iacute;'), (22, '2', 'No');
INSERT INTO FRM_RTA_GRUPO (COD_GRUPO, GRUPO, COD_RTA, ORDEN, VALOR) VALUES
  (11, 20, 21, '1', '1'), (12, 20, 22, '2', '2');

-- Ayudas ficticias: una por página (B1, C1, C2, D1, D2, E1, F1, G1, H1) y una de encabezado.
INSERT INTO FRM_PRE_AYUDA (COD_AYU, COD_CAP_PROY, TEXTO) VALUES
  (1, 101, 'Ayuda ficticia de la página B1'), (2, 301, 'Ayuda ficticia de la página C1'),
  (3, 303, 'Ayuda ficticia de la página C2'), (4, 304, 'Ayuda ficticia de la página D1'),
  (5, 305, 'Ayuda ficticia de la página D2'), (6, 306, 'Ayuda ficticia de la página E1'),
  (7, 307, 'Ayuda ficticia de la página F1'), (8, 308, 'Ayuda ficticia de la página G1'),
  (9, 309, 'Ayuda ficticia de la página H1');
INSERT INTO FRM_ENC_AYUDA (COD_AYU, COD_ENC, TEXTO) VALUES
  (1, 1, 'Ayuda ficticia del encabezado de B1');
