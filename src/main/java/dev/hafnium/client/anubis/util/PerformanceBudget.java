/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.util;

import dev.hafnium.client.modules.client.PerformanceModule;

public final class PerformanceBudget {
   private static final TickWorkBudget WORK = new TickWorkBudget(System::nanoTime);
   private static long lastTick;

   private PerformanceBudget() {
   }

   public static void beginTick() {
      lastTick = WORK.usedNanos();
      WORK.reset(PerformanceModule.scanBudgetNanos());
   }

   public static long acquire(long requestedNanos) {
      return PerformanceModule.optimizationsEnabled() ? WORK.acquire(requestedNanos) : System.nanoTime() + Math.max(0L, requestedNanos);
   }

   public static void record(long startedAt) {
      WORK.record(startedAt);
   }

   public static long lastTickNanos() {
      return lastTick;
   }
}
