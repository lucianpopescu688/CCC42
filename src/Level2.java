import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Level2 {

    public static void solveBoard(char[][] grid, int numRows, int numCols) {
        int emitterR = -1;
        int emitterC = -1;
        int direction = -1;

        // Find emitter
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                char ch = grid[r][c];
                if (ch >= '1' && ch <= '6') {
                    emitterR = r;
                    emitterC = c;
                    direction = ch - '0';
                    break;
                }
            }
            if (direction != -1) break;
        }

        if (direction == -1) {
            System.err.println("No emitter found!");
            return;
        }

        int dr = 0, dc = 0;
        switch (direction) {
            case 1 -> { dr = 0; dc = 2; }
            case 2 -> { dr = 1; dc = 1; }
            case 3 -> { dr = 1; dc = -1; }
            case 4 -> { dr = 0; dc = -2; }
            case 5 -> { dr = -1; dc = -1; }
            case 6 -> { dr = -1; dc = 1; }
        }

        int r = emitterR + dr;
        int c = emitterC + dc;
        while (r >= 0 && r < numRows && c >= 0 && c < numCols) {
            char ch = grid[r][c];
            if (ch == '*') {
                break;
            }
            if (ch == 'O') {
                grid[r][c] = 'M';
            }
            r += dr;
            c += dc;
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
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-2");

        // Test with example
        Path exampleIn = baseDir.resolve("in_level-2_0-example.txt");
        Path exampleExpected = baseDir.resolve("out_level-2_0-example.txt");
        Path testOut = baseDir.resolve("test_out_example.txt");

        if (Files.exists(exampleIn)) {
            processFile(exampleIn, testOut);
            if (Files.exists(exampleExpected)) {
                String expected = Files.readString(exampleExpected);
                String actual = Files.readString(testOut);
                if (expected.equals(actual)) {
                    System.out.println("Level 2 Example MATCHES EXACTLY!");
                } else {
                    System.err.println("Mismatch with example output!");
                    System.err.println("Expected length: " + expected.length() + ", actual length: " + actual.length());
                }
            }
            Files.deleteIfExists(testOut);
        }

        // Small
        Path smallIn = baseDir.resolve("in_level-2_1-small.txt");
        Path smallOut = baseDir.resolve("out_level-2_1-small.txt");
        if (Files.exists(smallIn)) {
            processFile(smallIn, smallOut);
        }

        // Large
        Path largeIn = baseDir.resolve("in_level-2_2-large.txt");
        Path largeOut = baseDir.resolve("out_level-2_2-large.txt");
        if (Files.exists(largeIn)) {
            processFile(largeIn, largeOut);
        }
    }
}
