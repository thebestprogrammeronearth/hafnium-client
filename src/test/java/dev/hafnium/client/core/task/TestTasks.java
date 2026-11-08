package dev.hafnium.client.core.task;

import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.world.WorldScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Scriptable task that records every callback. */
class ScriptedTask implements Task {
    final String name;
    final Set<TaskResource> resources;
    Set<Capability> capabilities = Set.of();
    WorldScope scope = WorldScope.NONE;
    /** Finishes successfully after this many ticks of an attempt (-1 = never). */
    int ticksToFinish;
    /** Throws on these attempts (1-based). */
    Predicate<Integer> failOnAttempt = a -> false;
    Recovery recovery = Recovery.RETRY;
    final List<String> log = new ArrayList<>();
    int ticked;

    ScriptedTask(String name, int ticksToFinish, TaskResource... resources) {
        this.name = name;
        this.ticksToFinish = ticksToFinish;
        this.resources = resources.length == 0 ? Set.of() : Set.of(resources);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<TaskResource> resources() {
        return resources;
    }

    @Override
    public Set<Capability> capabilities() {
        return capabilities;
    }

    @Override
    public WorldScope worldScope() {
        return scope;
    }

    @Override
    public void start(TaskContext ctx) {
        log.add("start" + ctx.attempt());
    }

    @Override
    public boolean tick(TaskContext ctx) {
        ticked++;
        if (failOnAttempt.test(ctx.attempt())) {
            throw new TaskException("boom on attempt " + ctx.attempt());
        }
        return ticksToFinish >= 0 && ctx.runningTicks() + 1 >= ticksToFinish;
    }

    @Override
    public void suspend(TaskContext ctx) {
        log.add("suspend");
    }

    @Override
    public void resume(TaskContext ctx) {
        log.add("resume");
    }

    @Override
    public void stop(TaskContext ctx, StopReason reason) {
        log.add("stop:" + reason);
    }

    @Override
    public Recovery recover(TaskContext ctx, Throwable error) {
        log.add("recover");
        return recovery;
    }
}
