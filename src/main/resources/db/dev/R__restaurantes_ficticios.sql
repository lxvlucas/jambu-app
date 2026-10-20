INSERT INTO restaurantes(id, nome, endereco) VALUES
('10000000-0000-0000-0000-000000000001', 'Cozinha Jambu (fictício)', 'Rua de Teste, 1 — São Luís'),
('10000000-0000-0000-0000-000000000002', 'Mesa da Ilha (fictício)', 'Rua de Teste, 2 — São Luís')
ON CONFLICT (id) DO NOTHING;
