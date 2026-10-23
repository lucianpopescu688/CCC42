import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Level6 {

    public record Point(int r, int c) {}

    static final int[][] FIRE_OV = {
            {0, 2},   // 0: E
            {1, 1},   // 1: SE
            {1, -1},  // 2: SW
            {0, -2},  // 3: W
            {-1, -1}, // 4: NW
            {-1, 1}   // 5: NE
    };

    public static List<Point> buildPerimeter(int R) {
        List<Point> perim = new ArrayList<>();
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

    public static int dist(int from, int to, int P) {
        int d = Math.abs(to - from);
        return Math.min(d, P - d);
    }

    public static String getMoveDir(int from, int to, int P) {
        if (from == to) return "-";
        int cw = (to - from + P) % P;
        int ccw = (from - to + P) % P;
        return cw <= ccw ? "F" : "B";
    }

    public static int[][][] precomputePerimLookups(int R, List<Point> perim) {
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

    record Candidate(Point p, int u, int v, int fireTick, int totalDist, int distU, int distV) {}

    static List<String> solveGRASP(int R, List<Point> perim, int[][][] lookups, int requiredCount, List<Point> mCells, Random rng, double greediness) {
        int P = perim.size();

        @SuppressWarnings("unchecked")
        List<String>[] cmds = new List[6];
        for (int i = 0; i < 6; i++) cmds[i] = new ArrayList<>();

        int[] pos = new int[6];
        for (int i = 0; i < 6; i++) pos[i] = i * R;

        int[] freeTick = new int[6];
        int nextFireTick = 0;

        for (int cycle = 0; cycle < requiredCount; cycle++) {
            Set<Point> unvisited = new HashSet<>(mCells);

            while (!unvisited.isEmpty()) {
                List<Candidate> candidates = new ArrayList<>();

                for (Point p : unvisited) {
                    for (int u = 0; u < 6; u++) {
                        for (int v = u + 1; v < 6; v++) {
                            if (Math.abs(u - v) == 3) continue;

                            int reqPosU = lookups[u][p.r][p.c];
                            int reqPosV = lookups[v][p.r][p.c];
                            if (reqPosU == -1 || reqPosV == -1) continue;

                            int distU = dist(pos[u], reqPosU, P);
                            int distV = dist(pos[v], reqPosV, P);

                            int readyTime = Math.max(freeTick[u] + distU, freeTick[v] + distV);
                            int fireTick = Math.max(nextFireTick, readyTime);

                            candidates.add(new Candidate(p, u, v, fireTick, distU + distV, distU, distV));
                        }
                    }
                }

                candidates.sort(Comparator.comparingInt(Candidate::fireTick).thenComparingInt(Candidate::totalDist));

                Candidate chosen;
                if (rng == null || candidates.size() == 1 || greediness >= 1.0) {
                    chosen = candidates.get(0);
                } else {
                    int bestFire = candidates.get(0).fireTick;
                    int poolSize = 1;
                    while (poolSize < candidates.size() && candidates.get(poolSize).fireTick <= bestFire + 1) {
                        poolSize++;
                    }
                    poolSize = Math.min(poolSize, Math.max(1, (int)(candidates.size() * (1.0 - greediness))));
                    chosen = candidates.get(rng.nextInt(poolSize));
                }

                unvisited.remove(chosen.p);

                int reqPosU = lookups[chosen.u][chosen.p.r][chosen.p.c];
                int reqPosV = lookups[chosen.v][chosen.p.r][chosen.p.c];

                String dirU = getMoveDir(pos[chosen.u], reqPosU, P);
                for (int i = 0; i < chosen.distU; i++) cmds[chosen.u].add(dirU);
                while (cmds[chosen.u].size() < chosen.fireTick) cmds[chosen.u].add("-");
                cmds[chosen.u].add("T");
                cmds[chosen.u].add("T");

                String dirV = getMoveDir(pos[chosen.v], reqPosV, P);
                for (int i = 0; i < chosen.distV; i++) cmds[chosen.v].add(dirV);
                while (cmds[chosen.v].size() < chosen.fireTick) cmds[chosen.v].add("-");
                cmds[chosen.v].add("T");
                cmds[chosen.v].add("T");

                pos[chosen.u] = reqPosU;
                pos[chosen.v] = reqPosV;
                freeTick[chosen.u] = chosen.fireTick + 2;
                freeTick[chosen.v] = chosen.fireTick + 2;
                nextFireTick = chosen.fireTick + 1;
            }
        }

        int maxLen = 0;
        for (int i = 0; i < 6; i++) maxLen = Math.max(maxLen, cmds[i].size());

        List<String> program = new ArrayList<>(maxLen);
        for (int tick = 0; tick < maxLen; tick++) {
            String[] line = {"-", "-", "-", "-", "-", "-"};
            for (int i = 0; i < 6; i++) {
                if (tick < cmds[i].size()) line[i] = cmds[i].get(tick);
            }
            program.add(String.join(" ", line));
        }

        return program;
    }

    public static List<String> findBestProgram(int R, int requiredCount, List<Point> mCells, int iterations, Random rng) {
        List<Point> perim = buildPerimeter(R);
        int[][][] lookups = precomputePerimLookups(R, perim);

        List<String> best = solveGRASP(R, perim, lookups, requiredCount, mCells, null, 1.0);
        for (int it = 0; it < iterations; it++) {
            List<String> cand = solveGRASP(R, perim, lookups, requiredCount, mCells, rng, 0.85);
            if (cand.size() < best.size()) {
                best = cand;
            }
        }
        return best;
    }

    public static class Simulator {
        final int R, numRows, numCols, pRows, pCols, requiredCount;
        final List<Point> perimeter;
        final char[][] baseGrid;
        char[][] grid;
        int[] indices = new int[6];
        boolean[] active = new boolean[6];
        int acceptedPatterns = 0;
        final char[][] patternGrid;

        public Simulator(int boardRadius, char[][] patternGrid, int requiredCount) {
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

        public void step(String instructionLine) {
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

            int[][] hexDirs = {{0, 2}, {1, 1}, {1, -1}, {0, -2}, {-1, -1}, {-1, 1}};
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
                        for (int[] d : hexDirs) {
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

    public static String processFile(Path inputPath, int iters, Random rng) throws IOException {
        List<String> inLines = Files.readAllLines(inputPath);
        int idx = 0;
        int n = Integer.parseInt(inLines.get(idx++).trim());

        StringBuilder out = new StringBuilder();
        int totalInstructions = 0;

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

            List<String> prog = findBestProgram(radius, req, mCells, iters, rng);
            totalInstructions += prog.size();

            // Self-verify every board through simulator
            Simulator sim = new Simulator(radius, patternGrid, req);
            for (String stepStr : prog) {
                sim.step(stepStr);
            }
            if (sim.acceptedPatterns != req) {
                System.err.printf("ERROR on Board %d! Accepted: %d, Required: %d\n", b + 1, sim.acceptedPatterns, req);
            }

            for (String stepLine : prog) {
                out.append(stepLine).append("\n");
            }
            out.append("\n");

            if ((b + 1) % 30 == 0 || b + 1 == n) {
                System.out.printf("  Board %d / %d processed... (total lines so far: %d)\n", b + 1, n, totalInstructions);
            }
        }

        System.out.println("Completed " + inputPath.getFileName() + " -> Total instruction lines: " + totalInstructions);
        return out.toString();
    }

    public static void main(String[] args) throws IOException {
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-6");
        Random rng = new Random(42);

        // Small (50 iterations)
        Path smallIn = baseDir.resolve("in_level-6_1-small.txt");
        Path smallOut = baseDir.resolve("out_level-6_1-small.txt");
        if (Files.exists(smallIn)) {
            System.out.println("Processing Small...");
            String result = processFile(smallIn, 50, rng);
            Files.writeString(smallOut, result);
        }

        // Medium (15 iterations)
        Path medIn = baseDir.resolve("in_level-6_2-medium.txt");
        Path medOut = baseDir.resolve("out_level-6_2-medium.txt");
        if (Files.exists(medIn)) {
            System.out.println("Processing Medium...");
            String result = processFile(medIn, 15, rng);
            Files.writeString(medOut, result);
        }

        // Large (10 iterations)
        Path largeIn = baseDir.resolve("in_level-6_3-large.txt");
        Path largeOut = baseDir.resolve("out_level-6_3-large.txt");
        if (Files.exists(largeIn)) {
            System.out.println("Processing Large...");
            String result = processFile(largeIn, 10, rng);
            Files.writeString(largeOut, result);
        }

        // Enormous (8 iterations)
        Path enorIn = baseDir.resolve("in_level-6_4-enormous.txt");
        Path enorOut = baseDir.resolve("out_level-6_4-enormous.txt");
        if (Files.exists(enorIn)) {
            System.out.println("Processing Enormous...");
            String result = processFile(enorIn, 8, rng);
            Files.writeString(enorOut, result);
        }
    }
}
