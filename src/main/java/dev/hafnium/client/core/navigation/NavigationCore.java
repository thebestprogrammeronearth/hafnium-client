package dev.hafnium.client.core.navigation;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.task.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Planning service owned by HafniumCore. Execution must be a separately permission-guarded task. */
public final class NavigationCore {
    private final ClientThread thread;
    private final TaskScheduler scheduler;
    private final Map<Key,PathPlanner.Route> cache=new LinkedHashMap<>();
    private long revision;
    private record Key(CollisionSnapshot snapshot,PathPlanner.Point start,PathPlanner.Point goal,PathPlanner.Costs costs) {}
    public NavigationCore(ClientThread thread,TaskScheduler scheduler) { this.thread=thread; this.scheduler=scheduler; }
    private void checkThread() { if(!thread.isCurrent()) throw new IllegalStateException("Navigation requires client thread"); }
    public long revision() { checkThread(); return revision; }
    /** Conservative invalidation also covers untracked ordinary blocks and neighbor-dependent collision shapes. */
    public void invalidate() { checkThread(); revision++; cache.clear(); scheduler.cancelOwnedBy(this,"Collision geometry changed; recapture required"); }
    public TaskHandle plan(CollisionSnapshot snapshot,PathPlanner.Point start,PathPlanner.Point goal,
                           PathPlanner.Costs costs,Consumer<PathPlanner.Route> result) {
        checkThread(); Objects.requireNonNull(result);
        if(snapshot.revision()!=revision) throw new IllegalArgumentException("Stale collision snapshot");
        Key key=new Key(snapshot,start,goal,costs);
        return scheduler.submit(new Task() {
            final AtomicBoolean cancelled=new AtomicBoolean();
            AsyncResult<PathPlanner.Route> pending;
            PathPlanner.Route route;
            public String name() { return "Navigation planning"; }
            public void start(TaskContext ctx) {
                route=cache.get(key);
                if(route==null) pending=ctx.async(snapshot,s->new PathPlanner().plan(s,start,goal,costs,cancelled::get));
            }
            public boolean tick(TaskContext ctx) {
                if(snapshot.revision()!=revision) throw new TaskException("Stale collision snapshot");
                if(route==null) { if(!pending.isDone()) return false; route=pending.get(); }
                if(cache.size()>=16) cache.remove(cache.keySet().iterator().next());
                cache.put(key,route); result.accept(route); return true;
            }
            public void stop(TaskContext ctx,StopReason reason) { cancelled.set(true); }
        },TaskOptions.builder().owner(this).timeoutTicks(200).build());
    }
}
