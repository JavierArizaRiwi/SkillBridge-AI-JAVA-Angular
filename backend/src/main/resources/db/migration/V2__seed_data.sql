INSERT INTO app_users (id, name, email, password, role, created_at)
VALUES ('a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
        'Admin',
        'admin@gmail.com',
        '$2a$12$xr69e.uTldinxnAtYKNHJul8GJ9VsO5epcQlx4gWNTrL04Fz90KKe', -- Hash BCrypt de: 12345678
        'ADMIN',
        NOW());

INSERT INTO app_users (id, name, email, password, role, created_at)
VALUES ('f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c',
        'Proveedor',
        'provider@gmail.com',
        '$2a$12$xr69e.uTldinxnAtYKNHJul8GJ9VsO5epcQlx4gWNTrL04Fz90KKe', -- Hash BCrypt de: 12345678
        'PROVIDER',
        NOW());

INSERT INTO app_users (id, name, email, password, role, created_at)
VALUES ('b7d9f3c1-a8e4-4c59-b1d6-8f2a3e9c4b7d',
        'User',
        'user@gmail.com',
        '$2a$12$xr69e.uTldinxnAtYKNHJul8GJ9VsO5epcQlx4gWNTrL04Fz90KKe', -- Hash BCrypt de: 12345678
        'CUSTOMER',
        NOW());
