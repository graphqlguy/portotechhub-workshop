INSERT IGNORE INTO vets VALUES (1, 'James', 'Carter');
INSERT IGNORE INTO vets VALUES (2, 'Helen', 'Leary');
INSERT IGNORE INTO vets VALUES (3, 'Linda', 'Douglas');
INSERT IGNORE INTO vets VALUES (4, 'Rafael', 'Ortega');
INSERT IGNORE INTO vets VALUES (5, 'Henry', 'Stevens');
INSERT IGNORE INTO vets VALUES (6, 'Sharon', 'Jenkins');

INSERT IGNORE INTO specialties VALUES (1, 'radiology');
INSERT IGNORE INTO specialties VALUES (2, 'surgery');
INSERT IGNORE INTO specialties VALUES (3, 'dentistry');

INSERT IGNORE INTO vet_specialties VALUES (2, 1);
INSERT IGNORE INTO vet_specialties VALUES (3, 2);
INSERT IGNORE INTO vet_specialties VALUES (3, 3);
INSERT IGNORE INTO vet_specialties VALUES (4, 2);
INSERT IGNORE INTO vet_specialties VALUES (5, 1);

INSERT IGNORE INTO types VALUES (1, 'cat');
INSERT IGNORE INTO types VALUES (2, 'dog');
INSERT IGNORE INTO types VALUES (3, 'lizard');
INSERT IGNORE INTO types VALUES (4, 'snake');
INSERT IGNORE INTO types VALUES (5, 'bird');
INSERT IGNORE INTO types VALUES (6, 'hamster');

INSERT IGNORE INTO owners VALUES (1, 'George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');
INSERT IGNORE INTO owners VALUES (2, 'Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749');
INSERT IGNORE INTO owners VALUES (3, 'Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763');
INSERT IGNORE INTO owners VALUES (4, 'Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198');
INSERT IGNORE INTO owners VALUES (5, 'Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765');
INSERT IGNORE INTO owners VALUES (6, 'Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654');
INSERT IGNORE INTO owners VALUES (7, 'Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387');
INSERT IGNORE INTO owners VALUES (8, 'Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683');
INSERT IGNORE INTO owners VALUES (9, 'David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435');
INSERT IGNORE INTO owners VALUES (10, 'Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487');
INSERT IGNORE INTO owners VALUES (11, 'Porto', 'Tech', 'Alfândega do Porto, Rua Nova da Alfândega', 'Porto', '2212345678');

INSERT IGNORE INTO pets VALUES (1, 'Leo', '2010-09-07', 1, 1);
INSERT IGNORE INTO pets VALUES (2, 'Basil', '2012-08-06', 6, 2);
INSERT IGNORE INTO pets VALUES (3, 'Rosy', '2011-04-17', 2, 3);
INSERT IGNORE INTO pets VALUES (4, 'Jewel', '2010-03-07', 2, 3);
INSERT IGNORE INTO pets VALUES (5, 'Iggy', '2010-11-30', 3, 4);
INSERT IGNORE INTO pets VALUES (6, 'George', '2010-01-20', 4, 5);
INSERT IGNORE INTO pets VALUES (7, 'Samantha', '2012-09-04', 1, 6);
INSERT IGNORE INTO pets VALUES (8, 'Max', '2012-09-04', 1, 6);
INSERT IGNORE INTO pets VALUES (9, 'Lucky', '2011-08-06', 5, 7);
INSERT IGNORE INTO pets VALUES (10, 'Mulligan', '2007-02-24', 2, 8);
INSERT IGNORE INTO pets VALUES (11, 'Freddy', '2010-03-09', 5, 9);
INSERT IGNORE INTO pets VALUES (12, 'Lucky', '2010-06-24', 2, 10);
INSERT IGNORE INTO pets VALUES (13, 'Sly', '2012-06-08', 1, 10);
INSERT IGNORE INTO pets VALUES (14, 'Ronaldo', '2026-01-01', 2, 11);
INSERT IGNORE INTO pets VALUES (15, 'Rosa', '2025-06-14', 1, 11);

INSERT IGNORE INTO visits VALUES (1, 7, 1, '2013-01-01', 'rabies shot');
INSERT IGNORE INTO visits VALUES (2, 8, 6, '2013-01-02', 'rabies shot');
INSERT IGNORE INTO visits VALUES (3, 8, 4, '2013-01-03', 'neutered');
INSERT IGNORE INTO visits VALUES (4, 7, 3, '2013-01-04', 'spayed');
INSERT IGNORE INTO visits VALUES (5, 15, 3, '2025-08-14', 'wound stitched after a fight');
INSERT IGNORE INTO visits VALUES (6, 14, 1, '2026-03-10', 'first vaccination');
INSERT IGNORE INTO visits VALUES (7, 15, 1, '2026-03-29', 'hairball');
INSERT IGNORE INTO visits VALUES (8, 14, 6, '2026-04-07', 'rabies shot');
INSERT IGNORE INTO visits VALUES (9, 15, 1, '2026-08-20', 'annual check-up');
INSERT IGNORE INTO visits VALUES (10, 14, 1, '2026-10-28', 'annual check-up');
