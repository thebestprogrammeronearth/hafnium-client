package dev.hafnium.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Collects named checks so one run reports every failure instead of stopping at the first. */
final class Checks {
    private static final Logger LOG = LoggerFactory.getLogger("HafniumTest");

    private final List<String> failures = new ArrayList<>();
    private int passed;

    void check(boolean ok, String name, Object detail) {
        if (ok) {
            passed++;
            LOG.info("PASS  {} ({})", name, detail);
        } else {
            failures.add(name + " (" + detail + ")");
            LOG.error("FAIL  {} ({})", name, detail);
        }
    }

    void finish() {
        LOG.info("RESULT {} passed, {} failed", passed, failures.size());
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " check(s) failed:\n  " + String.join("\n  ", failures));
        }
    }
}
