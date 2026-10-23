import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Level4 {

    record Point(int r, int c) {}

    public static List<Point> buildRail(int R) {
        List<Point> rail = new ArrayList<>(6 * R);

        // Side 1: Top edge from Corner 2 (0, R) to Corner 3 (0, 3R)
        for (int i = 0; i < R; i++) {
            rail.add(new Point(0, R + 2 * i));
        }

        // Side 2: Upper-Right edge from Corner 3 (0, 3R) to Corner 4 (R, 4R)
        for (int i = 0; i < R; i++) {
            rail.add(new Point(i, 3 * R + i));
        }

        // Side 3: Lower-Right edge from Corner 4 (R, 4R) to Corner 5 (2R, 3R)
        for (int i = 0; i < R; i++) {
            rail.add(new Point(R + i, 4 * R - i));
        }

        // Side 4: Bottom edge from Corner 5 (2R, 3R) to Corner 6 (2R, R)
        for (int i = 0; i < R; i++) {
            rail.add(new Point(2 * R, 3 * R - 2 * i));
        }

        // Side 5: Lower-Left edge from Corner 6 (2R, R) to Corner 1 (R, 0)
        for (int i = 0; i < R; i++) {
            rail.add(new Point(2 * R - i, R - i));
        }

        // Side 6: Upper-Left edge from Corner 1 (R, 0) to Corner 2 (0, R)
        for (int i = 0; i < R; i++) {
            rail.add(new Point(R - i, i));
        }

        return rail;
    }

    public static char[][] createEmptyBoard(int r) {
        int totalRows = 2 * r + 1;
        int totalCols = 4 * r + 1;
        char[][] grid = new char[totalRows][totalCols];

        for (int i = 0; i < totalRows; i++) {
            int d = Math.abs(i - r);
            StringBuilder sb = new StringBuilder();
            sb.append("-".repeat(d));
            if (d == r) {
                sb.append("*");
                sb.append("-*".repeat(r));
            } else {
                int k = 2 * r - 1 - d;
                sb.append("*");
                sb.append("-O".repeat(k));
                sb.append("-*");
            }
            sb.append("-".repeat(d));
            String rowStr = sb.toString();
            for (int c = 0; c < totalCols; c++) {
                grid[i][c] = rowStr.charAt(c);
            }
        }
        return grid;
    }

    public static String solveProgram(int R, int L, List<String> instructions) {
        List<Point> rail = buildRail(R);
        int railLen = rail.size(); // 6 * R

        // Emitter indices along rail (0-based)
        // Emitter 1 -> Corner 1: 5 * R
        // Emitter 2 -> Corner 2: 0
        // Emitter 3 -> Corner 3: R
        // Emitter 4 -> Corner 4: 2 * R
        // Emitter 5 -> Corner 5: 3 * R
        // Emitter 6 -> Corner 6: 4 * R
        int[] pos = new int[]{
                5 * R,  // Emitter 1
                0,      // Emitter 2
                R,      // Emitter 3
                2 * R,  // Emitter 4
                3 * R,  // Emitter 5
                4 * R   // Emitter 6
        };
        boolean[] active = new boolean[6];

        int[][] dirVectors = new int[][]{
                {0, 2},   // 1: East
                {1, 1},   // 2: South-East
                {1, -1},  // 3: South-West
                {0, -2},  // 4: West
                {-1, -1}, // 5: North-West
                {-1, 1}   // 6: North-East
        };

        char[][] grid = createEmptyBoard(R);
        int numRows = 2 * R + 1;
        int numCols = 4 * R + 1;

        for (int tick = 0; tick < L; tick++) {
            String line = instructions.get(tick).trim();
            String[] tokens = line.split("\\s+");

            // 1. Execute all 6 instructions simultaneously
            for (int e = 0; e < 6; e++) {
                char cmd = tokens[e].charAt(0);
                switch (cmd) {
                    case 'F' -> pos[e] = (pos[e] + 1) % railLen;
                    case 'B' -> pos[e] = (pos[e] - 1 + railLen) % railLen;
                    case 'T' -> active[e] = !active[e];
                    case '-' -> {}
                }
            }

            // 2. Fire beams from active emitters
            int[][] beamCount = new int[numRows][numCols];
            for (int e = 0; e < 6; e++) {
                if (!active[e]) continue;

                Point start = rail.get(pos[e]);
                int dr = dirVectors[e][0];
                int dc = dirVectors[e][1];

                int r = start.r() + dr;
                int c = start.c() + dc;
                while (r >= 0 && r < numRows && c >= 0 && c < numCols) {
                    char ch = grid[r][c];
                    if (ch == 'O' || ch == 'M') {
                        beamCount[r][c]++;
                    } else {
                        // Border or out of board
                        break;
                    }
                    r += dr;
                    c += dc;
                }
            }

            // 3. Create and destroy matter
            for (int r = 0; r < numRows; r++) {
                for (int c = 0; c < numCols; c++) {
                    char ch = grid[r][c];
                    if (ch == 'O' || ch == 'M') {
                        int cnt = beamCount[r][c];
                        if (cnt == 2) {
                            grid[r][c] = 'M';
                        } else if (cnt >= 3) {
                            grid[r][c] = 'O';
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < numRows; r++) {
            sb.append(new String(grid[r])).append("\n");
        }
        return sb.toString();
    }

    public static String processFileContent(String content) {
        String[] lines = content.split("\r?\n");
        int lineIdx = 0;
        while (lineIdx < lines.length && lines[lineIdx].trim().isEmpty()) {
            lineIdx++;
        }
        if (lineIdx >= lines.length) return "";

        int n = Integer.parseInt(lines[lineIdx++].trim());
        StringBuilder out = new StringBuilder();

        for (int p = 0; p < n; p++) {
            while (lineIdx < lines.length && lines[lineIdx].trim().isEmpty()) {
                lineIdx++;
            }
            if (lineIdx >= lines.length) break;

            int R = Integer.parseInt(lines[lineIdx++].trim());
            int L = Integer.parseInt(lines[lineIdx++].trim());

            List<String> instructions = new ArrayList<>(L);
            for (int i = 0; i < L; i++) {
                instructions.add(lines[lineIdx++]);
            }

            String boardStr = solveProgram(R, L, instructions);
            out.append(boardStr).append("\n");
        }

        return out.toString();
    }

    public static void processFile(Path inputPath, Path outputPath) throws IOException {
        String content = Files.readString(inputPath);
        String result = processFileContent(content);
        Files.writeString(outputPath, result);
        System.out.println("Processed " + inputPath.getFileName() + " -> " + outputPath.getFileName());
    }

    public static void main(String[] args) throws IOException {
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-4");

        // Test with example
        Path exampleIn = baseDir.resolve("in_level-4_0-example.txt");
        Path exampleExpected = baseDir.resolve("out_level-4_0-example.txt");
        Path testOut = baseDir.resolve("test_out_example.txt");

        if (Files.exists(exampleIn)) {
            processFile(exampleIn, testOut);
            if (Files.exists(exampleExpected)) {
                String expected = Files.readString(exampleExpected);
                String actual = Files.readString(testOut);
                if (expected.equals(actual)) {
                    System.out.println("Level 4 Example MATCHES EXACTLY!");
                } else {
                    System.err.println("Mismatch with example output!");
                    System.err.println("Expected:\n" + expected);
                    System.err.println("Actual:\n" + actual);
                }
            }
            Files.deleteIfExists(testOut);
        }

        // Small
        Path smallIn = baseDir.resolve("in_level-4_1-small.txt");
        Path smallOut = baseDir.resolve("out_level-4_1-small.txt");
        if (Files.exists(smallIn)) {
            processFile(smallIn, smallOut);
        }

        // Large
        Path largeIn = baseDir.resolve("in_level-4_2-large.txt");
        Path largeOut = baseDir.resolve("out_level-4_2-large.txt");
        if (Files.exists(largeIn)) {
            processFile(largeIn, largeOut);
        }
    }
}
