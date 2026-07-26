-- 1. Create teams table
CREATE TABLE teams (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_teams_workspace_slug UNIQUE (workspace_id, slug)
);

-- 2. Create team_memberships table
CREATE TABLE team_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_memberships_team_user UNIQUE (team_id, user_id)
);

-- 3. Create projects table
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_workspace_slug UNIQUE (workspace_id, slug)
);

-- 4. Create tasks table
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'BACKLOG',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 5. Enable Row-Level Security (RLS) on all new tables
ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE teams FORCE ROW LEVEL SECURITY;

ALTER TABLE team_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_memberships FORCE ROW LEVEL SECURITY;

ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;

ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks FORCE ROW LEVEL SECURITY;

-- 6. Define RLS Policies matching tenant context session variable
CREATE POLICY teams_tenant_isolation ON teams
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY team_memberships_tenant_isolation ON team_memberships
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY projects_tenant_isolation ON projects
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tasks_tenant_isolation ON tasks
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- 7. Define Composite Indexes starting with tenant_id for RLS query optimization
CREATE INDEX idx_teams_tenant_workspace ON teams (tenant_id, workspace_id);
CREATE INDEX idx_team_memberships_tenant_team ON team_memberships (tenant_id, team_id);
CREATE INDEX idx_projects_tenant_workspace ON projects (tenant_id, workspace_id);
CREATE INDEX idx_tasks_tenant_workspace ON tasks (tenant_id, workspace_id);

-- 8. Seed Default System Permissions for Teams and Projects
INSERT INTO permissions (code, description) VALUES
    ('team:read', 'Read team details'),
    ('team:create', 'Create team'),
    ('team:update', 'Update team'),
    ('team:delete', 'Delete team'),
    ('project:read', 'Read project details'),
    ('project:create', 'Create project'),
    ('project:update', 'Update project'),
    ('project:delete', 'Delete project')
ON CONFLICT DO NOTHING;

-- 9. Associate Permissions with Default Global Roles
-- OWNER Role (All new permissions)
INSERT INTO role_permissions (role_id, permission_code, tenant_id) VALUES
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'team:read', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'team:create', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'team:update', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'team:delete', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'project:read', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'project:create', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'project:update', NULL),
    ('d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d', 'project:delete', NULL)
ON CONFLICT DO NOTHING;

-- MEMBER Role (Read team/project, create/update project)
INSERT INTO role_permissions (role_id, permission_code, tenant_id) VALUES
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'team:read', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'project:read', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'project:create', NULL),
    ('e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e', 'project:update', NULL)
ON CONFLICT DO NOTHING;

-- VIEWER Role (Read-only team/project)
INSERT INTO role_permissions (role_id, permission_code, tenant_id) VALUES
    ('f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f', 'team:read', NULL),
    ('f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f', 'project:read', NULL)
ON CONFLICT DO NOTHING;
