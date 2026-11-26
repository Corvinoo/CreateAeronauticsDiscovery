package me.corvino.aeronauticsdiscovery.glue;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GlueHierarchy {

    public static final int ROOT = -1;

    private final List<BoundingBox> boxes;
    private final boolean[] rejected;
    private final int[] parents;
    private final int[] depths;

    private GlueHierarchy(List<BoundingBox> boxes) {
        this.boxes = List.copyOf(boxes);
        int n = this.boxes.size();
        this.rejected = new boolean[n];
        this.parents = new int[n];
        this.depths = new int[n];
        computeRejections();
        computeParents();
        computeDepths();
    }

    public static GlueHierarchy of(List<BoundingBox> boxes) {
        return new GlueHierarchy(boxes);
    }

    public static boolean strictlyContains(BoundingBox outer, BoundingBox inner) {
        boolean encloses = outer.minX() <= inner.minX() && outer.minY() <= inner.minY() && outer.minZ() <= inner.minZ()
                && outer.maxX() >= inner.maxX() && outer.maxY() >= inner.maxY() && outer.maxZ() >= inner.maxZ();
        return encloses && !outer.equals(inner);
    }

    public static boolean conflicts(BoundingBox a, BoundingBox b) {
        return a.intersects(b) && !strictlyContains(a, b) && !strictlyContains(b, a);
    }

    public static long volume(BoundingBox box) {
        return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
    }

    private void computeRejections() {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                if (conflicts(boxes.get(i), boxes.get(j))) {
                    rejected[i] = true;
                    rejected[j] = true;
                }
            }
        }
    }

    private void computeParents() {
        for (int i = 0; i < boxes.size(); i++) {
            parents[i] = ROOT;
            if (rejected[i]) continue;
            long best = Long.MAX_VALUE;
            for (int j = 0; j < boxes.size(); j++) {
                if (i == j || rejected[j]) continue;
                if (!strictlyContains(boxes.get(j), boxes.get(i))) continue;
                long v = volume(boxes.get(j));
                if (v < best) {
                    best = v;
                    parents[i] = j;
                }
            }
        }
    }

    private void computeDepths() {
        for (int i = 0; i < boxes.size(); i++) {
            int depth = 0;
            int cursor = parents[i];
            while (cursor != ROOT) {
                depth++;
                cursor = parents[cursor];
            }
            depths[i] = depth;
        }
    }

    public int size() {
        return boxes.size();
    }

    public BoundingBox box(int index) {
        return boxes.get(index);
    }

    public boolean isRejected(int index) {
        return rejected[index];
    }

    public int parent(int index) {
        return parents[index];
    }

    public int depth(int index) {
        return depths[index];
    }

    public List<Integer> deepestFirst() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (!rejected[i]) order.add(i);
        }
        order.sort(Comparator.<Integer>comparingInt(i -> depths[i]).reversed().thenComparingInt(i -> i));
        return order;
    }
}
