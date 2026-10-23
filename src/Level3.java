import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Level3 {

    record Emitter(int r, int c, int direction) {}

    public static void solveBoard(char[][] grid, int numRows, int numCols) {
        List<Emitter> emitters = new ArrayList<>();

        // Find all emitters ('1' to '6')
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                char ch = grid[r][c];
                if (ch >= '1' && ch <= '6') {
                    emitters.add(new Emitter(r, c, ch - '0'));
                }
            }
        }

        int[][] beamCount = new int[numRows][numCols];

        for (Emitter emitter : emitters) {
            int dr = 0, dc = 0;
            switch (emitter.direction()) {
                case 1 -> { dr = 0; dc = 2; }
                case 2 -> { dr = 1; dc = 1; }
                case 3 -> { dr = 1; dc = -1; }
                case 4 -> { dr = 0; dc = -2; }
                case 5 -> { dr = -1; dc = -1; }
                case 6 -> { dr = -1; dc = 1; }
            }

            int r = emitter.r() + dr;
            int c = emitter.c() + dc;
            while (r >= 0 && r < numRows && c >= 0 && c < numCols) {
                char ch = grid[r][c];
                if (ch == 'O' || ch == 'M') {
                    beamCount[r][c]++;
                } else {
                    // Hit border ('*' or another emitter) or out of playable area
                    break;
                }
                r += dr;
                c += dc;
            }
        }

        // Apply rules:
        // 2 beams: matter created ('M')
        // 3+ beams: matter destroyed ('O')
        // 0 or 1 beam: unchanged
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                char ch = grid[r][c];
                if (ch == 'O' || ch == 'M') {
                    int count = beamCount[r][c];
                    if (count == 2) {
                        grid[r][c] = 'M';
                    } else if (count >= 3) {
                        grid[r][c] = 'O';
                    }
                }
            }
        }
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

        for (int b = 0; b < n; b++) {
            while (lineIdx < lines.length && lines[lineIdx].trim().isEmpty()) {
                lineIdx++;
            }
            if (lineIdx >= lines.length) break;

            String[] header = lines[lineIdx++].trim().split("\\s+");
            int radius = Integer.parseInt(header[0]);
            int rows = Integer.parseInt(header[1]);
            int cols = Integer.parseInt(header[2]);

            char[][] grid = new char[rows][cols];
            for (int r = 0; r < rows; r++) {
                String rowStr = lines[lineIdx++];
                for (int c = 0; c < cols; c++) {
                    grid[r][c] = c < rowStr.length() ? rowStr.charAt(c) : '-';
                }
            }

            solveBoard(grid, rows, cols);

            for (int r = 0; r < rows; r++) {
                out.append(new String(grid[r])).append("\n");
            }
            out.append("\n");
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
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-3 (2)");

        // Test with example
        Path exampleIn = baseDir.resolve("in_level-3_0-example.txt");
        Path exampleExpected = baseDir.resolve("out_level-3_0-example.txt");
        Path testOut = baseDir.resolve("test_out_example.txt");

        if (Files.exists(exampleIn)) {
            processFile(exampleIn, testOut);
            if (Files.exists(exampleExpected)) {
                String expected = Files.readString(exampleExpected);
                String actual = Files.readString(testOut);
                if (expected.equals(actual)) {
                    System.out.println("Level 3 Example MATCHES EXACTLY!");
                } else {
                    System.err.println("Mismatch with example output!");
                    System.err.println("Expected length: " + expected.length() + ", actual length: " + actual.length());
                }
            }
            Files.deleteIfExists(testOut);
        }

        // Small
        Path smallIn = baseDir.resolve("in_level-3_1-small.txt");
        Path smallOut = baseDir.resolve("out_level-3_1-small.txt");
        if (Files.exists(smallIn)) {
            processFile(smallIn, smallOut);
        }

        // Large
        Path largeIn = baseDir.resolve("in_level-3_2-large.txt");
        Path largeOut = baseDir.resolve("out_level-3_2-large.txt");
        if (Files.exists(largeIn)) {
            processFile(largeIn, largeOut);
        }
    }
}
