package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.GameIO;
import com.c1games.terminal.algo.FrameData;
import com.c1games.terminal.algo.io.GameLoop;
import com.c1games.terminal.algo.io.GameLoopDriver;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;

import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.action.GameUnit;
import com.c1games.terminal.algo.action.MobileUnits;
import com.c1games.terminal.algo.action.StructBoard;
import com.c1games.terminal.algo.action.Structure;
import com.c1games.terminal.algo.action.UnitInformationContainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Random;

/**
 * 
 */
public class StrategyMain implements GameLoop {
    public static void main(String[] args) {
        new GameLoopDriver(new StrategyMain()).run();
    }

    private static final int TURNS_PER_SUPPORT = 7;

    private static final double HISTORY_DISCOUNT = 0.2;

    private static final double SKIP_BUILD_FREQ = 0.4;
    private static final int OPPONENT_TEST_BASES = 8;

    private static final double MIN_DAMAGE_REDUCT_TO_BUILD = 0.1;
    private static final double MAX_DAMAGE_REDUCT_TO_REMOVE = 0.01;
    private static final int MAX_BUILDS_PER_TURN = 10;
    private static final int MAX_BUILD_OPTIONS = 20;

    private static final int DEFENSE_LOOKAHEAD = 4;
    private static final double DEFENSE_WEIGHT_CURRENT = 0.67;
    private static final int ATTACK_LOOKAHEAD = 5;
    private static final int ATTACK_SPAWN_LOCATIONS = 12;

    private static final List<UnitPlacement> basicStructures = new ArrayList<>();
    private static final List<Coords> supportCoords = new ArrayList<>();
    private static final List<List<Coords>> attackPathings = new ArrayList<>();

    private Map<UnitPlacement, Double> p2StructHistory = new TreeMap<>();
    private List<Coords> p2LastSentInterceptors = new ArrayList<>();

    private Attack savedAttack;
    private int turnLastAttacked = -1;

    private static final Random random = new Random();

    private UnitInformationContainer unitInfos;

    @Override
    public void initialize(GameIO io, Config config) {
        // read structure locations from files
        GameIO.debug().println("Beginning Algo");

        unitInfos = new UnitInformationContainer(config);

        basicStructures.add(new UnitPlacement(new Coords(3, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(24, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(14, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(0, 13), UnitType.Wall));
        basicStructures.add(new UnitPlacement(new Coords(27, 13), UnitType.Wall));
        basicStructures.add(new UnitPlacement(new Coords(8, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(20, 12), UnitType.Turret));

        supportCoords.add(new Coords(13, 11));
        supportCoords.add(new Coords(14, 11));
        supportCoords.add(new Coords(12, 11));
        supportCoords.add(new Coords(15, 11));
        supportCoords.add(new Coords(13, 10));
        supportCoords.add(new Coords(14, 10));
        supportCoords.add(new Coords(12, 10));
        supportCoords.add(new Coords(15, 10));
        supportCoords.add(new Coords(13, 9));
        supportCoords.add(new Coords(14, 9));
        supportCoords.add(new Coords(12, 9));
        supportCoords.add(new Coords(15, 9));
        supportCoords.add(new Coords(13, 8));
        supportCoords.add(new Coords(14, 8));
        supportCoords.add(new Coords(12, 8));
        supportCoords.add(new Coords(15, 8));

        List<Coords> leftCorner = new ArrayList<>();
        leftCorner.add(new Coords(1, 13));
        leftCorner.add(new Coords(1, 12));
        leftCorner.add(new Coords(2, 12));
        leftCorner.add(new Coords(2, 11));
        leftCorner.add(new Coords(3, 11));
        leftCorner.add(new Coords(3, 10));
        leftCorner.add(new Coords(4, 10));
        leftCorner.add(new Coords(4, 9));
        List<Coords> rightCorner = new ArrayList<>();
        rightCorner.add(new Coords(26, 13));
        rightCorner.add(new Coords(26, 12));
        rightCorner.add(new Coords(25, 12));
        rightCorner.add(new Coords(25, 11));
        rightCorner.add(new Coords(24, 11));
        rightCorner.add(new Coords(24, 10));
        rightCorner.add(new Coords(23, 10));
        rightCorner.add(new Coords(23, 9));
        List<Coords> leftSide = List.of(new Coords(3, 13), new Coords(3, 12), new Coords(3, 11), new Coords(3, 10),
                new Coords(4, 10), new Coords(4, 9));
        List<Coords> rightSide = List.of(new Coords(24, 13), new Coords(24, 12), new Coords(24, 11), new Coords(24, 10),
                new Coords(23, 10), new Coords(23, 9));
        List<Coords> middle = List.of(new Coords(13, 13), new Coords(12, 13), new Coords(12, 12), new Coords(11, 12),
                new Coords(11, 11), new Coords(11, 10), new Coords(11, 9));
        attackPathings.add(new ArrayList<>());
        attackPathings.add(leftCorner);
        attackPathings.add(rightCorner);
        attackPathings.add(leftSide);
        attackPathings.add(rightSide);
        attackPathings.add(middle);
    }

    /**
     * Make a move in the game.
     */
    @Override
    public void onTurn(GameIO io, GameState move) {
        GameIO.debug().println("Performing turn " + move.data.turnInfo.turnNumber);

        if (move.data.turnInfo.turnNumber == 0) {
            for (UnitPlacement placement : basicStructures) {
                move.attemptSpawn(placement.coords, placement.type);
            }
        }

        final long time0 = System.currentTimeMillis();

        List<Coords> toRemove = findUselessStructures(move, new StructBoard(move, unitInfos, true));
        for (Coords coords : toRemove) {
            move.attemptRemoveStructure(coords);
        }

        final long time1 = System.currentTimeMillis();
        GameIO.debug().println("Removals: " + ((time1 - time0) / 10) / 100.0);

        Defense defense = new Defense(new StructBoard(move, unitInfos, false), move, unitInfos);
        List<UnitPlacement> currentAttacks = defense.opponentAttacks(move.data.p2Stats.bits);
        double turns = bitsToTurns(move.data.p2Stats.bits, move.data.turnInfo.turnNumber);
        List<Double> weights = defense.attackDamages(currentAttacks, turns, null);
        List<UnitPlacement> build = findBestBuild(move, defense, currentAttacks, weights);
        for (int t = 0; build != null && Defense.attemptBuild(move, build) && t < MAX_BUILDS_PER_TURN; t++) {
            defense.buildMultiple(build);
            build = findBestBuild(move, defense, currentAttacks, weights);
        }

        final long time2 = System.currentTimeMillis();
        GameIO.debug().println("Decide builds: " + ((time2 - time1) / 10) / 100.0);

        final long time3 = System.currentTimeMillis();

        // find first open location for support
        int numSupports = 0;
        for (int i = 0; i < supportCoords.size(); i++) {
            Unit unit = move.getWallAt(supportCoords.get(i));
            if (unit != null && unit.type == UnitType.Support)
                numSupports++;
        }
        int buildableSupports = (int) (move.data.p1Stats.cores / 8);

        Attack bestAttack = new Attack(unitInfos, 1);
        for (int t = 0; t < ATTACK_LOOKAHEAD; t++) {
            // rebuild opponent's missing structures
            List<StructBoard> setups = opponentBasePredictions(new StructBoard(move, unitInfos, t >= 2),
                    move.data.p2Stats.cores + t * move.config.resources.coresPerRound, OPPONENT_TEST_BASES);

            int maxSupports = (move.data.turnInfo.turnNumber + t) / TURNS_PER_SUPPORT;
            // System.err.println("max supports: " + maxSupports + ", built supports: " +
            // numSupports + ", affordable: "
            // + buildableSupports);
            Attack attack = findBestAttack(move, setups, p2LastSentInterceptors,
                    Math.min(maxSupports - numSupports, buildableSupports), t);
            if (attack.damagePerTurn > bestAttack.damagePerTurn)
                bestAttack = attack;
        }

        final long time4 = System.currentTimeMillis();
        GameIO.debug().println("Prediction and decide attack: " + ((time4 - time3) / 10) / 100.0);

        if (bestAttack != null && bestAttack.turnsToWait == 0) {
            bestAttack.send(move);
            turnLastAttacked = move.data.turnInfo.turnNumber;
            savedAttack = null;
        } else if (bestAttack != null && bestAttack.turnsToWait <= 2) {
            for (Coords coords : bestAttack.usedCoords)
                move.attemptRemoveStructure(coords);
            savedAttack = bestAttack;
        } else {
            savedAttack = null;
        }
        GameIO.debug()
                .println("Best attack turns: " + bestAttack.turnsToWait + ", damage: " + bestAttack.damagePerTurn);
    }

    /**
     * Save process action frames. Careful there are many action frames per turn!
     */
    @Override
    public void onActionFrame(GameIO io, GameState move) {

        if (move.data.turnInfo.actionPhaseFrameNumber == 0) {
            // Save enemy base history to predict builds
            // reduce influence of base layout from earlier turns
            for (Map.Entry<UnitPlacement, Double> entry : p2StructHistory.entrySet()) {
                p2StructHistory.put(entry.getKey(), entry.getValue() * (1 - HISTORY_DISCOUNT));
            }
            for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
                for (int y = MapBounds.BOARD_SIZE / 2; y < MapBounds.BOARD_SIZE; y++) {
                    Coords coords = new Coords(x, y);
                    Unit struct = move.getWallAt(coords);
                    if (struct != null) {
                        UnitPlacement placement = new UnitPlacement(coords, struct.type);
                        double value = p2StructHistory.getOrDefault(placement, 0.0);
                        p2StructHistory.put(placement, value + 1);
                        if (struct.upgraded) {
                            UnitPlacement upgradePlacement = new UnitPlacement(coords, UnitType.Upgrade);
                            double val = p2StructHistory.getOrDefault(upgradePlacement, 0.0);
                            p2StructHistory.put(upgradePlacement, val + 1);
                        }
                    }
                }
            }

            // Keep track of enemy sending interceptors
            p2LastSentInterceptors.clear();
            for (FrameData.PlayerUnit unit : move.data.p2Units.interceptor) {
                p2LastSentInterceptors.add(new Coords(unit.x, unit.y));
            }
        }

        // // Add locations where a support was destroyed
        // for (FrameData.Events.DeathEvent death : move.data.events.death) {
        // if (death.unitOwner == PlayerId.Player1 && death.destroyedUnitType ==
        // UnitType.Support) {
        // supportCoords.addFirst(death.coords);
        // }
        // }
    }

    private List<UnitPlacement> findBestBuild(GameState move, Defense defense, List<UnitPlacement> currentAttacks,
            List<Double> currentWeights) {
        // final long startTime = System.currentTimeMillis();

        List<UnitPlacement> nextAttacks = new ArrayList<>();
        List<Double> waitTurns = new ArrayList<>();
        double initialTurns = bitsToTurns(move.data.p2Stats.bits, move.data.turnInfo.turnNumber);
        for (int t = 1; t < DEFENSE_LOOKAHEAD; t++) {
            double bits = nextBits(move.data.p2Stats.bits, move.data.turnInfo.turnNumber, t);
            for (UnitPlacement attack : defense.opponentAttacks(bits)) {
                nextAttacks.add(attack);
                waitTurns.add(initialTurns + t);
            }
        }

        Set<Coords> noBuildZone = savedAttack != null ? savedAttack.usedCoords : new TreeSet<>();
        List<List<UnitPlacement>> options = defense.buildOptions(nextAttacks, move.data.p1Stats.cores, noBuildZone);
        // GameIO.debug().println("num options: " + options.size());
        Collections.shuffle(options);

        List<Double> baseDamagesNow = defense.attackDamages(currentAttacks, initialTurns, null);
        List<Double> baseDamagesFuture = defense.attackDamages(nextAttacks, waitTurns, null);
        double baseDamage = DEFENSE_WEIGHT_CURRENT * weightedSum(baseDamagesNow, currentWeights)
                + (1 - DEFENSE_WEIGHT_CURRENT) * weightedSum(baseDamagesFuture, baseDamagesFuture);

        // GameIO.debug().println("base damage: " + baseDamage);
        List<UnitPlacement> bestPlacement = null;
        double mostDamageReduction = MIN_DAMAGE_REDUCT_TO_BUILD;
        for (int i = 0; i < Math.min(MAX_BUILD_OPTIONS, options.size()); i++) {
            List<UnitPlacement> build = options.get(i);
            if (build.isEmpty())
                continue;

            List<Double> damagesNow = defense.attackDamages(currentAttacks, initialTurns, build);
            List<Double> damagesFuture = defense.attackDamages(nextAttacks, waitTurns, build);
            // using attack weightings based on base before builds
            double damage = DEFENSE_WEIGHT_CURRENT * weightedSum(damagesNow, currentWeights)
                    + (1 - DEFENSE_WEIGHT_CURRENT) * weightedSum(damagesFuture, damagesFuture);
            // GameIO.debug()
            // .println("placement coords: " + build.coords + ", type: " + build.type + ",
            // damage: " + avgDamage);
            double cost = defense.coreCost(build);
            double reduction = (baseDamage - damage) / cost;
            if (reduction > mostDamageReduction) {
                mostDamageReduction = reduction;
                bestPlacement = build;
            }
        }

        // final long endTime = System.currentTimeMillis();
        // GameIO.debug().println("Single build iteration: " + ((endTime - startTime) /
        // 10) / 100.0);

        return bestPlacement;
    }

    private List<Coords> findUselessStructures(GameState move, StructBoard board) {

        Defense defense = new Defense(board, move, unitInfos);
        double bits2turns = nextBits(move.data.p2Stats.bits, move.data.turnInfo.turnNumber, 2);
        List<UnitPlacement> testAttacks = defense.opponentAttacks(bits2turns);

        List<UnitPlacement> removals = new ArrayList<>();
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE / 2; y++) {
                Coords coords = new Coords(x, y);
                Structure struct = defense.board.getLocation(coords);
                if (struct != null && struct.type != UnitType.Support) {
                    removals.add(new UnitPlacement(coords, UnitType.Remove));
                }
            }
        }

        List<Double> baseDamages = defense.attackDamages(testAttacks, 2, null);
        double baseDamage = weightedSum(baseDamages, baseDamages);
        // System.err.println("base damage: " + baseDamage);
        List<Coords> toRemove = new ArrayList<>();
        for (UnitPlacement rem : removals) {
            List<Double> damages = defense.attackDamages(testAttacks, 2, List.of(rem));
            double avgDamage = weightedSum(damages, damages);
            Structure struct = defense.board.getLocation(rem.coords);
            double cost = unitInfos.getInfo(struct.type, struct.upgraded).cost1.getAsDouble();
            if (avgDamage - baseDamage <= MAX_DAMAGE_REDUCT_TO_REMOVE * cost) {
                // System.err.println("remove " + rem.coords + ", damage: " + avgDamage);
                toRemove.add(rem.coords);
                defense.remove(rem.coords);
            }
        }
        return toRemove;
    }

    private List<StructBoard> opponentBasePredictions(StructBoard currentBoard, double cores, int number) {
        // make list of currently missing former opponent structures sorted by frequency
        List<Map.Entry<UnitPlacement, Double>> list = new LinkedList<>(p2StructHistory.entrySet());
        for (Iterator<Map.Entry<UnitPlacement, Double>> itr = list.iterator(); itr.hasNext();) {
            Coords coords = itr.next().getKey().coords;
            if (currentBoard.getLocation(coords) != null)
                itr.remove();
        }
        list.sort(Map.Entry.<UnitPlacement, Double>comparingByValue().reversed());

        List<StructBoard> setups = new ArrayList<>();
        setups.add(currentBoard);
        for (int t = 0; t < number; t++) {
            StructBoard board = new StructBoard(currentBoard);
            setups.add(board);
            List<UnitPlacement> placements = new LinkedList<>();
            for (Map.Entry<UnitPlacement, Double> entry : list)
                placements.add(entry.getKey());

            double coresSpent = 0;
            boolean outOfCores = false;
            while (!placements.isEmpty() && !outOfCores) {
                for (Iterator<UnitPlacement> itr = placements.iterator(); itr.hasNext();) {
                    UnitPlacement place = itr.next();
                    if (random.nextDouble() > SKIP_BUILD_FREQ) {
                        Structure struct = board.getLocation(place.coords);
                        if (place.type == UnitType.Upgrade && struct == null)
                            continue;

                        double cost = place.type == UnitType.Upgrade ? unitInfos.getCores(struct.type, true)
                                : unitInfos.getCores(place.type, false);
                        if (cost + coresSpent > cores)
                            outOfCores = true;
                        else if (board.build(unitInfos, place.type, place.coords)) {
                            coresSpent += cost;
                        }
                        itr.remove();
                        break;
                    }
                }
            }
        }
        return setups;
    }

    // private List<GameUnit> unitsList(GameState move, PlayerId player, boolean
    // immediate) {
    // ActionSimulator sim = new ActionSimulator(move, immediate);
    // List<GameUnit> list = new LinkedList<>();
    // for (Iterator<Structure> itr = sim.structures.iterator(); itr.hasNext();) {
    // GameUnit unit = itr.next();
    // if (player == PlayerId.Error || unit.getPlayer() == player)
    // list.add(unit);
    // }
    // for (Iterator<MobileUnits> itr = sim.mobileUnits.iterator(); itr.hasNext();)
    // {
    // GameUnit unit = itr.next();
    // if (player == PlayerId.Error || unit.getPlayer() == player)
    // list.add(unit);
    // }
    // return list;
    // }

    private double weightedSum(List<Double> values, List<Double> weights) {
        double totalSum = 0;
        double weightSum = 0;
        for (int i = 0; i < values.size(); i++) {
            totalSum += values.get(i) * weights.get(i);
            weightSum += weights.get(i);
        }
        return weightSum > 0 ? totalSum / weightSum : 0;
    }

    private double nextBits(double currentBits, int turnNumber, int turnsAhead) {
        for (int turn = turnNumber; turn < turnNumber + turnsAhead; turn++) {
            currentBits = 0.75 * currentBits + 1 + (int) ((turn + 1) / 5);
        }
        return currentBits;
    }

    private double bitsToTurns(double bits, int turnNumber) {
        int bitsPerTurn = 1 + (turnNumber + 1) / 5;
        return -4 * Math.log(1 - bits / (4 * bitsPerTurn));
    }

    private Attack findBestAttack(GameState move, List<StructBoard> setups, List<Coords> interceptorCoords,
            int numSupports, int turnsAhead) {
        // Find all scout and all demolisher attacks with current setup for this turn
        int turnNumber = move.data.turnInfo.turnNumber;
        int sinceLastAttack = turnNumber - turnLastAttacked;

        double bits = nextBits(move.data.p1Stats.bits, turnNumber, turnsAhead);
        double scoutCost = unitInfos.getInfo(UnitType.Scout).cost2.getAsDouble();
        double demolisherCost = unitInfos.getInfo(UnitType.Demolisher).cost2.getAsDouble();
        int numScouts = (int) (bits / scoutCost);
        int numDemolishers = (int) (bits / demolisherCost);

        Attack bestAttack = new Attack(unitInfos, 1);

        List<Coords> edgeCoords = new ArrayList<>();
        edgeCoords.addAll(Arrays.asList(MapBounds.EDGE_LISTS[MapBounds.EDGE_BOTTOM_LEFT]));
        edgeCoords.addAll(Arrays.asList(MapBounds.EDGE_LISTS[MapBounds.EDGE_BOTTOM_RIGHT]));
        Collections.shuffle(edgeCoords);

        for (int i = 0; i < ATTACK_SPAWN_LOCATIONS; i++) {
            Coords spawnCoords = edgeCoords.get(i);

            for (List<Coords> path : attackPathings) {
                Attack attack = new Attack(unitInfos, turnsAhead);
                attack.add(new UnitPlacement(spawnCoords, UnitType.Demolisher, numDemolishers));
                int leftOver = (int) (bits - demolisherCost * numDemolishers);
                if (leftOver > 0) {
                    attack.add(new UnitPlacement(spawnCoords, UnitType.Interceptor, leftOver));
                }
                for (Coords coords : path) {
                    attack.add(new UnitPlacement(coords, UnitType.Remove));
                }
                for (int j = 0, built = 0; built < numSupports && j < supportCoords.size(); j++) {
                    Coords coords = supportCoords.get(j);
                    if (move.getWallAt(coords) == null) {
                        attack.add(new UnitPlacement(coords, UnitType.Support));
                        attack.add(new UnitPlacement(coords, UnitType.Upgrade));
                        built++;
                    }
                }
                attack.test(move, setups, interceptorCoords, sinceLastAttack);
                if (attack.damagePerTurn > bestAttack.damagePerTurn)
                    bestAttack = attack;
                // need at least 2 turns to remove structures
                if (turnsAhead < 2)
                    break;
            }

            for (List<Coords> path : attackPathings) {
                Attack attack = new Attack(unitInfos, turnsAhead);
                attack.add(new UnitPlacement(spawnCoords, UnitType.Scout, numScouts));
                for (Coords coords : path) {
                    attack.add(new UnitPlacement(coords, UnitType.Remove));
                }
                for (int j = 0, built = 0; built < numSupports && j < supportCoords.size(); j++) {
                    Coords coords = supportCoords.get(j);
                    if (move.getWallAt(coords) == null) {
                        attack.add(new UnitPlacement(coords, UnitType.Support));
                        attack.add(new UnitPlacement(coords, UnitType.Upgrade));
                        built++;
                    }
                }
                attack.test(move, setups, interceptorCoords, sinceLastAttack);
                if (attack.damagePerTurn > bestAttack.damagePerTurn)
                    bestAttack = attack;
                // need at least 2 turns to remove structures
                if (turnsAhead < 2)
                    break;
            }
        }
        return bestAttack;
    }
}
