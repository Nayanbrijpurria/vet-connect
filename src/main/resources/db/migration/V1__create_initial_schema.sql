
-- V1: Initial veterinary platform schema

-- 1. Users: pet owners, veterinarians and administrators
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL
        CHECK (role IN ('CUSTOMER', 'VET', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Veterinarian profiles and verification
CREATE TABLE vet_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    qualification TEXT NOT NULL,
    registration_number VARCHAR(100) NOT NULL UNIQUE,
    experience_years INTEGER NOT NULL DEFAULT 0
        CHECK (experience_years >= 0),
    consultation_fee NUMERIC(10, 2) NOT NULL
        CHECK (consultation_fee >= 0),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Pets belonging to customers
CREATE TABLE pets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    species VARCHAR(50) NOT NULL,
    breed VARCHAR(100),
    date_of_birth DATE,
    weight_kg NUMERIC(7, 2)
        CHECK (weight_kg IS NULL OR weight_kg > 0),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Veterinary consultations and appointments
CREATE TABLE appointments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    vet_profile_id BIGINT NOT NULL REFERENCES vet_profiles(id),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING',
            'CONFIRMED',
            'COMPLETED',
            'CANCELLED',
            'REJECTED'
        )),
    consultation_fee NUMERIC(10, 2) NOT NULL
        CHECK (consultation_fee >= 0),
    meeting_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (scheduled_end > scheduled_start)
);

-- Indexes for common lookups
CREATE INDEX idx_pets_owner_id
    ON pets(owner_id);

CREATE INDEX idx_appointments_customer_id
    ON appointments(customer_id);

CREATE INDEX idx_appointments_vet_start
    ON appointments(vet_profile_id, scheduled_start);

CREATE INDEX idx_appointments_status
    ON appointments(status);
