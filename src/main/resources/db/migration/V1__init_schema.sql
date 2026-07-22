-- Phase 1 Schema Migration: Identity, Tenancy, and RBAC

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -------------------------------------------------------------
-- 2. GLOBAL TABLES
-- These tables are not tenant-scoped. They contain data shared across
-- the entire system (like global user identities and permissions catalog).
-- -------------------------------------------------------------

-- COMPANIES: Represents the tenants in the system.
-- A company is the top-level isolation boundary (the tenant).
CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- USERS: Represents global user identities.
-- A user can register once and belong to multiple companies/workspaces.
-- Therefore, this table has no tenant_id.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- REFRESH_TOKENS: Manages user authentication sessions.
-- Scoped strictly to users, so it doesn't require tenant_id directly.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    replaced_by_token VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- PERMISSIONS: The global catalog of system capabilities.
-- Static strings (e.g., 'task:create') that are tenant-agnostic.
CREATE TABLE permissions (
    code VARCHAR(100) PRIMARY KEY,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------
-- 3. TENANT-SCOPED TABLES
-- These tables contain data specific to a tenant. Every table
-- must include a tenant_id column referencing companies(id).
-- -------------------------------------------------------------

-- ROLES: Groups of permissions.
-- Can be global (tenant_id IS NULL, e.g., System Admin, Member)
-- or custom tenant-defined roles (tenant_id IS NOT NULL).
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);

-- Ensure global roles are unique by name when tenant_id is NULL
CREATE UNIQUE INDEX uq_global_roles_name ON roles (name) WHERE tenant_id IS NULL;

-- ROLE_PERMISSIONS: Maps permissions to roles.
-- Since roles can be tenant-scoped, role_permissions is tenant-scoped as well.
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    tenant_id UUID REFERENCES companies(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_code)
);

-- WORKSPACES: Logical subdivisions within a tenant.
-- Belongs to a single company (tenant_id).
CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_workspaces_tenant_slug UNIQUE (tenant_id, slug)
);

-- WORKSPACE_MEMBERSHIPS: Bridges global users to a specific workspace and role.
-- Belongs to a tenant (tenant_id) to enforce isolation.
CREATE TABLE workspace_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_memberships_workspace_user UNIQUE (workspace_id, user_id)
);

-- OUTBOX_EVENTS: Stores messages for async processing (Transactional Outbox Pattern).
-- Kept tenant-scoped for security compliance.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

-- -------------------------------------------------------------
-- 4. COMPOSITE INDEXES
-- For every tenant-scoped table, we create a composite index starting
-- with tenant_id to optimize row-level security filters.
-- -------------------------------------------------------------

CREATE INDEX idx_workspaces_tenant_slug ON workspaces (tenant_id, slug);
CREATE INDEX idx_workspace_memberships_tenant_workspace ON workspace_memberships (tenant_id, workspace_id);
CREATE INDEX idx_workspace_memberships_tenant_user ON workspace_memberships (tenant_id, user_id);
CREATE INDEX idx_roles_tenant_name ON roles (tenant_id, name);
CREATE INDEX idx_role_permissions_tenant ON role_permissions (tenant_id);
CREATE INDEX idx_outbox_events_tenant_status ON outbox_events (tenant_id, status);

-- -------------------------------------------------------------
-- 5. ROW-LEVEL SECURITY (RLS) POLICIES
-- We enable RLS on all tenant-scoped tables and define policies that
-- check the 'app.current_tenant_id' session local variable.
-- -------------------------------------------------------------

-- Enable and force RLS on all tenant-scoped tables
ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspaces FORCE ROW LEVEL SECURITY;

ALTER TABLE workspace_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspace_memberships FORCE ROW LEVEL SECURITY;

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions FORCE ROW LEVEL SECURITY;

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;

-- Define Policies:
-- We read the current_setting('app.current_tenant_id', true) variable.
-- NULLIF(..., '') handles empty string settings.
-- For tables with nullable tenant_id (roles and role_permissions),
-- we also allow tenant_id IS NULL to support global system roles.

CREATE POLICY workspaces_tenant_isolation ON workspaces
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY workspace_memberships_tenant_isolation ON workspace_memberships
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY roles_tenant_isolation ON roles
    USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY role_permissions_tenant_isolation ON role_permissions
    USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY outbox_events_tenant_isolation ON outbox_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
