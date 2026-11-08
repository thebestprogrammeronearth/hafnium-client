package dev.hafnium.client.core.permission;

import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskScheduler;

/** Adapter that lets the scheduler consult the permission layer without depending on it. */
public final class PermissionGuard implements TaskScheduler.Guard {
    private final PermissionService permissions;

    public PermissionGuard(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public String deny(Task task) {
        if (task.capabilities().isEmpty()) {
            return null; // client-local work (planning, analysis, calculations) is never gated
        }
        Decision d = permissions.decideAll(task.capabilities(), task.autonomy());
        return d.allowed() ? null : d.reason();
    }
}
