import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Level6 {

    private record Candidate(int firstEmitter, int firstPosition, int secondEmitter, int secondPosition,
                             Set<Level5.Point> cells) {}
    private record Pattern(int radius, int count, List<Level5.Point> cells) {}

    private static List<String> generateProgram(int radius, List<Level5.Point> cells, int count) {
        List<Level5.Point> rail = Level5.buildRail(radius);
        int perimeterLength = rail.size();
        char[][] board = Level5.createEmptyBoard(radius);
        int[] rowSteps = {0, 1, 1, 0, -1, -1};
        int[] colSteps = {2, 1, -1, -2, -1, 1};

        Set<Level5.Point> target = new HashSet<>(cells);
        List<Candidate> batchCandidates = new ArrayList<>();
        List<Candidate> singleCellCandidates = new ArrayList<>();

        List<Set<Level5.Point>[]> raysByPosition = new ArrayList<>(perimeterLength);
        for (int position = 0; position < perimeterLength; position++) {
            @SuppressWarnings("unchecked")
            Set<Level5.Point>[] rays = new Set[6];
            Level5.Point start = rail.get(position);
            for (int emitter = 0; emitter < 6; emitter++) {
                Set<Level5.Point> ray = new HashSet<>();
                int row = start.r() + rowSteps[emitter];
                int col = start.c() + colSteps[emitter];
                while (row >= 0 && row < board.length && col >= 0 && col < board[row].length
                        && (board[row][col] == 'O' || board[row][col] == 'M')) {
                    ray.add(new Level5.Point(row, col));
                    row += rowSteps[emitter];
                    col += colSteps[emitter];
                }
                rays[emitter] = ray;
            }
            raysByPosition.add(rays);
        }

        for (int firstEmitter = 0; firstEmitter < 6; firstEmitter++) {
            for (int secondEmitter = firstEmitter + 1; secondEmitter < 6; secondEmitter++) {
                for (int firstPosition = 0; firstPosition < perimeterLength; firstPosition++) {
                    Set<Level5.Point> firstRay = raysByPosition.get(firstPosition)[firstEmitter];
                    for (int secondPosition = 0; secondPosition < perimeterLength; secondPosition++) {
                        Set<Level5.Point> secondRay = raysByPosition.get(secondPosition)[secondEmitter];
                        Set<Level5.Point> intersections = new HashSet<>();
                        boolean onlyTargetCells = true;
                        for (Level5.Point cell : firstRay) {
                            if (secondRay.contains(cell)) {
                                if (!target.contains(cell)) {
                                    onlyTargetCells = false;
                                    break;
                                }
                                intersections.add(cell);
                            }
                        }
                        if (onlyTargetCells && !intersections.isEmpty()) {
                            batchCandidates.add(new Candidate(firstEmitter, firstPosition, secondEmitter, secondPosition,
                                    Set.copyOf(intersections)));
                            for (Level5.Point cell : intersections) {
                                singleCellCandidates.add(new Candidate(firstEmitter, firstPosition, secondEmitter,
                                        secondPosition, Set.of(cell)));
                            }
                        }
                    }
                }
            }
        }

        List<String> bestProgram = null;
        for (int strategy = 0; strategy < 4; strategy++) {
            for (int idleLookahead = 1; idleLookahead <= 2; idleLookahead++) {
                List<String> batchedProgram = generateUsingCandidates(
                        radius, cells, count, perimeterLength, batchCandidates, strategy, idleLookahead);
                if (bestProgram == null || batchedProgram.size() < bestProgram.size()) {
                    bestProgram = batchedProgram;
                }
            }
        }
        for (int idleLookahead = 1; idleLookahead <= 2; idleLookahead++) {
            List<String> singleCellProgram = generateUsingCandidates(
                    radius, cells, count, perimeterLength, singleCellCandidates, 0, idleLookahead);
            if (singleCellProgram.size() < bestProgram.size()) bestProgram = singleCellProgram;
        }
        return bestProgram;
    }

    private static List<String> generateUsingCandidates(int radius, List<Level5.Point> cells, int count,
                                                         int perimeterLength, List<Candidate> candidates,
                                                         int strategy, int idleLookahead) {
        int[] positions = {5 * radius, 0, radius, 2 * radius, 3 * radius, 4 * radius};
        boolean[] active = new boolean[6];
        List<String> program = new ArrayList<>();

        for (int repetition = 0; repetition < count; repetition++) {
            Set<Level5.Point> remaining = new HashSet<>(cells);
            while (!remaining.isEmpty()) {
                Candidate best = selectCandidate(candidates, remaining, positions, active, perimeterLength, strategy);
                if (best == null) {
                    throw new IllegalStateException("No emitter pair can create remaining target cells " + remaining);
                }

                int firstDistance = circularDistance(positions[best.firstEmitter()], best.firstPosition(), perimeterLength);
                int secondDistance = circularDistance(positions[best.secondEmitter()], best.secondPosition(), perimeterLength);
                int distance = Math.max(firstDistance, secondDistance);
                int firstDirection = movementDirection(positions[best.firstEmitter()], best.firstPosition(), perimeterLength);
                int secondDirection = movementDirection(positions[best.secondEmitter()], best.secondPosition(), perimeterLength);
                Set<Level5.Point> afterBest = new HashSet<>(remaining);
                afterBest.removeAll(best.cells());
                int[] plannedPositions = positions.clone();
                boolean[] plannedActive = active.clone();
                applyTransition(best, plannedPositions, plannedActive, perimeterLength);
                List<Candidate> futureRoute = planFutureRoute(candidates, afterBest, plannedPositions,
                        plannedActive, perimeterLength, strategy, idleLookahead);
                if (distance > 0) {
                    boolean[] wasActive = active.clone();
                    char[] firstMovement = noops();
                    for (int emitter = 0; emitter < active.length; emitter++) {
                        if (active[emitter]) {
                            firstMovement[emitter] = 'T';
                            active[emitter] = false;
                        }
                    }
                    if (!wasActive[best.firstEmitter()] && positions[best.firstEmitter()] != best.firstPosition()) {
                        moveEmitter(firstMovement, positions, best.firstEmitter(), firstDirection, perimeterLength);
                    }
                    if (!wasActive[best.secondEmitter()] && positions[best.secondEmitter()] != best.secondPosition()) {
                        moveEmitter(firstMovement, positions, best.secondEmitter(), secondDirection, perimeterLength);
                    }
                    moveIdleEmittersAlongRoute(futureRoute, best, firstMovement, positions, active, perimeterLength);
                    program.add(format(firstMovement));

                    while (positions[best.firstEmitter()] != best.firstPosition()
                            || positions[best.secondEmitter()] != best.secondPosition()) {
                        char[] movement = noops();
                        if (positions[best.firstEmitter()] != best.firstPosition()) {
                            moveEmitter(movement, positions, best.firstEmitter(), firstDirection, perimeterLength);
                        }
                        if (positions[best.secondEmitter()] != best.secondPosition()) {
                            moveEmitter(movement, positions, best.secondEmitter(), secondDirection, perimeterLength);
                        }
                        moveIdleEmittersAlongRoute(futureRoute, best, movement, positions, active, perimeterLength);
                        program.add(format(movement));
                    }
                }

                char[] fire = noops();
                for (int emitter = 0; emitter < active.length; emitter++) {
                    boolean shouldBeActive = emitter == best.firstEmitter() || emitter == best.secondEmitter();
                    if (active[emitter] != shouldBeActive) {
                        fire[emitter] = 'T';
                        active[emitter] = shouldBeActive;
                    }
                }
                remaining.removeAll(best.cells());
                if (!remaining.isEmpty()) {
                    Candidate next = selectCandidate(candidates, remaining, positions, active, perimeterLength,
                            strategy);
                    if (next != null) {
                        moveIdleEmittersAlongRoute(List.of(next), null, fire, positions, active, perimeterLength);
                    }
                }
                program.add(format(fire));
            }
        }

        return program;
    }

    private static List<Candidate> planFutureRoute(List<Candidate> candidates, Set<Level5.Point> remaining,
                                                   int[] positions, boolean[] active, int perimeterLength,
                                                   int strategy, int idleLookahead) {
        List<Candidate> route = new ArrayList<>(idleLookahead);
        Set<Level5.Point> futureRemaining = new HashSet<>(remaining);
        int[] futurePositions = positions.clone();
        boolean[] futureActive = active.clone();
        for (int step = 0; step < idleLookahead && !futureRemaining.isEmpty(); step++) {
            int selectionStrategy = step == 0 ? strategy : 2;
            Candidate next = selectCandidate(candidates, futureRemaining, futurePositions, futureActive,
                    perimeterLength, selectionStrategy);
            if (next == null) break;
            route.add(next);
            futureRemaining.removeAll(next.cells());
            applyTransition(next, futurePositions, futureActive, perimeterLength);
        }
        return route;
    }

    private static Candidate selectCandidate(List<Candidate> candidates, Set<Level5.Point> remaining,
                                             int[] positions, boolean[] active, int perimeterLength, int strategy) {
        if (strategy == 3) {
            return selectWithLookahead(candidates, remaining, positions, active, perimeterLength);
        }

        Candidate best = null;
        int bestGain = 0;
        int bestCost = Integer.MAX_VALUE;
        for (Candidate candidate : candidates) {
            int gain = 0;
            for (Level5.Point cell : candidate.cells()) {
                if (remaining.contains(cell)) gain++;
            }
            if (gain == 0) continue;

            int cost = transitionCost(candidate, positions, active, perimeterLength);
            boolean better;
            if (best == null) {
                better = true;
            } else if (strategy == 1) {
                better = gain > bestGain || (gain == bestGain && cost < bestCost);
            } else if (strategy == 2) {
                better = cost < bestCost || (cost == bestCost && gain > bestGain);
            } else {
                better = (long) gain * bestCost > (long) bestGain * cost
                        || ((long) gain * bestCost == (long) bestGain * cost && cost < bestCost);
            }
            if (better) {
                best = candidate;
                bestGain = gain;
                bestCost = cost;
            }
        }
        return best;
    }

    private static Candidate selectWithLookahead(List<Candidate> candidates, Set<Level5.Point> remaining,
                                                 int[] positions, boolean[] active, int perimeterLength) {
        List<Candidate> firstChoices = topCandidates(candidates, remaining, positions, active, perimeterLength, 4);
        Candidate bestFirst = null;
        double bestValue = -1;
        int bestCovered = 0;
        int bestCost = Integer.MAX_VALUE;

        for (Candidate first : firstChoices) {
            int[] firstPositions = positions.clone();
            boolean[] firstActive = active.clone();
            Set<Level5.Point> afterFirst = new HashSet<>(remaining);
            int firstGain = removeCovered(first, afterFirst);
            int firstCost = transitionCost(first, firstPositions, firstActive, perimeterLength);
            applyTransition(first, firstPositions, firstActive, perimeterLength);

            List<Candidate> secondChoices = topCandidates(
                    candidates, afterFirst, firstPositions, firstActive, perimeterLength, 4);
            if (secondChoices.isEmpty()) {
                double value = (double) firstGain / firstCost;
                if (value > bestValue) {
                    bestFirst = first;
                    bestValue = value;
                    bestCovered = firstGain;
                    bestCost = firstCost;
                }
                continue;
            }

            for (Candidate second : secondChoices) {
                int[] secondPositions = firstPositions.clone();
                boolean[] secondActive = firstActive.clone();
                Set<Level5.Point> afterSecond = new HashSet<>(afterFirst);
                int secondGain = removeCovered(second, afterSecond);
                int secondCost = transitionCost(second, secondPositions, secondActive, perimeterLength);
                applyTransition(second, secondPositions, secondActive, perimeterLength);

                List<Candidate> thirdChoices = topCandidates(
                        candidates, afterSecond, secondPositions, secondActive, perimeterLength, 4);
                int thirdGain = 0;
                int thirdCost = 0;
                if (!thirdChoices.isEmpty()) {
                    Candidate third = thirdChoices.get(0);
                    thirdGain = gain(third, afterSecond);
                    thirdCost = transitionCost(third, secondPositions, secondActive, perimeterLength);
                }

                int covered = firstGain + secondGain + thirdGain;
                int cost = firstCost + secondCost + thirdCost;
                double value = (double) covered / cost;
                if (value > bestValue || (value == bestValue && covered > bestCovered)
                        || (value == bestValue && covered == bestCovered && cost < bestCost)) {
                    bestFirst = first;
                    bestValue = value;
                    bestCovered = covered;
                    bestCost = cost;
                }
            }
        }
        return bestFirst;
    }

    private static List<Candidate> topCandidates(List<Candidate> candidates, Set<Level5.Point> remaining,
                                                int[] positions, boolean[] active, int perimeterLength, int limit) {
        List<Candidate> result = new ArrayList<>(limit);
        List<Double> values = new ArrayList<>(limit);
        for (Candidate candidate : candidates) {
            int candidateGain = gain(candidate, remaining);
            if (candidateGain == 0) continue;
            double value = (double) candidateGain
                    / transitionCost(candidate, positions, active, perimeterLength);
            int insertion = 0;
            while (insertion < values.size() && values.get(insertion) >= value) insertion++;
            if (insertion >= limit) continue;
            result.add(insertion, candidate);
            values.add(insertion, value);
            if (result.size() > limit) {
                result.remove(limit);
                values.remove(limit);
            }
        }
        return result;
    }

    private static int gain(Candidate candidate, Set<Level5.Point> remaining) {
        int result = 0;
        for (Level5.Point cell : candidate.cells()) {
            if (remaining.contains(cell)) result++;
        }
        return result;
    }

    private static int removeCovered(Candidate candidate, Set<Level5.Point> remaining) {
        int before = remaining.size();
        remaining.removeAll(candidate.cells());
        return before - remaining.size();
    }

    private static void applyTransition(Candidate candidate, int[] positions, boolean[] active, int perimeterLength) {
        int[] emitters = {candidate.firstEmitter(), candidate.secondEmitter()};
        int[] destinations = {candidate.firstPosition(), candidate.secondPosition()};
        int[] directions = new int[2];
        for (int i = 0; i < emitters.length; i++) {
            directions[i] = movementDirection(positions[emitters[i]], destinations[i], perimeterLength);
        }

        boolean[] wasActive = active.clone();
        int[] distances = {
                circularDistance(positions[emitters[0]], destinations[0], perimeterLength),
                circularDistance(positions[emitters[1]], destinations[1], perimeterLength)
        };
        int activeMoves = Math.max(wasActive[emitters[0]] ? distances[0] : 0,
                wasActive[emitters[1]] ? distances[1] : 0);
        int inactiveMoves = Math.max(wasActive[emitters[0]] ? 0 : distances[0],
                wasActive[emitters[1]] ? 0 : distances[1]);
        if (activeMoves + inactiveMoves > 0) {
            for (int emitter = 0; emitter < active.length; emitter++) active[emitter] = false;
            for (int i = 0; i < emitters.length; i++) {
                int emitter = emitters[i];
                if (!wasActive[emitter]
                        && circularDistance(positions[emitter], destinations[i], perimeterLength) > 0) {
                    positions[emitter] = (positions[emitter] + directions[i] + perimeterLength) % perimeterLength;
                }
            }
            int movementTicks = Math.max(activeMoves, Math.max(0, inactiveMoves - 1));
            for (int step = 0; step < movementTicks; step++) {
                for (int i = 0; i < emitters.length; i++) {
                    int emitter = emitters[i];
                    if (positions[emitter] != destinations[i]) {
                        positions[emitter] = (positions[emitter] + directions[i] + perimeterLength) % perimeterLength;
                    }
                }
            }
        }
        for (int emitter = 0; emitter < active.length; emitter++) {
            active[emitter] = emitter == candidate.firstEmitter() || emitter == candidate.secondEmitter();
        }
    }

    private static void moveIdleEmittersAlongRoute(List<Candidate> route, Candidate current, char[] instruction,
                                                   int[] positions, boolean[] active, int perimeterLength) {
        for (int emitter = 0; emitter < active.length; emitter++) {
            if (active[emitter] || instruction[emitter] != '-') continue;
            for (Candidate candidate : route) {
                if (current != null
                        && (emitter == current.firstEmitter() || emitter == current.secondEmitter())) break;
                int destination = candidate.firstEmitter() == emitter ? candidate.firstPosition()
                        : candidate.secondEmitter() == emitter ? candidate.secondPosition() : -1;
                if (destination < 0 || destination == positions[emitter]) continue;
                int direction = movementDirection(positions[emitter], destination, perimeterLength);
                moveEmitter(instruction, positions, emitter, direction, perimeterLength);
                break;
            }
        }
    }

    private static int transitionCost(Candidate candidate, int[] positions, boolean[] active, int perimeterLength) {
        int[] emitters = {candidate.firstEmitter(), candidate.secondEmitter()};
        int[] destinations = {candidate.firstPosition(), candidate.secondPosition()};
        int[] distances = new int[2];
        int activeMoves = 0;
        int inactiveMoves = 0;
        boolean anyActive = false;
        for (int i = 0; i < emitters.length; i++) {
            int emitter = emitters[i];
            distances[i] = circularDistance(positions[emitter], destinations[i], perimeterLength);
            if (active[emitter]) {
                anyActive = true;
                activeMoves = Math.max(activeMoves, distances[i]);
            } else {
                inactiveMoves = Math.max(inactiveMoves, distances[i]);
            }
        }
        int movementTicks;
        if (!anyActive) {
            movementTicks = Math.max(activeMoves, inactiveMoves);
        } else {
            movementTicks = 1 + Math.max(activeMoves, Math.max(0, inactiveMoves - 1));
        }
        return movementTicks + 1;
    }

    private static int circularDistance(int from, int to, int perimeterLength) {
        int forward = (to - from + perimeterLength) % perimeterLength;
        int backward = (from - to + perimeterLength) % perimeterLength;
        return Math.min(forward, backward);
    }

    private static int movementDirection(int from, int to, int perimeterLength) {
        if (from == to) return 0;
        int forward = (to - from + perimeterLength) % perimeterLength;
        int backward = (from - to + perimeterLength) % perimeterLength;
        return forward <= backward ? 1 : -1;
    }

    private static void moveEmitter(char[] instruction, int[] positions, int emitter, int direction, int perimeterLength) {
        if (direction == 0) return;
        instruction[emitter] = direction > 0 ? 'F' : 'B';
        positions[emitter] = (positions[emitter] + direction + perimeterLength) % perimeterLength;
    }

    private static char[] noops() {
        char[] instruction = new char[6];
        Arrays.fill(instruction, '-');
        return instruction;
    }

    private static String format(char[] instruction) {
        return instruction[0] + " " + instruction[1] + " " + instruction[2] + " "
                + instruction[3] + " " + instruction[4] + " " + instruction[5];
    }

    private static List<Pattern> parsePatterns(String content) {
        String[] lines = content.split("\\R");
        int index = skipBlankLines(lines, 0);
        if (index >= lines.length) throw new IllegalArgumentException("Input is empty");

        int patternCount = Integer.parseInt(lines[index++].trim());
        List<Pattern> patterns = new ArrayList<>(patternCount);
        for (int patternIndex = 0; patternIndex < patternCount; patternIndex++) {
            index = skipBlankLines(lines, index);
            if (index >= lines.length) throw new IllegalArgumentException("Missing pattern header " + patternIndex);

            String[] header = lines[index++].trim().split("\\s+");
            if (header.length != 4) throw new IllegalArgumentException("Pattern header must contain four numbers");
            int radius = Integer.parseInt(header[0]);
            int rows = Integer.parseInt(header[1]);
            int columns = Integer.parseInt(header[2]);
            int count = Integer.parseInt(header[3]);
            if (rows != 2 * radius + 1 || columns != 4 * radius + 1) {
                throw new IllegalArgumentException("Pattern dimensions do not match radius " + radius);
            }

            List<Level5.Point> cells = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                if (index >= lines.length) throw new IllegalArgumentException("Pattern grid is incomplete");
                String gridRow = lines[index++];
                if (gridRow.length() != columns) throw new IllegalArgumentException("Invalid pattern row width");
                for (int column = 0; column < columns; column++) {
                    if (gridRow.charAt(column) == 'M') cells.add(new Level5.Point(row, column));
                }
            }
            if (cells.isEmpty()) throw new IllegalArgumentException("A pattern must contain at least one matter cell");
            patterns.add(new Pattern(radius, count, cells));
        }
        return patterns;
    }

    private static int skipBlankLines(String[] lines, int index) {
        while (index < lines.length && lines[index].trim().isEmpty()) index++;
        return index;
    }

    public static String solve(String input) {
        List<Pattern> patterns = parsePatterns(input);
        StringBuilder output = new StringBuilder();
        for (int patternIndex = 0; patternIndex < patterns.size(); patternIndex++) {
            Pattern pattern = patterns.get(patternIndex);
            List<String> program = generateProgram(pattern.radius(), pattern.cells(), pattern.count());

            int submitted = Level5.solveProgram(pattern.radius(), pattern.cells(), program.size(), program);
            if (submitted != pattern.count()) {
                throw new IllegalStateException("Generated program submitted pattern " + submitted
                        + " times instead of " + pattern.count());
            }

            if (patternIndex > 0) output.append('\n');
            for (String line : program) output.append(line).append('\n');
        }
        return output.toString();
    }

    public static void processFile(Path inputPath, Path outputPath) throws IOException {
        String result = solve(Files.readString(inputPath));
        Files.writeString(outputPath, result);
        System.out.println("Processed " + inputPath.getFileName() + " -> " + outputPath.getFileName());
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: java Level6 <input-file> <output-file>");
        }
        processFile(Paths.get(args[0]), Paths.get(args[1]));
    }
}
