-- 1. Create Invitations Table (Tenant-scoped to enforce database RLS)
CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Index for RLS scope and lookups
CREATE INDEX idx_invitations_tenant_token ON invitations (tenant_id, token_hash);

-- Enable RLS
ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations FORCE ROW LEVEL SECURITY;

CREATE POLICY invitations_tenant_isolation ON invitations
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 2. Seed Default Permissions
INSERT INTO permissions (code, description) VALUES
    ('workspace:read', 'Read workspace details'),
    ('workspace:update', 'Update workspace settings'),
    ('workspace:delete', 'Delete workspace'),
    ('member:invite', 'Invite members to workspace'),
    ('member:remove', 'Remove members from workspace'),
    ('task:read', 'Read tasks in workspace'),
    ('task:create', 'Create tasks in workspace'),
    ('task:update', 'Update tasks in workspace'),
    ('task:delete', 'Delete tasks in workspace')
ON CONFLICT DO NOTHING;

-- 3. Seed Default System Roles (tenant_id IS NULL)
INSERT INTO roles (id, tenant_id, name, description) VALUES
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', NULL, 'OWNER', 'Workspace Owner with full permissions'),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', NULL, 'MEMBER', 'Collaborative member with read/write permissions'),
    ('f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f', NULL, 'VIEWER', 'Read-only viewer')
ON CONFLICT DO NOTHING;

-- 4. Associate Permissions with default Roles
-- OWNER Permissions (All permissions)
INSERT INTO role_permissions (role_id, permission_code, tenant_id) VALUES
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'workspace:read', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'workspace:update', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'workspace:delete', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'member:invite', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'member:remove', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'task:read', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'task:create', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'task:update', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'task:delete', NULL)
ON CONFLICT DO NOTHING;

-- MEMBER Permissions (Read/Write, Invite, no workspace modification)
INSERT INTO role_permissions (role_id, permission_code, tenant_id) VALUES
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'workspace:read', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'member:invite', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'task:read', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'task:create', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'task:update', NULL)
ON CONFLICT DO NOTHING;

-- VIEWER Permissions (Read-only)
INSERT INTO role_permissions (role_id, permission_code, tenant_id) VALUES
    ('f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f', 'workspace:read', NULL),
    ('f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f', 'task:read', NULL)
ON CONFLICT DO NOTHING;
