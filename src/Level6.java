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

    static int transitionCost(int curA, int curB, int nextA, int nextB, int P, boolean activeA, boolean activeB) {
        int sA = dist(curA, nextA, P);
        int sB = dist(curB, nextB, P);
        int reqA = (activeA ? 1 : 0) + sA + 1; // turn off (if active) + move + turn on
        int reqB = (!activeB ? 1 : 0) + sB;   // turn on (if inactive) + move
        return Math.max(reqA, reqB);
    }

    static int computeTourCost(List<Target> tour, int startA, int startB, int P, boolean activeA, boolean activeB) {
        if (tour.isEmpty()) return 0;
        int cost = transitionCost(startA, startB, tour.get(0).posA, tour.get(0).posB, P, activeA, activeB);
        for (int i = 0; i < tour.size() - 1; i++) {
            cost += transitionCost(tour.get(i).posA, tour.get(i).posB, tour.get(i + 1).posA, tour.get(i + 1).posB, P, true, true);
        }
        return cost;
    }

    static List<Target> solveTSP(List<Target> targets, int startA, int startB, int P, boolean activeA, boolean activeB) {
        if (targets.size() <= 1) return new ArrayList<>(targets);

        List<Target> bestOverallTour = null;
        int bestOverallCost = Integer.MAX_VALUE;

        int startsToTry = Math.min(targets.size(), 8);
        for (int firstChoice = -1; firstChoice < startsToTry; firstChoice++) {
            List<Target> remaining = new ArrayList<>(targets);
            List<Target> tour = new ArrayList<>();

            int curA = startA, curB = startB;
            boolean curActA = activeA, curActB = activeB;

            if (firstChoice >= 0) {
                Target first = remaining.remove(firstChoice);
                tour.add(first);
                curA = first.posA;
                curB = first.posB;
                curActA = true;
                curActB = true;
            }

            while (!remaining.isEmpty()) {
                int bestIdx = 0;
                int bestCost = Integer.MAX_VALUE;
                for (int i = 0; i < remaining.size(); i++) {
                    Target t = remaining.get(i);
                    int cost = transitionCost(curA, curB, t.posA, t.posB, P, curActA, curActB);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestIdx = i;
                    }
                }
                Target next = remaining.remove(bestIdx);
                tour.add(next);
                curA = next.posA;
                curB = next.posB;
                curActA = true;
                curActB = true;
            }

            // 2-opt
            boolean improved = true;
            int iter = 0;
            while (improved && iter++ < 40) {
                improved = false;
                for (int i = 0; i < tour.size() - 1; i++) {
                    for (int j = i + 1; j < tour.size(); j++) {
                        int prevA = (i == 0 ? startA : tour.get(i - 1).posA);
                        int prevB = (i == 0 ? startB : tour.get(i - 1).posB);
                        boolean prevActA = (i == 0 ? activeA : true);
                        boolean prevActB = (i == 0 ? activeB : true);

                        int curCost = transitionCost(prevA, prevB, tour.get(i).posA, tour.get(i).posB, P, prevActA, prevActB);
                        for (int k = i; k < j; k++) {
                            curCost += transitionCost(tour.get(k).posA, tour.get(k).posB, tour.get(k + 1).posA, tour.get(k + 1).posB, P, true, true);
                        }
                        if (j + 1 < tour.size()) {
                            curCost += transitionCost(tour.get(j).posA, tour.get(j).posB, tour.get(j + 1).posA, tour.get(j + 1).posB, P, true, true);
                        }

                        int revCost = transitionCost(prevA, prevB, tour.get(j).posA, tour.get(j).posB, P, prevActA, prevActB);
                        for (int k = j; k > i; k--) {
                            revCost += transitionCost(tour.get(k).posA, tour.get(k).posB, tour.get(k - 1).posA, tour.get(k - 1).posB, P, true, true);
                        }
                        if (j + 1 < tour.size()) {
                            revCost += transitionCost(tour.get(i).posA, tour.get(i).posB, tour.get(j + 1).posA, tour.get(j + 1).posB, P, true, true);
                        }

                        if (revCost < curCost) {
                            Collections.reverse(tour.subList(i, j + 1));
                            improved = true;
                        }
                    }
                }
            }

            int finalCost = computeTourCost(tour, startA, startB, P, activeA, activeB);
            if (finalCost < bestOverallCost) {
                bestOverallCost = finalCost;
                bestOverallTour = tour;
            }
        }

        return bestOverallTour;
    }

    public static List<String> generateForPair(int R, List<Target> rawTargets, int trackA, int trackB, int requiredCount, int P) {
        int posA = trackA * R;
        int posB = trackB * R;
        boolean activeA = false;
        boolean activeB = false;

        List<String> program = new ArrayList<>();

        for (int cycle = 0; cycle < requiredCount; cycle++) {
            List<Target> cycleTargets = solveTSP(rawTargets, posA, posB, P, activeA, activeB);

            for (Target target : cycleTargets) {
                int destA = target.posA;
                int destB = target.posB;

                int sA = dist(posA, destA, P);
                int sB = dist(posB, destB, P);
                String dirA = getMoveDir(posA, destA, P);
                String dirB = getMoveDir(posB, destB, P);

                List<String> actionsA = new ArrayList<>();
                if (activeA) actionsA.add("T");
                for (int s = 0; s < sA; s++) actionsA.add(dirA);
                actionsA.add("T");

                List<String> actionsB = new ArrayList<>();
                if (!activeB) actionsB.add("T");
                for (int s = 0; s < sB; s++) actionsB.add(dirB);

                int totalTicks = Math.max(actionsA.size(), actionsB.size());
                String[] scheduledA = new String[totalTicks];
                Arrays.fill(scheduledA, "-");
                for (int i = 0; i < actionsA.size() - 1; i++) {
                    scheduledA[i] = actionsA.get(i);
                }
                scheduledA[totalTicks - 1] = "T";

                String[] scheduledB = new String[totalTicks];
                Arrays.fill(scheduledB, "-");
                for (int i = 0; i < actionsB.size(); i++) {
                    scheduledB[i] = actionsB.get(i);
                }

                for (int tick = 0; tick < totalTicks; tick++) {
                    String[] line = {"-", "-", "-", "-", "-", "-"};
                    line[trackA] = scheduledA[tick];
                    line[trackB] = scheduledB[tick];
                    program.add(String.join(" ", line));
                }

                posA = destA;
                posB = destB;
                activeA = true;
                activeB = true;
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
                if (tA == tB || Math.abs(tA - tB) == 3) continue;

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
            Files.writeString(largeOut, result);
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
