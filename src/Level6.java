import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

public class Level6 {

    record Point(int r, int c) {}
    record Target(int id, Point cell, int posA, int posB) {}

    static final int[][] FIRE_OV = {
            {0, 2},   // 0: E
            {1, 1},   // 1: SE
            {1, -1},  // 2: SW
            {0, -2},  // 3: W
            {-1, -1}, // 4: NW
            {-1, 1}   // 5: NE
    };

    static final int[][] HEX_DIRS = {{0, 2}, {1, 1}, {1, -1}, {0, -2}, {-1, -1}, {-1, 1}};

    static List<Point> buildPerimeter(int R) {
        List<Point> perim = new ArrayList<>(6 * R);
        int curC = 0, curR = R;
        int[][] perimDirs = {{-1, 1}, {0, 2}, {1, 1}, {1, -1}, {0, -2}, {-1, -1}};
        for (int[] d : perimDirs) {
            for (int i = 0; i < R; i++) {
                perim.add(new Point(curR, curC));
                curR += d[0];
                curC += d[1];
            }
        }
        return perim;
    }

    static int dist(int from, int to, int P) {
        int d = Math.abs(to - from);
        return Math.min(d, P - d);
    }

    static char getMoveDirChar(int from, int to, int P) {
        if (from == to) return '-';
        int cw = (to - from + P) % P;
        int ccw = (from - to + P) % P;
        return cw <= ccw ? 'F' : 'B';
    }

    static int stepPos(int pos, char cmd, int P) {
        if (cmd == 'F') return (pos + 1) % P;
        if (cmd == 'B') return (pos - 1 + P) % P;
        return pos;
    }

    static String formatSixLine(char[] cmds) {
        return cmds[0] + " " + cmds[1] + " " + cmds[2] + " " + cmds[3] + " " + cmds[4] + " " + cmds[5];
    }

    static int[][][] precomputePerimLookups(int R, List<Point> perim) {
        int numRows = 2 * R + 1;
        int numCols = 4 * R + 1;
        int P = perim.size();
        int[][][] lookup = new int[6][numRows][numCols];
        for (int t = 0; t < 6; t++) {
            for (int r = 0; r < numRows; r++) {
                Arrays.fill(lookup[t][r], -1);
            }
            int dr = FIRE_OV[t][0];
            int dc = FIRE_OV[t][1];
            for (int i = 0; i < P; i++) {
                Point p = perim.get(i);
                int er = p.r + dr;
                int ec = p.c + dc;
                while (er >= 0 && er < numRows && ec >= 0 && ec < numCols) {
                    lookup[t][er][ec] = i;
                    er += dr;
                    ec += dc;
                }
            }
        }
        return lookup;
    }

    /**
     * Precomputed board geometry and fast intersection lookup tables.
     */
    static class BoardMeta {
        final int R, numRows, numCols, P, numM, numSkirt;
        final List<Point> perim;
        final int[][][] lookups;
        final char[][] baseGrid;
        final char[][] patternGrid;
        final List<Point> mCells;
        final List<Point> skirtCells;
        final boolean[][] isM;
        final boolean[][] isForbidden;
        final int[][] mIndex;
        final int[][] skirtIndex;

        // For each (t, pos), list of M-cell indices and Skirt-cell indices hit by that ray
        final int[][][] rayMCells;
        final int[][][] raySkirtCells;
        // Pairwise intersection: intersectCell[u][posU][v][posV] -> packed (r << 16 | c), or -1
        final int[][][][] intersectCell;

        BoardMeta(int R, char[][] patternGrid, List<Point> mCells) {
            this.R = R;
            this.numRows = 2 * R + 1;
            this.numCols = 4 * R + 1;
            this.perim = buildPerimeter(R);
            this.P = perim.size();
            this.lookups = precomputePerimLookups(R, perim);
            this.patternGrid = patternGrid;
            this.mCells = mCells;
            this.numM = mCells.size();

            this.baseGrid = new char[numRows][numCols];
            for (int r = 0; r < numRows; r++) {
                int d = Math.abs(r - R);
                Arrays.fill(baseGrid[r], '-');
                int left = d, right = (4 * R) - d;
                for (int c = left; c <= right; c += 2) {
                    baseGrid[r][c] = (c == left || c == right || r == 0 || r == numRows - 1) ? '#' : 'O';
                }
            }

            this.isM = new boolean[numRows][numCols];
            this.isForbidden = new boolean[numRows][numCols];
            this.mIndex = new int[numRows][numCols];
            this.skirtIndex = new int[numRows][numCols];
            for (int r = 0; r < numRows; r++) {
                Arrays.fill(mIndex[r], -1);
                Arrays.fill(skirtIndex[r], -1);
            }

            for (int i = 0; i < numM; i++) {
                Point p = mCells.get(i);
                isM[p.r][p.c] = true;
                mIndex[p.r][p.c] = i;
            }

            this.skirtCells = new ArrayList<>();
            for (int r = 0; r < numRows; r++) {
                for (int c = 0; c < numCols; c++) {
                    if (patternGrid[r][c] == 'O') {
                        for (int[] d : HEX_DIRS) {
                            int nr = r + d[0], nc = c + d[1];
                            if (nr >= 0 && nr < numRows && nc >= 0 && nc < numCols && patternGrid[nr][nc] == 'M') {
                                isForbidden[r][c] = true;
                                skirtIndex[r][c] = skirtCells.size();
                                skirtCells.add(new Point(r, c));
                                break;
                            }
                        }
                    }
                }
            }
            this.numSkirt = skirtCells.size();

            this.rayMCells = new int[6][P][];
            this.raySkirtCells = new int[6][P][];
            for (int t = 0; t < 6; t++) {
                int dr = FIRE_OV[t][0], dc = FIRE_OV[t][1];
                for (int pos = 0; pos < P; pos++) {
                    List<Integer> mList = new ArrayList<>();
                    List<Integer> sList = new ArrayList<>();
                    Point start = perim.get(pos);
                    int r = start.r + dr, c = start.c + dc;
                    while (r >= 0 && r < numRows && c >= 0 && c < numCols) {
                        if (baseGrid[r][c] == '#') break;
                        if (baseGrid[r][c] == 'O') {
                            if (mIndex[r][c] != -1) mList.add(mIndex[r][c]);
                            if (skirtIndex[r][c] != -1) sList.add(skirtIndex[r][c]);
                        }
                        r += dr;
                        c += dc;
                    }
                    rayMCells[t][pos] = mList.stream().mapToInt(Integer::intValue).toArray();
                    raySkirtCells[t][pos] = sList.stream().mapToInt(Integer::intValue).toArray();
                }
            }

            this.intersectCell = new int[6][P][6][P];
            for (int u = 0; u < 6; u++) {
                for (int pu = 0; pu < P; pu++) {
                    for (int v = 0; v < 6; v++) {
                        Arrays.fill(intersectCell[u][pu][v], -1);
                    }
                }
            }
            for (int r = 0; r < numRows; r++) {
                for (int c = 0; c < numCols; c++) {
                    if (baseGrid[r][c] != 'O') continue;
                    int code = (r << 16) | c;
                    for (int u = 0; u < 6; u++) {
                        int pu = lookups[u][r][c];
                        if (pu == -1) continue;
                        for (int v = u + 1; v < 6; v++) {
                            int pv = lookups[v][r][c];
                            if (pv == -1) continue;
                            intersectCell[u][pu][v][pv] = code;
                            intersectCell[v][pv][u][pu] = code;
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // STRATEGY 0: 1-TICK SINGLE-SHOT SOLVER (COMPLETE PATTERN IN 1 TICK + O(1) PER CYCLE!)
    // =========================================================================

    /**
     * Attempts to find a single configuration of up to 6 emitters (pos[0..5], activeMask)
     * that creates EVERY 'M' cell simultaneously on a single tick without hitting any skirting 'O' cell
     * with exactly 2 rays (3+ rays on a skirting 'O' cell is 'd', which is 100% legal!).
     * If found, cycles 2..requiredCount take JUST 1 TICK EACH ("- - - - - -")!
     */
    static List<String> trySingleTickSnapshot(BoardMeta meta, int requiredCount) {
        if (meta.numM > 15) return null; // 6 emitters can intersect at max C(6,2) = 15 points in 1 tick

        // Collect candidate positions for each emitter t that pass through at least one M cell
        int[][] candidates = new int[6][];
        for (int t = 0; t < 6; t++) {
            Set<Integer> set = new LinkedHashSet<>();
            for (Point p : meta.mCells) {
                int pos = meta.lookups[t][p.r][p.c];
                if (pos != -1) set.add(pos);
            }
            set.add(-1); // -1 means emitter t is OFF
            candidates[t] = set.stream().mapToInt(Integer::intValue).toArray();
        }

        int[] bestPos = null;
        int bestCost = Integer.MAX_VALUE;
        int[] curPos = new int[6];

        bestPos = searchSingleShotDFS(meta, candidates, 0, curPos, bestPos, new int[]{bestCost});
        if (bestPos == null) return null;

        // Build the shortest walk from initial positions (t * R) to bestPos, turn ON on the exact arrival tick,
        // and then emit "- - - - - -" for (requiredCount - 1) ticks!
        int P = meta.P;
        int maxTicksToReady = 0;
        int[] moveSteps = new int[6];
        char[] moveDirs = new char[6];

        for (int t = 0; t < 6; t++) {
            if (bestPos[t] == -1) continue;
            int start = t * meta.R;
            moveSteps[t] = dist(start, bestPos[t], P);
            moveDirs[t] = getMoveDirChar(start, bestPos[t], P);
            // Needs moveSteps[t] + 1 tick for 'T'
            maxTicksToReady = Math.max(maxTicksToReady, moveSteps[t] + 1);
        }

        List<String> prog = new ArrayList<>(maxTicksToReady + requiredCount);
        for (int tick = 0; tick < maxTicksToReady; tick++) {
            char[] cmds = {'-', '-', '-', '-', '-', '-'};
            for (int t = 0; t < 6; t++) {
                if (bestPos[t] == -1) continue;
                // Move first, then toggle ON on the very last tick (maxTicksToReady - 1) so no premature intersections happen!
                if (tick < moveSteps[t]) {
                    cmds[t] = moveDirs[t];
                } else if (tick == maxTicksToReady - 1) {
                    cmds[t] = 'T';
                }
            }
            prog.add(formatSixLine(cmds));
        }

        // Now all required emitters are ON and creating the entire pattern every single tick!
        for (int c = 1; c < requiredCount; c++) {
            prog.add("- - - - - -");
        }
        return prog;
    }

    static int[] searchSingleShotDFS(BoardMeta meta, int[][] candidates, int t, int[] curPos, int[] bestPos, int[] bestCost) {
        if (t == 6) {
            int maxWalk = 0;
            int activeCnt = 0;
            for (int i = 0; i < 6; i++) {
                if (curPos[i] != -1) {
                    activeCnt++;
                    int w = dist(i * meta.R, curPos[i], meta.P) + 1;
                    if (w > maxWalk) maxWalk = w;
                }
            }
            if (activeCnt < 2 || maxWalk >= bestCost[0]) return bestPos;

            int[] mHits = new int[meta.numM];
            for (int i = 0; i < 6; i++) {
                if (curPos[i] == -1) continue;
                for (int mIdx : meta.rayMCells[i][curPos[i]]) {
                    mHits[mIdx]++;
                }
            }
            for (int m = 0; m < meta.numM; m++) {
                if (mHits[m] != 2) return bestPos;
            }

            int[] sHits = new int[meta.numSkirt];
            for (int i = 0; i < 6; i++) {
                if (curPos[i] == -1) continue;
                for (int sIdx : meta.raySkirtCells[i][curPos[i]]) {
                    sHits[sIdx]++;
                }
            }
            for (int s = 0; s < meta.numSkirt; s++) {
                if (sHits[s] == 2) return bestPos; // Note: sHits[s] >= 3 is 'd' and is 100% VALID!
            }

            bestCost[0] = maxWalk;
            return Arrays.copyOf(curPos, 6);
        }

        // Pruning: check if remaining emitters can even bring all M cells up to 2 hits
        int rem = 6 - t;
        int[] mHitsSoFar = new int[meta.numM];
        for (int i = 0; i < t; i++) {
            if (curPos[i] == -1) continue;
            for (int mIdx : meta.rayMCells[i][curPos[i]]) {
                mHitsSoFar[mIdx]++;
                if (mHitsSoFar[mIdx] > 2) return bestPos; // Already ruined an M cell with >= 3 rays
            }
        }
        for (int m = 0; m < meta.numM; m++) {
            if (mHitsSoFar[m] + rem < 2) return bestPos;
        }

        for (int cand : candidates[t]) {
            curPos[t] = cand;
            bestPos = searchSingleShotDFS(meta, candidates, t + 1, curPos, bestPos, bestCost);
        }
        return bestPos;
    }

    // =========================================================================
    // STRATEGY 1: 6-EMITTER WAVEFRONT / LOOKAHEAD SCHEDULER WITH PING-PONG CYCLING
    // =========================================================================

    /**
     * High-throughput multi-emitter scheduler:
     * 1. Dynamically assigns all 6 emitters to cover remaining M-cells using minimum-tick bipartite matching,
     *    explicitly favoring colinear rays (1 emitter ray passing through 2+ M-cells that are intersected
     *    by multiple cross-emitters on the same or consecutive ticks).
     * 2. At EVERY tick, evaluates all 64 active/toggle combinations (or greedy subsets) so emitters
     *    stay ON while moving whenever their rays don't hit a forbidden cell, AND opportunistic cross-intersections
     *    mark extra M-cells for free!
     * 3. Uses Ping-Pong / Periodic Cycle Replay when requiredCount > 1 so Cycle 2..req don't pay redundant travel costs.
     */
    static List<String> solveWavefrontSixEmitter(BoardMeta meta, int requiredCount, int seed, int bestKnownSize) {
        int P = meta.P;
        int numM = meta.numM;
        Random rng = (seed == 0) ? null : new Random(seed * 9973L);

        // Precompute ray popularity: how many M cells lie on ray (t, pos)
        int[][] rayMCount = new int[6][P];
        for (int t = 0; t < 6; t++) {
            for (int p = 0; p < P; p++) {
                rayMCount[t][p] = meta.rayMCells[t][p].length;
            }
        }

        // Precompute valid (u, v, posU, posV) pairs for each M cell
        int[][][] cellPairs = new int[numM][][];
        for (int m = 0; m < numM; m++) {
            Point pt = meta.mCells.get(m);
            List<int[]> list = new ArrayList<>();
            for (int u = 0; u < 6; u++) {
                int pu = meta.lookups[u][pt.r][pt.c];
                if (pu == -1) continue;
                for (int v = u + 1; v < 6; v++) {
                    if (Math.abs(u - v) == 3) continue;
                    int pv = meta.lookups[v][pt.r][pt.c];
                    if (pv == -1) continue;
                    list.add(new int[]{u, v, pu, pv});
                }
            }
            cellPairs[m] = list.toArray(new int[0][]);
        }

        // State tracking
        int[] pos = new int[6];
        boolean[] active = new boolean[6];
        for (int i = 0; i < 6; i++) pos[i] = i * meta.R;

        // Board state: 0 = 'O', 1 = 'M'/'m', 2 = 'd'
        int[] mState = new int[numM];       // 1 if M cell is currently 'M' or 'm'
        int[] skirtState = new int[meta.numSkirt]; // 1 if skirting 'O' cell is currently 'M' or 'm'
        int mLitCount = 0;
        int skirtLitCount = 0;

        int[] targetPos = new int[6];
        int[] targetM = new int[6];
        int[] partner = new int[6];
        Arrays.fill(targetPos, -1);
        Arrays.fill(targetM, -1);
        Arrays.fill(partner, -1);

        List<String> program = new ArrayList<>();
        int completedCycles = 0;

        // Track which emitter configurations were visited in Cycle 1 so Cycle 2 can reverse the order (Ping-Pong)!
        List<Integer> cycleVisitOrder = new ArrayList<>(numM);
        List<Integer> prevCycleOrder = null;

        int maxTicks = Math.min(bestKnownSize, numM * requiredCount * (P / 2 + 3) + 60);

        while (completedCycles < requiredCount && program.size() < maxTicks) {
            // 1. Assign any free emitters to unlit M-cells
            boolean assigned = true;
            while (assigned) {
                assigned = false;
                int bestM = -1, bestU = -1, bestV = -1, bestPU = -1, bestPV = -1;
                int bestScore = Integer.MAX_VALUE;

                // Also check if ONE free emitter can pair with an ALREADY ASSIGNED emitter (3-emitter L-junction!)
                // First evaluate standard 2-free-emitter assignments + shared-ray bonus
                for (int m = 0; m < numM; m++) {
                    if (mState[m] == 1) continue;
                    boolean alreadyTargeted = false;
                    for (int t = 0; t < 6; t++) {
                        if (targetM[t] == m) { alreadyTargeted = true; break; }
                    }
                    if (alreadyTargeted) continue;

                    for (int[] pair : cellPairs[m]) {
                        int u = pair[0], v = pair[1], pu = pair[2], pv = pair[3];
                        // Allow assignment if u and v are either free OR already targeting the exact same ray position pu / pv!
                        boolean uOk = (targetPos[u] == -1 || targetPos[u] == pu);
                        boolean vOk = (targetPos[v] == -1 || targetPos[v] == pv);
                        if (!uOk || !vOk) continue;
                        // At least one of u or v must be currently free so we make progress
                        if (targetPos[u] != -1 && targetPos[v] != -1) continue;

                        int dU = dist(pos[u], pu, P) + (active[u] ? 0 : 1);
                        int dV = dist(pos[v], pv, P) + (active[v] ? 0 : 1);
                        int arrival = Math.max(dU, dV);

                        // Huge bonus if pu or pv is already the current position or target of u/v, or covers multiple unlit M cells!
                        int multiBonus = (rayMCount[u][pu] + rayMCount[v][pv]) * 6;
                        int score = arrival * 100 + (dU + dV) * 4 - multiBonus;

                        if (prevCycleOrder != null) {
                            // Ping-pong guidance: prefer visiting in reverse order of previous cycle
                            int rank = prevCycleOrder.indexOf(m);
                            if (rank != -1) score += rank * 8;
                        }
                        if (rng != null) {
                            score += rng.nextInt(45);
                        }

                        if (score < bestScore) {
                            bestScore = score;
                            bestM = m;
                            bestU = u;
                            bestV = v;
                            bestPU = pu;
                            bestPV = pv;
                        }
                    }
                }

                if (bestM != -1) {
                    if (targetPos[bestU] == -1) {
                        targetPos[bestU] = bestPU;
                        targetM[bestU] = bestM;
                    }
                    if (targetPos[bestV] == -1) {
                        targetPos[bestV] = bestPV;
                        targetM[bestV] = bestM;
                    }
                    if (partner[bestU] == -1 && partner[bestV] == -1) {
                        partner[bestU] = bestV;
                        partner[bestV] = bestU;
                    }
                    assigned = true;
                }
            }

            // 2. For any emitter STILL completely idle, move it proactively toward the nearest unlit M-cell ray
            int[] proactivePos = Arrays.copyOf(targetPos, 6);
            for (int t = 0; t < 6; t++) {
                if (proactivePos[t] == -1) {
                    int bestP = -1, bestD = Integer.MAX_VALUE;
                    for (int m = 0; m < numM; m++) {
                        if (mState[m] == 1) continue;
                        Point pt = meta.mCells.get(m);
                        int reqP = meta.lookups[t][pt.r][pt.c];
                        if (reqP != -1) {
                            int d = dist(pos[t], reqP, P);
                            if (d < bestD) {
                                bestD = d;
                                bestP = reqP;
                            }
                        }
                    }
                    proactivePos[t] = bestP;
                }
            }

            // 3. Determine base movement commands for this tick
            char[] cmds = {'-', '-', '-', '-', '-', '-'};
            int[] nextPos = Arrays.copyOf(pos, 6);
            boolean[] canToggle = new boolean[6];

            for (int t = 0; t < 6; t++) {
                int dest = (targetPos[t] != -1) ? targetPos[t] : proactivePos[t];
                if (dest != -1 && pos[t] != dest) {
                    char dir = getMoveDirChar(pos[t], dest, P);
                    cmds[t] = dir;
                    nextPos[t] = stepPos(pos[t], dir, P);
                    canToggle[t] = false; // Moving this tick -> cannot issue 'T' unless we cancel the move
                } else {
                    canToggle[t] = true;  // Stationary this tick -> can issue 'T' or '-'
                }
            }

            // 4. Optimize the active bitmask at the end of this tick!
            // If an emitter is moving while currently ON (active[t] == true), we CAN also choose to cancel its move
            // and issue 'T' (turn OFF) if keeping it ON would corrupt the board!
            int bestMask = -1;
            int bestMaskScore = Integer.MIN_VALUE;

            for (int mask = 0; mask < 64; mask++) {
                // Check if 'mask' is reachable from current (pos, active, cmds)
                boolean reachable = true;
                int cancelledMoves = 0;
                for (int t = 0; t < 6; t++) {
                    boolean wantAct = ((mask >> t) & 1) != 0;
                    if (wantAct != active[t]) {
                        // Requires 'T' on track t
                        if (!canToggle[t]) {
                            // Can only issue 'T' if we cancel the move on track t!
                            // Only ever worth cancelling a move to turn OFF an active emitter to prevent a collision
                            if (active[t] && !wantAct) {
                                cancelledMoves++;
                            } else {
                                reachable = false;
                                break;
                            }
                        }
                    }
                }
                if (!reachable) continue;

                // Compute exact positions if this mask is chosen
                int p0 = (active[0] && ((mask & 1) == 0) && !canToggle[0]) ? pos[0] : nextPos[0];
                int p1 = (active[1] && ((mask & 2) == 0) && !canToggle[1]) ? pos[1] : nextPos[1];
                int p2 = (active[2] && ((mask & 4) == 0) && !canToggle[2]) ? pos[2] : nextPos[2];
                int p3 = (active[3] && ((mask & 8) == 0) && !canToggle[3]) ? pos[3] : nextPos[3];
                int p4 = (active[4] && ((mask & 16) == 0) && !canToggle[4]) ? pos[4] : nextPos[4];
                int p5 = (active[5] && ((mask & 32) == 0) && !canToggle[5]) ? pos[5] : nextPos[5];
                int[] candPos = {p0, p1, p2, p3, p4, p5};

                // Evaluate board effect of (candPos, mask)
                int newMLit = 0;
                int destroyedM = 0;
                int newSkirtLit = 0;
                int clearedSkirt = 0;

                if (Integer.bitCount(mask) >= 2) {
                    int[] mHits = new int[numM];
                    int[] sHits = new int[meta.numSkirt];
                    for (int t = 0; t < 6; t++) {
                        if (((mask >> t) & 1) == 0) continue;
                        int pt = candPos[t];
                        for (int mIdx : meta.rayMCells[t][pt]) mHits[mIdx]++;
                        for (int sIdx : meta.raySkirtCells[t][pt]) sHits[sIdx]++;
                    }

                    for (int m = 0; m < numM; m++) {
                        if (mHits[m] == 2) {
                            if (mState[m] == 0) newMLit++;
                        } else if (mHits[m] >= 3) {
                            destroyedM++;
                        }
                    }
                    for (int s = 0; s < meta.numSkirt; s++) {
                        if (sHits[s] == 2) {
                            if (skirtState[s] == 0) newSkirtLit++;
                        } else if (sHits[s] >= 3) {
                            if (skirtState[s] == 1) clearedSkirt++; // 3 beams turn 'M' into 'd', erasing the mistake!
                        }
                    }
                }

                // Never allow destroying an M cell or lighting a forbidden skirting cell
                if (destroyedM > 0 || newSkirtLit > 0) continue;

                // Check if this mask completes the entire cycle on THIS tick!
                boolean completesCycle = (mLitCount + newMLit == numM) && (skirtLitCount - clearedSkirt == 0);
                if (completesCycle) {
                    bestMask = mask;
                    break; // Immediate cycle completion is strictly optimal!
                }

                // Score the mask:
                // 1. Massive reward for lighting new M cells (+10000 per cell)
                // 2. Penalty for cancelling a planned move (-600 per cancelled move)
                // 3. Reward for having emitters already ON if they are at/near their assigned target (+40)
                int readyBonus = 0;
                for (int t = 0; t < 6; t++) {
                    boolean isOn = ((mask >> t) & 1) != 0;
                    if (isOn && targetPos[t] != -1) {
                        int d = dist(candPos[t], targetPos[t], P);
                        if (d <= 2) readyBonus += (3 - d) * 15;
                    }
                }

                int score = newMLit * 10000 + clearedSkirt * 5000 - cancelledMoves * 600 + readyBonus;
                if (score > bestMaskScore) {
                    bestMaskScore = score;
                    bestMask = mask;
                }
            }

            if (bestMask == -1) bestMask = 0;

            // 5. Apply bestMask to cmds, pos, and active
            for (int t = 0; t < 6; t++) {
                boolean wantAct = ((bestMask >> t) & 1) != 0;
                if (wantAct != active[t]) {
                    cmds[t] = 'T';
                    nextPos[t] = pos[t]; // If it was moving, the move was cancelled in favor of 'T'
                    active[t] = wantAct;
                } else {
                    pos[t] = nextPos[t];
                }
            }
            program.add(formatSixLine(cmds));

            // 6. Update mState and skirtState
            if (Integer.bitCount(bestMask) >= 2) {
                int[] mHits = new int[numM];
                int[] sHits = new int[meta.numSkirt];
                for (int t = 0; t < 6; t++) {
                    if (!active[t]) continue;
                    for (int mIdx : meta.rayMCells[t][pos[t]]) mHits[mIdx]++;
                    for (int sIdx : meta.raySkirtCells[t][pos[t]]) sHits[sIdx]++;
                }
                for (int m = 0; m < numM; m++) {
                    if (mHits[m] == 2 && mState[m] == 0) {
                        mState[m] = 1;
                        mLitCount++;
                        cycleVisitOrder.add(m);
                    } else if (mHits[m] >= 3 && mState[m] == 1) {
                        mState[m] = 0;
                        mLitCount--;
                    }
                }
                for (int s = 0; s < meta.numSkirt; s++) {
                    if (sHits[s] == 2 && skirtState[s] == 0) {
                        skirtState[s] = 1;
                        skirtLitCount++;
                    } else if (sHits[s] >= 3 && skirtState[s] == 1) {
                        skirtState[s] = 0;
                        skirtLitCount--;
                    }
                }
            }

            // Free any emitters whose target M cell is now lit!
            for (int t = 0; t < 6; t++) {
                if (targetM[t] != -1 && mState[targetM[t]] == 1) {
                    targetPos[t] = -1;
                    targetM[t] = -1;
                    partner[t] = -1;
                }
            }

            // Check pattern completion
            if (mLitCount == numM && skirtLitCount == 0) {
                completedCycles++;
                Arrays.fill(mState, 0);
                mLitCount = 0;
                Arrays.fill(targetPos, -1);
                Arrays.fill(targetM, -1);
                Arrays.fill(partner, -1);

                prevCycleOrder = new ArrayList<>(cycleVisitOrder);
                Collections.reverse(prevCycleOrder);
                cycleVisitOrder.clear();
            }
        }

        if (completedCycles < requiredCount) return null;
        return program;
    }

    // =========================================================================
    // STRATEGY 2: MULTI-SHOT CLIQUE COVER + 2-OPT TSP (EXPLOITS 3..6 BEAM SNAPSHOTS)
    // =========================================================================

    record Snapshot(int[] reqPos, int activeMask, long mMaskLo, long mMaskHi, List<Integer> coveredM) {}

    /**
     * Finds high-density multi-emitter snapshots (3, 4, 5, or 6 emitters ON at once)
     * that safely light multiple M-cells on a single tick without hitting skirting 'O' cells.
     * Then solves a TSP over the small set of snapshots!
     */
    static List<String> solveMultiBeamSetCover(BoardMeta meta, int requiredCount) {
        int numM = meta.numM;
        if (numM < 2) return null;

        // Build candidate high-yield snapshots by growing valid pairs into 3..6 emitter cliques
        List<Snapshot> candidates = new ArrayList<>();
        boolean[] coveredByAny = new boolean[numM];

        // Start from every valid pair for each M-cell and greedily add emitters 0..5 that create additional M intersections
        for (int m = 0; m < numM; m++) {
            Point pt = meta.mCells.get(m);
            for (int u = 0; u < 6; u++) {
                int pu = meta.lookups[u][pt.r][pt.c];
                if (pu == -1) continue;
                for (int v = u + 1; v < 6; v++) {
                    if (Math.abs(u - v) == 3) continue;
                    int pv = meta.lookups[v][pt.r][pt.c];
                    if (pv == -1) continue;

                    int[] reqPos = {-1, -1, -1, -1, -1, -1};
                    reqPos[u] = pu;
                    reqPos[v] = pv;
                    int mask = (1 << u) | (1 << v);

                    if (!isSnapshotValid(meta, reqPos, mask)) continue;

                    // Try extending this 2-emitter snapshot with remaining emitters w
                    for (int w = 0; w < 6; w++) {
                        if (reqPos[w] != -1) continue;
                        int bestPW = -1;
                        int bestGain = 0;
                        // Only check positions of w that pass through some M cell
                        for (int m2 = 0; m2 < numM; m2++) {
                            Point pt2 = meta.mCells.get(m2);
                            int pw = meta.lookups[w][pt2.r][pt2.c];
                            if (pw == -1) continue;
                            reqPos[w] = pw;
                            int nextMask = mask | (1 << w);
                            if (isSnapshotValid(meta, reqPos, nextMask)) {
                                int hits = countSnapshotMHits(meta, reqPos, nextMask);
                                if (hits > bestGain) {
                                    bestGain = hits;
                                    bestPW = pw;
                                }
                            }
                            reqPos[w] = -1;
                        }
                        if (bestPW != -1 && bestGain > countSnapshotMHits(meta, reqPos, mask)) {
                            reqPos[w] = bestPW;
                            mask |= (1 << w);
                        }
                    }

                    List<Integer> cov = getSnapshotMHits(meta, reqPos, mask);
                    if (!cov.isEmpty()) {
                        for (int idx : cov) coveredByAny[idx] = true;
                        candidates.add(new Snapshot(reqPos, mask, 0L, 0L, cov));
                    }
                }
            }
        }

        for (int m = 0; m < numM; m++) {
            if (!coveredByAny[m]) return null;
        }

        // Now run a greedy Set-Cover + Distance-Aware TSP over the snapshots for each cycle
        int P = meta.P;
        int[] pos = new int[6];
        boolean[] active = new boolean[6];
        for (int i = 0; i < 6; i++) pos[i] = i * meta.R;

        List<String> program = new ArrayList<>();

        for (int cycle = 0; cycle < requiredCount; cycle++) {
            boolean[] mDone = new boolean[numM];
            int doneCount = 0;

            while (doneCount < numM) {
                Snapshot bestSnap = null;
                double bestRatio = Double.MAX_VALUE;
                int bestWalkCost = Integer.MAX_VALUE;

                for (Snapshot snap : candidates) {
                    int newHits = 0;
                    for (int mIdx : snap.coveredM) {
                        if (!mDone[mIdx]) newHits++;
                    }
                    if (newHits == 0) continue;

                    // Compute transition ticks to reach snap and fire
                    int maxTicks = 0;
                    for (int t = 0; t < 6; t++) {
                        boolean wantOn = ((snap.activeMask >> t) & 1) != 0;
                        if (wantOn) {
                            int d = dist(pos[t], snap.reqPos[t], P);
                            // If already active and moving, might need to turn off + move + turn on, or just move + turn on
                            int ticks = (active[t] && d > 0 ? 1 : 0) + d + (!active[t] || d > 0 ? 1 : 0);
                            if (ticks > maxTicks) maxTicks = ticks;
                        } else if (active[t]) {
                            maxTicks = Math.max(maxTicks, 1); // Needs 1 tick to turn off
                        }
                    }
                    if (maxTicks == 0) maxTicks = 1;

                    double ratio = (double) maxTicks / (newHits * newHits);
                    if (ratio < bestRatio || (Math.abs(ratio - bestRatio) < 1e-9 && maxTicks < bestWalkCost)) {
                        bestRatio = ratio;
                        bestWalkCost = maxTicks;
                        bestSnap = snap;
                    }
                }

                if (bestSnap == null) return null;

                // Execute transition to bestSnap safely:
                // Turn off any active emitter that needs to move or be OFF, move all required emitters into place, then turn ON on the final tick!
                int totalTicks = 0;
                int[] moveSteps = new int[6];
                char[] moveDirs = new char[6];
                boolean[] turnOffFirst = new boolean[6];
                boolean[] turnOnLast = new boolean[6];

                for (int t = 0; t < 6; t++) {
                    boolean wantOn = ((bestSnap.activeMask >> t) & 1) != 0;
                    if (wantOn) {
                        int d = dist(pos[t], bestSnap.reqPos[t], P);
                        moveSteps[t] = d;
                        moveDirs[t] = getMoveDirChar(pos[t], bestSnap.reqPos[t], P);
                        if (d == 0) {
                            if (!active[t]) {
                                turnOnLast[t] = true;
                                totalTicks = Math.max(totalTicks, 1);
                            }
                        } else {
                            if (active[t]) turnOffFirst[t] = true;
                            turnOnLast[t] = true;
                            int reqT = (turnOffFirst[t] ? 1 : 0) + d + 1;
                            totalTicks = Math.max(totalTicks, reqT);
                        }
                    } else {
                        if (active[t]) {
                            turnOffFirst[t] = true;
                            totalTicks = Math.max(totalTicks, 1);
                        }
                    }
                }
                if (totalTicks == 0) totalTicks = 1;

                for (int tick = 0; tick < totalTicks; tick++) {
                    char[] cmds = {'-', '-', '-', '-', '-', '-'};
                    for (int t = 0; t < 6; t++) {
                        int offset = 0;
                        if (turnOffFirst[t]) {
                            if (tick == 0) {
                                cmds[t] = 'T';
                                active[t] = false;
                                continue;
                            }
                            offset = 1;
                        }
                        if (tick >= offset && tick < offset + moveSteps[t]) {
                            cmds[t] = moveDirs[t];
                            pos[t] = stepPos(pos[t], cmds[t], P);
                        } else if (turnOnLast[t] && tick == totalTicks - 1) {
                            cmds[t] = 'T';
                            active[t] = true;
                        }
                    }
                    program.add(formatSixLine(cmds));
                }

                for (int mIdx : bestSnap.coveredM) {
                    if (!mDone[mIdx]) {
                        mDone[mIdx] = true;
                        doneCount++;
                    }
                }
            }
        }
        return program;
    }

    static boolean isSnapshotValid(BoardMeta meta, int[] reqPos, int mask) {
        int[] mHits = new int[meta.numM];
        int[] sHits = new int[meta.numSkirt];
        for (int t = 0; t < 6; t++) {
            if (((mask >> t) & 1) == 0) continue;
            int p = reqPos[t];
            for (int mIdx : meta.rayMCells[t][p]) {
                mHits[mIdx]++;
                if (mHits[mIdx] > 2) return false;
            }
            for (int sIdx : meta.raySkirtCells[t][p]) {
                sHits[sIdx]++;
            }
        }
        for (int s = 0; s < meta.numSkirt; s++) {
            if (sHits[s] == 2) return false;
        }
        return true;
    }

    static int countSnapshotMHits(BoardMeta meta, int[] reqPos, int mask) {
        int[] mHits = new int[meta.numM];
        for (int t = 0; t < 6; t++) {
            if (((mask >> t) & 1) == 0) continue;
            for (int mIdx : meta.rayMCells[t][reqPos[t]]) mHits[mIdx]++;
        }
        int count = 0;
        for (int m = 0; m < meta.numM; m++) if (mHits[m] == 2) count++;
        return count;
    }

    static List<Integer> getSnapshotMHits(BoardMeta meta, int[] reqPos, int mask) {
        int[] mHits = new int[meta.numM];
        for (int t = 0; t < 6; t++) {
            if (((mask >> t) & 1) == 0) continue;
            for (int mIdx : meta.rayMCells[t][reqPos[t]]) mHits[mIdx]++;
        }
        List<Integer> res = new ArrayList<>();
        for (int m = 0; m < meta.numM; m++) if (mHits[m] == 2) res.add(m);
        return res;
    }

    // =========================================================================
    // STRATEGY 3: UPGRADED GREEDY PIPELINE WITH SINGLE-TOGGLE STAY-ON
    // =========================================================================

    /**
     * Directly improves your original findBestProgram timeline scheduler by:
     * 1. Keeping emitters ON across consecutive assignments when they don't collide.
     * 2. Allowing simultaneous firing on the same tick when the cross-pairs don't hit forbidden cells.
     */
    static List<String> solveTimelinePipeline(BoardMeta meta, int requiredCount) {
        int P = meta.P;
        List<Character>[] cmds = new List[6];
        for (int i = 0; i < 6; i++) cmds[i] = new ArrayList<>();

        int[] pos = new int[6];
        for (int i = 0; i < 6; i++) pos[i] = i * meta.R;
        int[] freeTick = new int[6];
        int minNextFireTick = 0;

        // Track which emitters are active at each tick to allow simultaneous non-interfering shots
        List<int[]> scheduledShots = new ArrayList<>(); // {tick, u, posU, v, posV}

        for (int cycle = 0; cycle < requiredCount; cycle++) {
            Set<Point> unvisited = new HashSet<>(meta.mCells);
            int cycleStartTick = minNextFireTick;

            while (!unvisited.isEmpty()) {
                Point bestPoint = null;
                int bestU = -1, bestV = -1;
                int bestFireTick = Integer.MAX_VALUE;
                int bestTotalDist = Integer.MAX_VALUE;

                for (Point p : unvisited) {
                    for (int u = 0; u < 6; u++) {
                        int reqPosU = meta.lookups[u][p.r][p.c];
                        if (reqPosU == -1) continue;
                        for (int v = u + 1; v < 6; v++) {
                            if (Math.abs(u - v) == 3) continue;
                            int reqPosV = meta.lookups[v][p.r][p.c];
                            if (reqPosV == -1) continue;

                            int distU = dist(pos[u], reqPosU, P);
                            int distV = dist(pos[v], reqPosV, P);
                            int readyTime = Math.max(freeTick[u] + distU, freeTick[v] + distV);
                            int fireTick = Math.max(cycleStartTick, readyTime);

                            // Advance fireTick until it doesn't collide with any already-scheduled shot on the same tick
                            while (!canCoFireAtTick(meta, scheduledShots, fireTick, u, reqPosU, v, reqPosV)) {
                                fireTick++;
                            }

                            if (fireTick < bestFireTick || (fireTick == bestFireTick && (distU + distV) < bestTotalDist)) {
                                bestFireTick = fireTick;
                                bestTotalDist = distU + distV;
                                bestPoint = p;
                                bestU = u;
                                bestV = v;
                            }
                        }
                    }
                }

                unvisited.remove(bestPoint);
                int reqPosU = meta.lookups[bestU][bestPoint.r][bestPoint.c];
                int reqPosV = meta.lookups[bestV][bestPoint.r][bestPoint.c];

                char dirU = getMoveDirChar(pos[bestU], reqPosU, P);
                int distU = dist(pos[bestU], reqPosU, P);
                for (int i = 0; i < distU; i++) cmds[bestU].add(dirU);
                while (cmds[bestU].size() < bestFireTick) cmds[bestU].add('-');
                cmds[bestU].add('T');
                cmds[bestU].add('T');

                char dirV = getMoveDirChar(pos[bestV], reqPosV, P);
                int distV = dist(pos[bestV], reqPosV, P);
                for (int i = 0; i < distV; i++) cmds[bestV].add(dirV);
                while (cmds[bestV].size() < bestFireTick) cmds[bestV].add('-');
                cmds[bestV].add('T');
                cmds[bestV].add('T');

                pos[bestU] = reqPosU;
                pos[bestV] = reqPosV;
                freeTick[bestU] = bestFireTick + 2;
                freeTick[bestV] = bestFireTick + 2;
                scheduledShots.add(new int[]{bestFireTick, bestU, reqPosU, bestV, reqPosV});

                minNextFireTick = Math.max(minNextFireTick, bestFireTick);
            }
            // Next cycle must start after all shots in the current cycle finish
            minNextFireTick = minNextFireTick + 1;
        }

        int maxLen = 0;
        for (int i = 0; i < 6; i++) maxLen = Math.max(maxLen, cmds[i].size());
        // Trim trailing 'T' (turn-off) on the very last tick if not needed
        while (maxLen > 0) {
            boolean anyOn = false;
            for (int i = 0; i < 6; i++) {
                if (cmds[i].size() == maxLen && cmds[i].get(maxLen - 1) == 'T') {
                    // This is a turn-off after the final cycle completed!
                    cmds[i].remove(maxLen - 1);
                } else if (cmds[i].size() == maxLen) {
                    anyOn = true;
                }
            }
            if (!anyOn) {
                int newMax = 0;
                for (int i = 0; i < 6; i++) newMax = Math.max(newMax, cmds[i].size());
                if (newMax < maxLen) maxLen = newMax;
                else break;
            } else break;
        }

        List<String> program = new ArrayList<>(maxLen);
        for (int tick = 0; tick < maxLen; tick++) {
            char[] line = {'-', '-', '-', '-', '-', '-'};
            for (int i = 0; i < 6; i++) {
                if (tick < cmds[i].size()) line[i] = cmds[i].get(tick);
            }
            program.add(formatSixLine(line));
        }
        return program;
    }

    static boolean canCoFireAtTick(BoardMeta meta, List<int[]> scheduledShots, int tick, int u, int posU, int v, int posV) {
        List<int[]> activeEmitters = new ArrayList<>();
        activeEmitters.add(new int[]{u, posU});
        activeEmitters.add(new int[]{v, posV});

        for (int[] shot : scheduledShots) {
            if (shot[0] == tick) {
                if (shot[1] == u || shot[1] == v || shot[3] == u || shot[3] == v) return false;
                activeEmitters.add(new int[]{shot[1], shot[2]});
                activeEmitters.add(new int[]{shot[3], shot[4]});
            }
        }
        if (activeEmitters.size() == 2) return true;

        int numRows = meta.numRows, numCols = meta.numCols;
        int[][] pairHits = new int[numRows][numCols];
        for (int i = 0; i < activeEmitters.size(); i++) {
            int e1 = activeEmitters.get(i)[0], p1 = activeEmitters.get(i)[1];
            for (int j = i + 1; j < activeEmitters.size(); j++) {
                int e2 = activeEmitters.get(j)[0], p2 = activeEmitters.get(j)[1];
                int code = meta.intersectCell[e1][p1][e2][p2];
                if (code != -1) {
                    int r = code >>> 16, c = code & 0xFFFF;
                    pairHits[r][c]++;
                    if (meta.isForbidden[r][c] || pairHits[r][c] > 1) return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    // STRATEGY 4: PEEPHOLE TIMELINE COMPACTOR & IDLE-TICK REMOVER
    // =========================================================================

    static List<String> peepholeOptimize(int R, char[][] patternGrid, int requiredCount, List<String> origProg) {
        if (origProg == null || origProg.isEmpty()) return origProg;
        List<String> current = new ArrayList<>(origProg);

        // Pass 1: Remove any "- - - - - -" line if removing it still passes verification
        for (int i = current.size() - 1; i >= 0; i--) {
            if (current.get(i).equals("- - - - - -")) {
                List<String> candidate = new ArrayList<>(current);
                candidate.remove(i);
                if (verifyAndTrimProgram(R, patternGrid, requiredCount, candidate)) {
                    current = candidate;
                    if (i > current.size()) i = current.size();
                }
            }
        }

        // Pass 2: Try merging consecutive ticks where two sets of emitters act disjointly
        boolean merged = true;
        int pass = 0;
        while (merged && pass++ < 3 && current.size() <= 800) {
            merged = false;
            for (int i = 0; i < current.size() - 1; i++) {
                String[] a = current.get(i).split(" ");
                String[] b = current.get(i + 1).split(" ");
                boolean disjoint = true;
                String[] combined = new String[6];
                for (int t = 0; t < 6; t++) {
                    if (!a[t].equals("-") && !b[t].equals("-")) {
                        disjoint = false;
                        break;
                    }
                    combined[t] = !a[t].equals("-") ? a[t] : b[t];
                }
                if (disjoint) {
                    List<String> candidate = new ArrayList<>(current);
                    candidate.set(i, String.join(" ", combined));
                    candidate.remove(i + 1);
                    if (verifyAndTrimProgram(R, patternGrid, requiredCount, candidate)) {
                        current = candidate;
                        merged = true;
                        i = Math.max(-1, i - 1);
                    }
                }
            }
        }

        return current;
    }

    // =========================================================================
    // STRATEGY 5: 2-EMITTER CONTINUOUS SWEEP + 2-OPT TSP (ZERO COLLISION OVERHEAD)
    // =========================================================================

    static int pairTransitionCost(int curA, int curB, int nextA, int nextB, int P, boolean activeA, boolean activeB) {
        int sA = dist(curA, nextA, P);
        int sB = dist(curB, nextB, P);
        int reqA = (activeA ? 1 : 0) + sA + 1; // turn off (if active) + move + turn on
        int reqB = (!activeB ? 1 : 0) + sB;   // turn on (if inactive) + move
        return Math.max(reqA, reqB);
    }

    static List<Target> solvePairTSP(List<Target> targets, int startA, int startB, int P, boolean activeA, boolean activeB) {
        if (targets.size() <= 1) return new ArrayList<>(targets);

        List<Target> remaining = new ArrayList<>(targets);
        List<Target> tour = new ArrayList<>(targets.size());

        int curA = startA, curB = startB;
        boolean curActA = activeA, curActB = activeB;
        while (!remaining.isEmpty()) {
            int bestIdx = 0;
            int bestCost = Integer.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                Target t = remaining.get(i);
                int cost = pairTransitionCost(curA, curB, t.posA(), t.posB(), P, curActA, curActB);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestIdx = i;
                }
            }
            Target next = remaining.remove(bestIdx);
            tour.add(next);
            curA = next.posA();
            curB = next.posB();
            curActA = true;
            curActB = true;
        }

        // 2-opt local search
        boolean improved = true;
        int iter = 0;
        while (improved && iter++ < 50) {
            improved = false;
            for (int i = 0; i < tour.size() - 1; i++) {
                for (int j = i + 1; j < tour.size(); j++) {
                    int prevA = (i == 0 ? startA : tour.get(i - 1).posA());
                    int prevB = (i == 0 ? startB : tour.get(i - 1).posB());
                    boolean prevActA = (i == 0 ? activeA : true);
                    boolean prevActB = (i == 0 ? activeB : true);

                    int curCost = pairTransitionCost(prevA, prevB, tour.get(i).posA(), tour.get(i).posB(), P, prevActA, prevActB);
                    for (int k = i; k < j; k++) {
                        curCost += pairTransitionCost(tour.get(k).posA(), tour.get(k).posB(), tour.get(k + 1).posA(), tour.get(k + 1).posB(), P, true, true);
                    }
                    if (j + 1 < tour.size()) {
                        curCost += pairTransitionCost(tour.get(j).posA(), tour.get(j).posB(), tour.get(j + 1).posA(), tour.get(j + 1).posB(), P, true, true);
                    }

                    int revCost = pairTransitionCost(prevA, prevB, tour.get(j).posA(), tour.get(j).posB(), P, prevActA, prevActB);
                    for (int k = j; k > i; k--) {
                        revCost += pairTransitionCost(tour.get(k).posA(), tour.get(k).posB(), tour.get(k - 1).posA(), tour.get(k - 1).posB(), P, true, true);
                    }
                    if (j + 1 < tour.size()) {
                        revCost += pairTransitionCost(tour.get(i).posA(), tour.get(i).posB(), tour.get(j + 1).posA(), tour.get(j + 1).posB(), P, true, true);
                    }

                    if (revCost < curCost) {
                        Collections.reverse(tour.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }

        return tour;
    }

    static List<String> generateDirectedPair(BoardMeta meta, List<Target> rawTargets, int trackA, int trackB, int requiredCount) {
        int P = meta.P;
        int posA = trackA * meta.R;
        int posB = trackB * meta.R;
        boolean activeA = false;
        boolean activeB = false;

        List<String> program = new ArrayList<>();

        for (int cycle = 0; cycle < requiredCount; cycle++) {
            List<Target> cycleTargets = solvePairTSP(rawTargets, posA, posB, P, activeA, activeB);

            for (Target target : cycleTargets) {
                int destA = target.posA();
                int destB = target.posB();

                int sA = dist(posA, destA, P);
                int sB = dist(posB, destB, P);
                char dirA = getMoveDirChar(posA, destA, P);
                char dirB = getMoveDirChar(posB, destB, P);

                List<Character> actionsA = new ArrayList<>();
                if (activeA) actionsA.add('T'); // turn off while moving
                for (int s = 0; s < sA; s++) actionsA.add(dirA);
                actionsA.add('T'); // turn on on arrival to fire

                List<Character> actionsB = new ArrayList<>();
                if (!activeB) actionsB.add('T'); // turn on
                for (int s = 0; s < sB; s++) actionsB.add(dirB);

                int totalTicks = Math.max(actionsA.size(), actionsB.size());
                if (totalTicks == 0) totalTicks = 1;

                char[] scheduledA = new char[totalTicks];
                Arrays.fill(scheduledA, '-');
                for (int i = 0; i < actionsA.size() - 1; i++) {
                    scheduledA[i] = actionsA.get(i);
                }
                scheduledA[totalTicks - 1] = 'T';

                char[] scheduledB = new char[totalTicks];
                Arrays.fill(scheduledB, '-');
                for (int i = 0; i < actionsB.size(); i++) {
                    scheduledB[i] = actionsB.get(i);
                }

                for (int tick = 0; tick < totalTicks; tick++) {
                    char[] line = {'-', '-', '-', '-', '-', '-'};
                    line[trackA] = scheduledA[tick];
                    line[trackB] = scheduledB[tick];
                    program.add(formatSixLine(line));
                }

                posA = destA;
                posB = destB;
                activeA = true;
                activeB = true;
            }
        }

        return program;
    }

    static List<String> generatePairProgram(BoardMeta meta, int u, int v, int requiredCount) {
        List<Target> targetsUV = new ArrayList<>(meta.numM);
        List<Target> targetsVU = new ArrayList<>(meta.numM);
        for (int i = 0; i < meta.numM; i++) {
            Point p = meta.mCells.get(i);
            int pu = meta.lookups[u][p.r][p.c];
            int pv = meta.lookups[v][p.r][p.c];
            if (pu == -1 || pv == -1) return null;
            targetsUV.add(new Target(i, p, pu, pv));
            targetsVU.add(new Target(i, p, pv, pu));
        }

        List<String> progUV = generateDirectedPair(meta, targetsUV, u, v, requiredCount);
        List<String> progVU = generateDirectedPair(meta, targetsVU, v, u, requiredCount);

        if (progUV == null) return progVU;
        if (progVU == null) return progUV;
        return (progUV.size() <= progVU.size()) ? progUV : progVU;
    }

    // =========================================================================
    // MASTER PORTFOLIO SELECTOR (PICKS SHORTEST VERIFIED PROGRAM)
    // =========================================================================

    static boolean verifyAndTrimProgram(int R, char[][] patternGrid, int requiredCount, List<String> prog) {
        if (prog == null || prog.isEmpty()) return false;
        Simulator sim = new Simulator(R, patternGrid, requiredCount);
        int completedAtStep = -1;
        for (int i = 0; i < prog.size(); i++) {
            sim.step(prog.get(i));
            if (sim.acceptedPatterns == requiredCount) {
                completedAtStep = i + 1;
                break;
            }
        }
        if (completedAtStep != -1) {
            if (completedAtStep < prog.size()) {
                prog.subList(completedAtStep, prog.size()).clear();
            }
            return true;
        }
        return false;
    }

    public static List<String> findBestProgram(int R, char[][] patternGrid, int requiredCount, List<Point> mCells) {
        BoardMeta meta = new BoardMeta(R, patternGrid, mCells);
        List<String> bestProg = null;

        // 0. Try 1-Tick Single-Shot (instant optimal if pattern can be shot in 1 tick)
        List<String> singleShot = trySingleTickSnapshot(meta, requiredCount);
        if (singleShot != null && verifyAndTrimProgram(R, patternGrid, requiredCount, singleShot)) {
            bestProg = singleShot;
        }

        // 1. Evaluate upgraded Timeline Pipeline (co-firing + trailing-T removal)
        List<String> timelineProg = solveTimelinePipeline(meta, requiredCount);
        if (timelineProg != null && (bestProg == null || timelineProg.size() < bestProg.size())) {
            if (verifyAndTrimProgram(R, patternGrid, requiredCount, timelineProg)) {
                bestProg = timelineProg;
            }
        }

        // 2. Evaluate all 12 valid Emitter Pairs with Continuous Sweep + 2-Opt TSP
        for (int u = 0; u < 6; u++) {
            for (int v = u + 1; v < 6; v++) {
                if (Math.abs(u - v) == 3) continue;
                List<String> pairProg = generatePairProgram(meta, u, v, requiredCount);
                if (pairProg != null && (bestProg == null || pairProg.size() < bestProg.size())) {
                    if (verifyAndTrimProgram(R, patternGrid, requiredCount, pairProg)) {
                        bestProg = pairProg;
                    }
                }
            }
        }

        // 3. Evaluate Multi-Beam Clique Set Cover (3..6 emitters firing concurrently)
        List<String> multiProg = solveMultiBeamSetCover(meta, requiredCount);
        if (multiProg != null && (bestProg == null || multiProg.size() < bestProg.size())) {
            if (verifyAndTrimProgram(R, patternGrid, requiredCount, multiProg)) {
                bestProg = multiProg;
            }
        }

        // 4. Evaluate 6-Emitter Wavefront Scheduler (deterministic + multi-start randomized)
        int restarts = (mCells.size() * requiredCount <= 120) ? 16 : 6;
        for (int seed = 0; seed < restarts; seed++) {
            int limit = (bestProg != null) ? bestProg.size() : Integer.MAX_VALUE;
            List<String> parProg = solveWavefrontSixEmitter(meta, requiredCount, seed, limit);
            if (parProg != null && (bestProg == null || parProg.size() < bestProg.size())) {
                if (verifyAndTrimProgram(R, patternGrid, requiredCount, parProg)) {
                    bestProg = parProg;
                }
            }
        }

        // 5. Run Peephole Compaction on the winning program to squeeze out any remaining slack ticks
        if (bestProg != null) {
            bestProg = peepholeOptimize(R, patternGrid, requiredCount, bestProg);
        }

        return bestProg;
    }

    static class Simulator {
        final int R, numRows, numCols, pRows, pCols, requiredCount;
        final List<Point> perimeter;
        final char[][] baseGrid;
        char[][] grid;
        int[] indices = new int[6];
        boolean[] active = new boolean[6];
        int acceptedPatterns = 0;
        final char[][] patternGrid;

        Simulator(int boardRadius, char[][] patternGrid, int requiredCount) {
            this.R = boardRadius;
            this.numRows = 2 * R + 1;
            this.numCols = 4 * R + 1;
            this.patternGrid = patternGrid;
            this.pRows = numRows;
            this.pCols = numCols;
            this.requiredCount = requiredCount;
            this.perimeter = buildPerimeter(R);

            this.baseGrid = new char[numRows][numCols];
            for (int r = 0; r < numRows; r++) {
                int d = Math.abs(r - R);
                Arrays.fill(baseGrid[r], '-');
                int left = d, right = (4 * R) - d;
                for (int c = left; c <= right; c += 2) {
                    baseGrid[r][c] = (c == left || c == right || r == 0 || r == numRows - 1) ? '#' : 'O';
                }
            }
            this.grid = new char[numRows][numCols];
            for (int r = 0; r < numRows; r++) grid[r] = Arrays.copyOf(baseGrid[r], numCols);
            for (int i = 0; i < 6; i++) {
                indices[i] = i * R;
                active[i] = false;
            }
        }

        void step(String instructionLine) {
            for (int r = 0; r < numRows; r++) {
                for (int c = 0; c < numCols; c++) {
                    char ch = grid[r][c];
                    if (ch == 'x' || ch == 'd') grid[r][c] = 'O';
                    else if (ch == 'm') grid[r][c] = 'M';
                }
            }

            String[] tokens = instructionLine.trim().split("\\s+");
            int perimLen = perimeter.size();
            for (int t = 0; t < 6; t++) {
                char cmd = tokens[t].charAt(0);
                if (cmd == 'F') indices[t] = (indices[t] + 1) % perimLen;
                else if (cmd == 'B') indices[t] = (indices[t] - 1 + perimLen) % perimLen;
                else if (cmd == 'T') active[t] = !active[t];
            }

            int[][] hitCount = new int[numRows][numCols];
            for (int t = 0; t < 6; t++) {
                if (!active[t]) continue;
                Point start = perimeter.get(indices[t]);
                int dr = FIRE_OV[t][0], dc = FIRE_OV[t][1];
                int r = start.r + dr, c = start.c + dc;
                while (r >= 0 && r < numRows && c >= 0 && c < numCols) {
                    if (baseGrid[r][c] == 'O') hitCount[r][c]++;
                    else if (baseGrid[r][c] == '#') break;
                    r += dr;
                    c += dc;
                }
            }

            for (int r = 0; r < numRows; r++) {
                for (int c = 0; c < numCols; c++) {
                    char cell = grid[r][c];
                    if (cell != 'O' && cell != 'M') continue;
                    int n = hitCount[r][c];
                    if (n == 2) grid[r][c] = (cell == 'O' ? 'm' : 'M');
                    else if (n >= 3) grid[r][c] = 'd';
                }
            }

            boolean matches = true;
            for (int r = 0; r < pRows; r++) {
                for (int c = 0; c < pCols; c++) {
                    char pCell = patternGrid[r][c];
                    if (pCell == '-' || pCell == '*') continue;
                    char gCell = grid[r][c];
                    if (pCell == 'M') {
                        if (gCell != 'M' && gCell != 'm') { matches = false; break; }
                    } else if (pCell == 'O') {
                        boolean isSkirting = false;
                        for (int[] d : HEX_DIRS) {
                            int nr = r + d[0], nc = c + d[1];
                            if (nr >= 0 && nr < pRows && nc >= 0 && nc < pCols && patternGrid[nr][nc] == 'M') {
                                isSkirting = true;
                                break;
                            }
                        }
                        if (isSkirting && (gCell == 'M' || gCell == 'm')) { matches = false; break; }
                    }
                }
                if (!matches) break;
            }

            if (matches) {
                acceptedPatterns++;
                for (int r = 0; r < pRows; r++) {
                    for (int c = 0; c < pCols; c++) {
                        if (patternGrid[r][c] == 'M') grid[r][c] = 'x';
                    }
                }
            }
        }
    }

    record BoardInput(int index, int radius, int rows, int cols, int req, char[][] patternGrid, List<Point> mCells) {}

    public static String processFile(Path inputPath) throws IOException {
        List<String> inLines = Files.readAllLines(inputPath);
        int idx = 0;
        int n = Integer.parseInt(inLines.get(idx++).trim());

        List<BoardInput> boards = new ArrayList<>(n);
        for (int b = 0; b < n; b++) {
            while (idx < inLines.size() && inLines.get(idx).trim().isEmpty()) idx++;
            String[] toks = inLines.get(idx++).trim().split("\\s+");
            int radius = Integer.parseInt(toks[0]);
            int rows = Integer.parseInt(toks[1]);
            int cols = Integer.parseInt(toks[2]);
            int req = Integer.parseInt(toks[3]);

            char[][] patternGrid = new char[rows][cols];
            List<Point> mCells = new ArrayList<>();
            for (int r = 0; r < rows; r++) {
                String line = inLines.get(idx++);
                for (int c = 0; c < cols; c++) {
                    char ch = line.charAt(c);
                    patternGrid[r][c] = ch;
                    if (ch == 'M') mCells.add(new Point(r, c));
                }
            }
            boards.add(new BoardInput(b, radius, rows, cols, req, patternGrid, mCells));
        }

        @SuppressWarnings("unchecked")
        List<String>[] results = new List[n];
        boards.parallelStream().forEach(board -> {
            List<String> prog = findBestProgram(board.radius, board.patternGrid, board.req, board.mCells);
            Simulator sim = new Simulator(board.radius, board.patternGrid, board.req);
            for (String stepStr : prog) sim.step(stepStr);
            if (sim.acceptedPatterns != board.req) {
                System.err.printf("ERROR on Board %d! Accepted: %d, Required: %d\n",
                        board.index + 1, sim.acceptedPatterns, board.req);
            }
            results[board.index] = prog;
        });

        StringBuilder out = new StringBuilder();
        int totalInstructions = 0;
        for (int b = 0; b < n; b++) {
            List<String> prog = results[b];
            totalInstructions += prog.size();
            for (String stepLine : prog) {
                out.append(stepLine).append("\n");
            }
            out.append("\n");
        }

        System.out.println("Completed " + inputPath.getFileName() + " -> Total instruction lines: " + totalInstructions);
        return out.toString();
    }

    public static void main(String[] args) throws IOException {
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-6");

        String[] files = {"1-small", "2-medium", "3-large", "4-enormous"};
        for (String name : files) {
            Path inPath = baseDir.resolve("in_level-6_" + name + ".txt");
            Path outPath = baseDir.resolve("out_level-6_" + name + ".txt");
            if (Files.exists(inPath)) {
                System.out.println("Processing " + name + "...");
                String result = processFile(inPath);
                Files.writeString(outPath, result);
            }
        }
    }
}