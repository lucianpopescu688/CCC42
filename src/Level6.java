import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Level6 {

    record Point(int r, int c) {}
    record Target(Point cell, int posA, int posB) {}

    static final int[][] FIRE_OV = {
            {0, 2},   // 0: E
            {1, 1},   // 1: SE
            {1, -1},  // 2: SW
            {0, -2},  // 3: W
            {-1, -1}, // 4: NW
            {-1, 1}   // 5: NE
    };

    static List<Point> buildPerimeter(int R) {
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

    static int dist(int from, int to, int P) {
        int d = Math.abs(to - from);
        return Math.min(d, P - d);
    }

    static String getMoveDir(int from, int to, int P) {
        if (from == to) return "-";
        int cw = (to - from + P) % P;
        int ccw = (from - to + P) % P;
        return cw <= ccw ? "F" : "B";
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

    static List<Target> solveTSP(List<Target> targets, int startA, int startB, int P) {
        if (targets.size() <= 2) return new ArrayList<>(targets);

        List<Target> tour = new ArrayList<>();
        List<Target> remaining = new ArrayList<>(targets);

        int curA = startA, curB = startB;
        while (!remaining.isEmpty()) {
            int bestIdx = 0;
            int bestD = Integer.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                Target t = remaining.get(i);
                int d = Math.max(dist(curA, t.posA, P), dist(curB, t.posB, P));
                if (d < bestD) {
                    bestD = d;
                    bestIdx = i;
                }
            }
            Target next = remaining.remove(bestIdx);
            tour.add(next);
            curA = next.posA;
            curB = next.posB;
        }

        // 2-opt improvement with iteration limit
        boolean improved = true;
        int iter = 0;
        int maxIter = 40;
        while (improved && iter++ < maxIter) {
            improved = false;
            for (int i = 0; i < tour.size() - 1; i++) {
                for (int j = i + 1; j < tour.size(); j++) {
                    int prevA = (i == 0 ? startA : tour.get(i - 1).posA);
                    int prevB = (i == 0 ? startB : tour.get(i - 1).posB);
                    int curCost = Math.max(dist(prevA, tour.get(i).posA, P), dist(prevB, tour.get(i).posB, P));
                    if (j + 1 < tour.size()) {
                        curCost += Math.max(dist(tour.get(j).posA, tour.get(j + 1).posA, P),
                                            dist(tour.get(j).posB, tour.get(j + 1).posB, P));
                    }

                    int newCost = Math.max(dist(prevA, tour.get(j).posA, P), dist(prevB, tour.get(j).posB, P));
                    if (j + 1 < tour.size()) {
                        newCost += Math.max(dist(tour.get(i).posA, tour.get(j + 1).posA, P),
                                            dist(tour.get(i).posB, tour.get(j + 1).posB, P));
                    }

                    if (newCost < curCost) {
                        Collections.reverse(tour.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }

        return tour;
    }

    public static List<String> generateForPair(int R, List<Target> rawTargets, int trackA, int trackB, int requiredCount, int P) {
        int posA = trackA * R;
        int posB = trackB * R;
        boolean activeA = false;
        boolean activeB = false;

        List<String> program = new ArrayList<>();

        for (int cycle = 0; cycle < requiredCount; cycle++) {
            List<Target> cycleTargets = solveTSP(rawTargets, posA, posB, P);

            for (Target target : cycleTargets) {
                int destA = target.posA;
                int destB = target.posB;

                int sA = dist(posA, destA, P);
                int sB = dist(posB, destB, P);
                String dirA = getMoveDir(posA, destA, P);
                String dirB = getMoveDir(posB, destB, P);

                List<String> moveCmdsA = new ArrayList<>();
                if (activeA) {
                    moveCmdsA.add("T");
                    activeA = false;
                }
                for (int s = 0; s < sA; s++) moveCmdsA.add(dirA);

                List<String> moveCmdsB = new ArrayList<>();
                if (!activeB) {
                    moveCmdsB.add("T");
                    activeB = true;
                }
                for (int s = 0; s < sB; s++) moveCmdsB.add(dirB);

                int totalMoveSteps = Math.max(moveCmdsA.size(), moveCmdsB.size());
                for (int step = 0; step < totalMoveSteps; step++) {
                    String cmdA = step < moveCmdsA.size() ? moveCmdsA.get(step) : "-";
                    String cmdB = step < moveCmdsB.size() ? moveCmdsB.get(step) : "-";
                    String[] line = new String[]{"-", "-", "-", "-", "-", "-"};
                    line[trackA] = cmdA;
                    line[trackB] = cmdB;
                    program.add(String.join(" ", line));
                }

                posA = destA;
                posB = destB;

                // Fire
                String[] fireLine = new String[]{"-", "-", "-", "-", "-", "-"};
                fireLine[trackA] = "T";
                program.add(String.join(" ", fireLine));
                activeA = true;
            }
        }

        return program;
    }

    public static List<String> findBestProgram(int R, char[][] patternGrid, int requiredCount, List<Point> mCells) {
        List<Point> perim = buildPerimeter(R);
        int P = perim.size();
        int[][][] lookups = precomputePerimLookups(R, perim);

        List<String> bestProgram = null;

        for (int tA = 0; tA < 6; tA++) {
            for (int tB = 0; tB < 6; tB++) {
                if (tA == tB || Math.abs(tA - tB) == 3) continue; // skip parallel

                // Check if all mCells are reachable by this pair
                List<Target> targets = new ArrayList<>(mCells.size());
                boolean possible = true;
                for (Point p : mCells) {
                    int posA = lookups[tA][p.r][p.c];
                    int posB = lookups[tB][p.r][p.c];
                    if (posA == -1 || posB == -1) {
                        possible = false;
                        break;
                    }
                    targets.add(new Target(p, posA, posB));
                }
                if (!possible) continue;

                List<String> prog = generateForPair(R, targets, tA, tB, requiredCount, P);
                if (bestProgram == null || prog.size() < bestProgram.size()) {
                    bestProgram = prog;
                }
            }
        }
        return bestProgram;
    }

    // Full simulator to verify correctness on the fly
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

    public static String processFile(Path inputPath) throws IOException {
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

            List<String> prog = findBestProgram(radius, patternGrid, req, mCells);
            totalInstructions += prog.size();

            // Simulate to ensure 100% verification
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

            if ((b + 1) % 20 == 0 || b + 1 == n) {
                System.out.printf("  Board %d / %d processed... (total lines so far: %d)\n", b + 1, n, totalInstructions);
            }
        }

        System.out.println("Completed " + inputPath.getFileName() + " -> Total instruction lines: " + totalInstructions);
        return out.toString();
    }

    public static void main(String[] args) throws IOException {
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-6");

        // Small
        Path smallIn = baseDir.resolve("in_level-6_1-small.txt");
        Path smallOut = baseDir.resolve("out_level-6_1-small.txt");
        if (Files.exists(smallIn)) {
            System.out.println("Processing Small...");
            String result = processFile(smallIn);
            Files.writeString(smallOut, result);
        }

        // Medium
        Path medIn = baseDir.resolve("in_level-6_2-medium.txt");
        Path medOut = baseDir.resolve("out_level-6_2-medium.txt");
        if (Files.exists(medIn)) {
            System.out.println("Processing Medium...");
            String result = processFile(medIn);
            Files.writeString(medOut, result);
        }

        // Large
        Path largeIn = baseDir.resolve("in_level-6_3-large.txt");
        Path largeOut = baseDir.resolve("out_level-6_3-large.txt");
        if (Files.exists(largeIn)) {
            System.out.println("Processing Large...");
            String result = processFile(largeIn);
            Files.writeString(medOut.resolveSibling("out_level-6_3-large.txt"), result);
        }

        // Enormous
        Path enorIn = baseDir.resolve("in_level-6_4-enormous.txt");
        Path enorOut = baseDir.resolve("out_level-6_4-enormous.txt");
        if (Files.exists(enorIn)) {
            System.out.println("Processing Enormous...");
            String result = processFile(enorIn);
            Files.writeString(enorOut, result);
        }
    }
}
