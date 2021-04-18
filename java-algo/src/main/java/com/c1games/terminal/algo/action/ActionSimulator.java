package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.map.SpawnCommand;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.Deque;
import java.util.LinkedList;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.ListIterator;

public class ActionSimulator {

    public final GameMap map;
    public final List<Structure> structures;
    public final List<MobileUnits> mobileUnits;

    private final Config config;

    public int p1LivesLost = 0;
    public int p2LivesLost = 0;
    public double p1CoresLost = 0;
    public double p2CoresLost = 0;
    public Deque<Coords> unitPath = new LinkedList<>();

    // copies structures from another simulator instance
    // for given coords, keeps first in list and ignores others
    public ActionSimulator(Config config, List<GameUnit> unitsToAdd) {
        this.config = config;
        map = new GameMap();

        structures = new LinkedList<>();
        mobileUnits = new LinkedList<>();
        for (GameUnit unit : unitsToAdd) {
            if (unit.isStructure()) {
                Structure oldStruct = (Structure) unit;
                Structure struct = new Structure(oldStruct.type, oldStruct.health, oldStruct.coords);
                struct.upgraded = oldStruct.upgraded;
                map.setLocation(oldStruct.coords, struct);
                structures.add(struct);
            } else {
                Locationable loc = map.getLocation(unit.getCoords());
                MobileUnitsList list = (loc == null) ? new MobileUnitsList() : (MobileUnitsList) loc;
                if (loc == null)
                    map.setLocation(unit.getCoords(), list);
                list.addUnits((MobileUnits) unit);
                mobileUnits.add((MobileUnits) unit);
            }
        }

        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Coords coords = new Coords(x, y);
                if (MapBounds.inArena(coords) && map.getLocation(coords) == null)
                    map.setLocation(coords, new MobileUnitsList());
            }
        }

    }

    // Use spawn commands to create finalized map
    public ActionSimulator(GameState move, boolean immediate) {
        config = move.config;
        map = new GameMap(move, !immediate);

        SortedMap<String, Structure> structById = new TreeMap<>();
        structures = new LinkedList<>();
        mobileUnits = new LinkedList<>();
        // Get existing units
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Locationable loc = map.getLocation(x, y);
                if (loc == null)
                    continue;
                if (loc.hasStructure()) {
                    Unit unit = move.allUnits[x][y].get(0);
                    structById.put(unit.id, (Structure) loc);
                } else if (loc.hasMobileUnit()) {
                    mobileUnits.addAll(((MobileUnitsList) loc).unitsList);
                }
            }
        }

        for (Structure struct : structById.values()) {
            structures.add(struct);
        }
        // Include newly spawned structures
        for (SpawnCommand command : move.buildStack) {
            spawnUnit(new Coords(command.x, command.y), command.type);
        }
    }

    public Config.UnitInformation getInfo(UnitType type, Boolean upgraded) {
        if (upgraded)
            return config.unitInformation.get(type.ordinal()).upgrade.get();
        else
            return config.unitInformation.get(type.ordinal());
    }

    public boolean spawnUnit(Coords coords, UnitType type) {
        return spawnUnits(coords, type, 1);
    }

    // Immediately place or remove units ignoring resource cost
    public boolean spawnUnits(Coords coords, UnitType type, int quantity) {
        if (quantity <= 0)
            return false;

        Locationable location = map.getLocation(coords);
        Config.UnitInformation unitInfo = getInfo(type, false);

        if (location == null)
            return false;
        else if (location.hasStructure()) {
            Structure struct = (Structure) location;
            if (type == UnitType.Remove) {
                structures.remove(location);
                map.setLocation(coords, new MobileUnitsList());
            } else if (type == UnitType.Upgrade) {
                if (struct.upgraded)
                    return false;
                struct.upgraded = true;
                double initialHealth = getInfo(struct.type, false).startHealth.getAsDouble();
                double upgradedHealth = getInfo(struct.type, true).startHealth.getAsDouble();
                struct.takeDamage(initialHealth - upgradedHealth);
            } else
                return false;
        } else if (unitInfo.unitCategory.orElse(-1) == GameState.TowerUnitCategory) {
            if (location.hasMobileUnit())
                return false;
            Structure struct = new Structure(type, unitInfo.startHealth.getAsDouble(), coords);
            map.setLocation(coords, struct);
            structures.add(struct);
        } else if (unitInfo.unitCategory.orElse(-1) == GameState.WalkerUnitCategory) {
            int targetEdge;
            if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_BOTTOM_LEFT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_TOP_RIGHT;
            else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_BOTTOM_RIGHT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_TOP_LEFT;
            else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_TOP_LEFT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_BOTTOM_RIGHT;
            else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_TOP_RIGHT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_BOTTOM_LEFT;
            else
                return false;

            MobileUnitsList list = (MobileUnitsList) location;
            MobileUnits unitGroup = new MobileUnits(type, quantity, unitInfo.startHealth.getAsDouble(), coords,
                    targetEdge);
            list.addUnits(unitGroup);
            mobileUnits.add(unitGroup);
        } else {
            return false;
        }
        return true;
    }

    // Simulates action phase until completion
    public void run() {

        pathfind();
        for (int frame = 0; !mobileUnits.isEmpty(); frame++) {

            // Move mobile units
            unitPath.add(mobileUnits.get(0).coords);
            for (Iterator<MobileUnits> itr = mobileUnits.iterator(); itr.hasNext();) {
                MobileUnits units = itr.next();
                double speed = getInfo(units.type, false).speed.getAsDouble();
                if ((int) (frame * speed) - (int) ((frame - 1) * speed) > 0) {
                    ((MobileUnitsList) map.getLocation(units.coords)).removeUnits(units);
                    if (units.path.isEmpty()) {
                        if (MapBounds.IS_ON_EDGE[units.targetEdge][units.coords.x][units.coords.y]) {
                            if (units.getPlayer() == PlayerId.Player1)
                                p2LivesLost += units.healths.size();
                            else if (units.getPlayer() == PlayerId.Player2)
                                p1LivesLost += units.healths.size();
                        } else {
                            selfDestruct(units);
                        }
                        itr.remove();
                    } else {
                        Coords next = units.path.removeFirst();
                        units.lastDirection = (units.coords.x == next.x) ? Pathfinder.Direction.VERTICAL
                                : Pathfinder.Direction.HORIZONTAL;
                        units.coords = next;
                        ((MobileUnitsList) map.getLocation(next)).addUnits(units);
                    }
                }
            }

            // Add shielding
            for (Structure struct : structures) {
                Config.UnitInformation unitInfo = getInfo(struct.type, struct.upgraded);
                if (unitInfo.shieldPerUnit.isPresent()) {
                    int yValue = struct.getPlayer() == PlayerId.Player1 ? struct.coords.y
                            : MapBounds.BOARD_SIZE - 1 - struct.coords.y;
                    double shieldAmount = unitInfo.shieldPerUnit.getAsDouble()
                            + unitInfo.shieldBonusPerY.orElse(0) * yValue;
                    for (MobileUnits units : mobileUnits) {
                        if (struct.coords.distance(units.coords) <= unitInfo.shieldRange.getAsDouble()
                                && units.shieldsFrom.add(struct.coords)) {
                            for (ListIterator<Double> itr = units.healths.listIterator(); itr.hasNext();) {
                                double health = itr.next();
                                itr.set(health + shieldAmount);
                            }
                        }
                    }
                }
            }

            // Perform attacks
            for (Structure struct : structures) {
                Config.UnitInformation unitInfo = getInfo(struct.type, struct.upgraded);
                if (unitInfo.attackDamageWalker.isPresent()) {
                    GameUnit target = getMobileTarget(struct.coords, unitInfo.attackRange.getAsDouble(),
                            struct.getPlayer());
                    if (target != null)
                        target.takeDamage(unitInfo.attackDamageWalker.getAsDouble());
                }
            }
            for (MobileUnits units : mobileUnits) {
                Config.UnitInformation unitInfo = getInfo(units.type, false);
                boolean canAttackStructures;
                if (unitInfo.attackDamageTower.isPresent())
                    canAttackStructures = true;
                else if (unitInfo.attackDamageWalker.isPresent())
                    canAttackStructures = false;
                else
                    continue;
                for (int i = 0; i < units.healths.size(); i++) {
                    GameUnit target;
                    if (canAttackStructures)
                        target = getAnyTarget(units.coords, unitInfo.attackRange.getAsDouble(), units.getPlayer());
                    else
                        target = getMobileTarget(units.coords, unitInfo.attackRange.getAsDouble(), units.getPlayer());
                    if (target != null) {
                        double damage = unitInfo.attackDamageWalker.getAsDouble();
                        if (target.isStructure()) {
                            Structure struct = (Structure) target;
                            Config.UnitInformation targetInfo = getInfo(struct.type, struct.upgraded);
                            double coreValue = Math.min(damage, target.getTargetHealth())
                                    / targetInfo.startHealth.getAsDouble() * targetInfo.cost1.getAsDouble();
                            if (target.getPlayer() == PlayerId.Player1)
                                p1CoresLost += coreValue;
                            else if (target.getPlayer() == PlayerId.Player2)
                                p2CoresLost += coreValue;
                        }
                        target.takeDamage(damage);
                    }
                }
            }

            // Check death
            boolean structureRemoved = false;
            for (Iterator<Structure> itr = structures.iterator(); itr.hasNext();) {
                Structure struct = itr.next();
                if (struct.health <= 0) {
                    itr.remove();
                    map.setLocation(struct.coords, new MobileUnitsList());
                    structureRemoved = true;
                }
            }
            for (Iterator<MobileUnits> itr = mobileUnits.iterator(); itr.hasNext();) {
                MobileUnits units = itr.next();
                units.removeDead();
                if (units.healths.isEmpty()) {
                    itr.remove();
                    ((MobileUnitsList) map.getLocation(units.coords)).removeUnits(units);
                }
            }

            // If a structure was destroyed, paths may change
            if (structureRemoved)
                pathfind();
        }

    }

    // Recompute and set pathing for mobile units
    private void pathfind() {
        Pathfinder pather = new Pathfinder(map);
        for (MobileUnits units : mobileUnits) {
            units.path = pather.getPath(units.coords, units.targetEdge, units.lastDirection);
        }
    }

    private void selfDestruct(MobileUnits units) {
        Config.UnitInformation unitInfo = config.unitInformation.get(units.type.ordinal());

        int yMovement = (units.getPlayer() == PlayerId.Player1) ? 1 : -1;
        Coords destructAt = new Coords(units.coords.x, units.coords.y + yMovement);
        // config.mechanics not initializated ?
        // float range = config.mechanics.selfDestructRadius;
        double range = 1.5;
        double damage = unitInfo.startHealth.getAsDouble();

        int window = (int) range;
        for (int x = destructAt.x - window; x <= destructAt.x + window; x++) {
            for (int y = destructAt.y - window; y <= destructAt.y + window; y++) {
                Coords here = new Coords(x, y);
                double distance = destructAt.distance(here);
                Locationable location = map.getLocation(here);

                if (distance <= range && location != null) {
                    for (GameUnit u : location.getUnits()) {
                        if (u.getPlayer() != units.getPlayer())
                            u.takeSplashDamage(damage);
                    }
                }
            }
        }
    }

    // Chooses target for a unit based on following priorities:
    // (1) Mobile units over Structures
    // (2) nearest
    // (3) lowest remaining health
    // (4) furthest into/towards your side of the arena
    // Nullable for no valid valid targets
    public GameUnit getAnyTarget(Coords coords, double range, PlayerId player) {
        GameUnit targetUnit = null;
        boolean targetMobile = false;
        double targetDistTo = 2 * range;
        double targetHealth = 1000;

        int window = (int) range;
        // Searchs bottom up for player 1 and top down for player 2
        int[] yValues = new int[window * 2 + 1];
        for (int i = 0; i < yValues.length; i++)
            yValues[i] = (player == PlayerId.Player1) ? (coords.y - window + i) : (coords.y + window - i);
        for (int y : yValues) {
            for (int x = coords.x - window; x <= coords.x + window; x++) {
                Coords here = new Coords(x, y);
                double dist = coords.distance(here);
                Locationable location = map.getLocation(here);

                if (dist > range || location == null)
                    continue;

                // Case for structure target
                if (!targetMobile && targetDistTo >= dist && location.hasStructure()) {
                    GameUnit unit = location.getUnits().get(0);
                    double health = unit.getTargetHealth();
                    if (unit.getPlayer() != player && health > 0)
                        if (targetDistTo > dist || (targetDistTo == dist && targetHealth > health)) {
                            targetUnit = unit;
                            targetMobile = false;
                            targetDistTo = dist;
                            targetHealth = health;
                        }
                }
                // Case for mobile unit target
                else if ((!targetMobile || targetDistTo >= dist) && location.hasMobileUnit()) {
                    for (GameUnit unit : location.getUnits()) {
                        double health = unit.getTargetHealth();
                        if (unit.getPlayer() != player && health > 0)
                            if (!targetMobile || targetDistTo > dist
                                    || (targetDistTo == dist && targetHealth > health)) {
                                targetUnit = unit;
                                targetMobile = true;
                                targetDistTo = dist;
                                targetHealth = health;
                            }
                    }
                }
            }
        }
        return targetUnit;
    }

    private boolean fartherFromBottom(Coords coords1, Coords coords2, PlayerId player) {
        return (player == PlayerId.Player1) ? coords1.y > coords2.y : coords1.y < coords2.y;
    }

    public MobileUnits getMobileTarget(Coords coords, double range, PlayerId player) {
        MobileUnits targetUnit = null;
        double targetDistTo = 2 * range;
        double targetHealth = 1000;

        for (MobileUnits units : mobileUnits) {
            double dist = coords.distance(units.coords);
            double health = units.getTargetHealth();

            if (dist > range || units.getPlayer() == player || health <= 0)
                continue;

            if (dist < targetDistTo || (dist == targetDistTo && targetHealth > health) || (dist == targetDistTo
                    && targetHealth == health && fartherFromBottom(units.coords, targetUnit.coords, player))) {
                targetUnit = units;
                targetDistTo = dist;
                targetHealth = health;
            }
        }

        return targetUnit;
    }
}