package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.action.StructBoard;
import com.c1games.terminal.algo.action.Structure;
import com.c1games.terminal.algo.action.UnitInformationContainer;
import com.c1games.terminal.algo.map.CanSpawn;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

public class Defense {

    private static final double HEALTH_TO_REPLACE_TURRET_RATIO = 0.4;
    private static final double HEALTH_TO_REPLACE_WALL_RATIO = 0.6;
    private static final double CORES_PER_LIFE = 2;
    private static final double TOTAL_CORE_VALUE = 40;
    private static final int MIN_BUILD_HEIGHT = 11;
    private static final int NUM_SPAWN_LOCATIONS = 8;

    public StructBoard board;

    private final GameState move;
    private final UnitInformationContainer unitInfos;
    private Set<Coords> noBuildZone;

    public Defense(StructBoard board, GameState move, UnitInformationContainer unitInfos) {
        this.board = board;
        this.move = move;
        this.unitInfos = unitInfos;
    }

    public void remove(Coords coords) {
        board.setLocation(coords, null);
    }

    public void buildMultiple(List<UnitPlacement> build) {
        for (UnitPlacement placement : build)
            board.build(unitInfos, placement.type, placement.coords);
    }

    public static boolean attemptBuild(GameState move, List<UnitPlacement> build) {
        boolean successful = true;
        for (UnitPlacement placement : build)
            if (placement.type == UnitType.Upgrade) {
                successful = successful && move.attemptUpgrade(placement.coords) > 0;
            } else {
                successful = successful && move.attemptSpawn(placement.coords, placement.type);
            }
        return successful;
    }

    public double coreCost(List<UnitPlacement> build) {
        double cores = 0;
        for (UnitPlacement placement : build)
            if (placement.type == UnitType.Upgrade) {
                Structure struct = board.getLocation(placement.coords);
                UnitType type = struct != null ? struct.type : null;
                // need to find struct earlier in build at coords
                if (struct == null) {
                    for (UnitPlacement before : build)
                        if (before.coords.equals(placement.coords)) {
                            type = before.type;
                            break;
                        }
                }
                if (type == UnitType.Upgrade || (struct != null && struct.upgraded)) {
                    System.err.println("Impossible upgrade attempted");
                    System.err.println(build);
                } else {
                    cores += unitInfos.getCores(type, true);
                }
            } else {
                cores += unitInfos.getCores(placement.type, false);
            }
        return cores;
    }

    private double damageScore(double coresLost, int livesLost) {
        return coresLost + CORES_PER_LIFE * livesLost
                + TOTAL_CORE_VALUE * Math.min(1, livesLost / move.data.p1Stats.integrity);
    }

    // from each edge location using all bits on one unit type
    public List<UnitPlacement> opponentAttacks(double bits) {
        double scoutCost = unitInfos.getInfo(UnitType.Scout).cost2.getAsDouble();
        double demolisherCost = unitInfos.getInfo(UnitType.Demolisher).cost2.getAsDouble();

        List<Coords> edgeCoords = new ArrayList<>();
        for (Coords coords : MapBounds.EDGE_LISTS[MapBounds.EDGE_TOP_LEFT])
            if (board.getLocation(coords) == null)
                edgeCoords.add(coords);
        for (Coords coords : MapBounds.EDGE_LISTS[MapBounds.EDGE_TOP_RIGHT])
            if (board.getLocation(coords) == null)
                edgeCoords.add(coords);

        Collections.shuffle(edgeCoords);
        List<UnitPlacement> attacks = new ArrayList<>();
        for (int i = 0; i < NUM_SPAWN_LOCATIONS && i < edgeCoords.size(); i++)
            attacks.add(new UnitPlacement(edgeCoords.get(i), UnitType.Scout, (int) (bits / scoutCost)));

        Collections.shuffle(edgeCoords);
        for (int i = 0; i < NUM_SPAWN_LOCATIONS; i++)
            attacks.add(new UnitPlacement(edgeCoords.get(i), UnitType.Demolisher, (int) (bits / demolisherCost)));

        return attacks;
    }

    // includes upgrading any destroyed structure,
    // building a wall or turret in front of any destroyed structure,
    // or building a wall or turrent along the path of units that score
    public List<List<UnitPlacement>> buildOptions(List<UnitPlacement> testAttacks, double cores,
            Set<Coords> noBuildZone) {
        this.noBuildZone = noBuildZone;
        Set<Coords> locations = new TreeSet<>();
        for (UnitPlacement placement : testAttacks) {

            ActionSimulator sim = new ActionSimulator(unitInfos, board, move);
            sim.spawnUnits(placement.coords, placement.type, placement.quantity);
            sim.run();

            for (Coords coords : sim.p1LostStructCoords) {
                Coords above = new Coords(coords.x, coords.y + 1);
                Coords left = new Coords(coords.x - 1, coords.y);
                Coords right = new Coords(coords.x + 1, coords.y);
                for (Coords c : List.of(coords, above, left, right)) {
                    if (MapBounds.inArena(c) && c.y < MapBounds.BOARD_SIZE / 2)
                        locations.add(c);
                }
            }

            if (sim.p1LivesLost > 0) {
                for (Coords coords : sim.unitPath) {
                    if (coords.y >= MIN_BUILD_HEIGHT && coords.y < MapBounds.BOARD_SIZE / 2)
                        locations.add(coords);
                }
            }
        }

        List<List<UnitPlacement>> options = new ArrayList<>();
        for (Coords coords : locations) {
            if (cores < 8)
                options.addAll(smallBuilds(coords, noBuildZone));
            else
                options.addAll(mediumBuilds(coords, noBuildZone));
        }
        for (List<UnitPlacement> build : options) {
            for (Iterator<UnitPlacement> itr = build.iterator(); itr.hasNext();) {
                if (noBuildZone.contains(itr.next().coords))
                    itr.remove();
            }
        }
        return options;
    }

    //
    private List<List<UnitPlacement>> smallBuilds(Coords coords, Set<Coords> noBuildZone) {
        Structure struct = board.getLocation(coords);
        List<List<UnitPlacement>> ret = new ArrayList<>();
        if (struct == null) {
            ret.add(horizontalWalls(coords, 3, true));
            ret.add(horizontalWalls(coords, 3, false));
            ret.add(new ArrayList<>(
                    List.of(new UnitPlacement(coords, UnitType.Wall), new UnitPlacement(coords, UnitType.Upgrade))));
            ret.add(new ArrayList<>(List.of(new UnitPlacement(coords, UnitType.Turret))));
        } else if (!struct.upgraded) {
            ret.add(new ArrayList<>(List.of(new UnitPlacement(coords, UnitType.Upgrade))));
        }
        return ret;
    }

    // 8 <= cores
    private List<List<UnitPlacement>> mediumBuilds(Coords coords, Set<Coords> noBuildZone) {
        List<List<UnitPlacement>> ret = new ArrayList<>();
        ret.add(horizontalWalls(coords, 5, true));
        ret.add(horizontalWalls(coords, 5, false));
        ret.add(defendedTurret(coords));
        ret.add(adjacentTurrets(coords));
        ret.add(upgradeAndAbove(coords));
        return ret;
    }

    private List<UnitPlacement> horizontalWalls(Coords coords, int maxDist, boolean leftwards) {
        List<UnitPlacement> walls = new ArrayList<>();
        int start = board.getLocation(coords) == null ? 0 : 1;
        for (int dx = start; dx <= maxDist; dx++) {
            Coords here = leftwards ? new Coords(coords.x - dx, coords.y) : new Coords(coords.x + dx, coords.y);
            if (MapBounds.inArena(here) && board.getLocation(here) == null)
                walls.add(new UnitPlacement(here, UnitType.Wall));
            else
                break;
        }
        return walls;
    }

    private List<UnitPlacement> defendedTurret(Coords coords) {
        List<UnitPlacement> ret = new ArrayList<>();
        if (board.getLocation(coords) == null)
            ret.add(new UnitPlacement(coords, UnitType.Turret));
        Coords above = new Coords(coords.x, coords.y + 1);
        Coords left = new Coords(coords.x - 1, coords.y);
        Coords right = new Coords(coords.x + 1, coords.y);
        for (Coords c : List.of(above, left, right)) {
            if (MapBounds.inArena(c) && c.y < MapBounds.BOARD_SIZE / 2) {
                Structure struct = board.getLocation(c);
                if (struct == null)
                    ret.add(new UnitPlacement(c, UnitType.Wall));
                else if (!struct.upgraded)
                    ret.add(new UnitPlacement(c, UnitType.Upgrade));
            }
        }
        return ret;
    }

    private List<UnitPlacement> adjacentTurrets(Coords coords) {
        List<UnitPlacement> ret = new ArrayList<>();
        if (board.getLocation(coords) == null)
            ret.add(new UnitPlacement(coords, UnitType.Wall));
        Coords left = new Coords(coords.x - 1, coords.y);
        Coords right = new Coords(coords.x + 1, coords.y);
        for (Coords c : List.of(left, right)) {
            if (MapBounds.inArena(c)) {
                Structure struct = board.getLocation(c);
                if (struct == null)
                    ret.add(new UnitPlacement(c, UnitType.Turret));
                else if (struct.type == UnitType.Wall && !struct.upgraded)
                    ret.add(new UnitPlacement(c, UnitType.Upgrade));
            }
        }
        Coords left2 = new Coords(coords.x - 2, coords.y);
        Coords right2 = new Coords(coords.x + 2, coords.y);
        for (Coords c : List.of(left2, right2)) {
            if (MapBounds.inArena(c)) {
                Structure struct = board.getLocation(c);
                if (struct == null)
                    ret.add(new UnitPlacement(c, UnitType.Wall));
                else if (struct.type == UnitType.Wall && !struct.upgraded)
                    ret.add(new UnitPlacement(c, UnitType.Upgrade));
            }
        }
        return ret;
    }

    private List<UnitPlacement> upgradeAndAbove(Coords coords) {
        List<UnitPlacement> ret = new ArrayList<>();
        Structure struct = board.getLocation(coords);
        if (struct == null || struct.type == UnitType.Turret) {
            Coords above = new Coords(coords.x, coords.y + 1);
            if (MapBounds.inArena(above) && above.y < MapBounds.BOARD_SIZE / 2) {
                Structure structAbove = board.getLocation(above);
                if (structAbove == null)
                    ret.add(new UnitPlacement(above, UnitType.Wall));
                else if (!structAbove.upgraded)
                    ret.add(new UnitPlacement(above, UnitType.Upgrade));
            }
        }
        if (struct == null) {
            ret.add(new UnitPlacement(coords, UnitType.Turret));
            ret.add(new UnitPlacement(coords, UnitType.Upgrade));
        } else if (!struct.upgraded)
            ret.add(new UnitPlacement(coords, UnitType.Upgrade));
        return ret;
    }

    public List<Double> attackDamages(List<UnitPlacement> attacks, double turns, List<UnitPlacement> edit) {
        return attackDamages(attacks, Collections.nCopies(attacks.size(), turns), edit);
    }

    public List<Double> attackDamages(List<UnitPlacement> attacks, List<Double> turns, List<UnitPlacement> edit) {
        List<Double> damages = new ArrayList<>();
        for (int i = 0; i < attacks.size(); i++) {
            UnitPlacement attack = attacks.get(i);
            ActionSimulator sim = new ActionSimulator(unitInfos, board, move);
            sim.spawnUnits(attack.coords, attack.type, attack.quantity);
            if (edit != null)
                for (UnitPlacement placement : edit)
                    sim.spawnUnit(placement.coords, placement.type);
            sim.run();
            damages.add(damageScore(sim.p1CoresLost, sim.p1LivesLost) / turns.get(i));
        }
        return damages;
    }
}

// if (spawnable == CanSpawn.NotEnoughResources) {
// return false;
// } else if (spawnable == CanSpawn.UnitAlreadyPresent) {
// double startHealth = there.unitInformation.startHealth.getAsDouble();
// if ((there.type == UnitType.Wall && placement.type == UnitType.Turret)
// || (there.type == UnitType.Turret && there.health / startHealth <=
// HEALTH_TO_REPLACE_TURRET_RATIO)
// || (there.type == UnitType.Wall && there.health / startHealth <=
// HEALTH_TO_REPLACE_WALL_RATIO)) {
// move.attemptRemoveStructure(placement.coords);
// }