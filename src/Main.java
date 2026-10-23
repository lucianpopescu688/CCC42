import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static String generateBoard(int r) {
        StringBuilder sb = new StringBuilder();
        int totalRows = 2 * r + 1;
        for (int i = 0; i < totalRows; i++) {
            int d = Math.abs(i - r);
            // leading hyphens
            sb.append("-".repeat(d));

            if (d == r) {
                // Top or bottom border
                sb.append("*");
                sb.append("-*".repeat(r));
            } else {
                // Inner row
                int k = 2 * r - 1 - d;
                sb.append("*");
                sb.append("-O".repeat(k));
                sb.append("-*");
            }

            // trailing hyphens
            sb.append("-".repeat(d));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String solve(List<Integer> radii) {
        StringBuilder sb = new StringBuilder();
        for (int r : radii) {
            sb.append(generateBoard(r));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static void processFile(Path inputPath, Path outputPath) throws IOException {
        List<String> lines = Files.readAllLines(inputPath);
        if (lines.isEmpty()) return;
        int n = Integer.parseInt(lines.get(0).trim());
        List<Integer> radii = new ArrayList<>();
        for (int i = 1; i <= n && i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                radii.add(Integer.parseInt(line));
            }
        }
        String result = solve(radii);
        Files.writeString(outputPath, result);
        System.out.println("Processed " + inputPath.getFileName() + " -> " + outputPath.getFileName());
    }

    public static void main(String[] args) throws IOException {
        Path baseDir = Paths.get("d:/CCC42/CCC42/level-1 (1)");

        // Test with example file
        Path exampleIn = baseDir.resolve("in_level-1_0-example.txt");
        Path exampleOutTest = baseDir.resolve("test_out_example.txt");
        if (Files.exists(exampleIn)) {
            processFile(exampleIn, exampleOutTest);
            Path exampleExpected = baseDir.resolve("out_level-1_0-example.txt");
            if (Files.exists(exampleExpected)) {
                String expected = Files.readString(exampleExpected);
                String actual = Files.readString(exampleOutTest);
                if (expected.equals(actual)) {
                    System.out.println("Example MATCHES EXACTLY!");
                } else {
                    System.err.println("Mismatch with example output!");
                }
            }
        }

        // Process small and large
        Path smallIn = baseDir.resolve("in_level-1_1-small.txt");
        Path smallOut = baseDir.resolve("out_level-1_1-small.txt");
        if (Files.exists(smallIn)) {
            processFile(smallIn, smallOut);
        }

        Path largeIn = baseDir.resolve("in_level-1_2-large.txt");
        Path largeOut = baseDir.resolve("out_level-1_2-large.txt");
        if (Files.exists(largeIn)) {
            processFile(largeIn, largeOut);
        }
    }
}
