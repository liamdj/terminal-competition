package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.SpawnCommand;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.ListIterator;

public class ActionSimulator {

    public final Locationable[][] map;
    public final List<Structure> structures;
    public final List<MobileUnits> mobileUnits;

    private final Config config;
    private final List<Config.UnitInformation> upgradedUnitInfo;

    public int p1LivesLost = 0;
    public int p2LivesLost = 0;
    public double p1CoresLost = 0;
    public double p2CoresLost = 0;

    // public List<Coords> pathOfUnits;
    // public List<Double> unitsHealths;

    // copies structures from another simulator instance
    // for given coords, keeps first in list and ignores others
    public ActionSimulator(Config config, List<Structure> structuresToAdd) {
        this.config = config;
        upgradedUnitInfo = new ArrayList<Config.UnitInformation>();
        for (Config.UnitInformation unitInfo : config.unitInformation) {
            Config.UnitInformation copy = new Config.UnitInformation(unitInfo);
            if (copy.upgrade.isPresent())
                copy.upgrade();
            upgradedUnitInfo.add(copy);
        }

        map = new Locationable[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];

        structures = new LinkedList<Structure>();
        for (Structure struct : structuresToAdd) {
            Config.UnitInformation unitInfo = config.unitInformation.get(struct.type.ordinal());
            if (getLocation(struct.coords) == null && unitInfo.unitCategory.orElse(-1) == GameState.TowerUnitCategory) {
                Structure copyStruct = new Structure(struct.type, struct.health, struct.coords);
                copyStruct.upgraded = struct.upgraded;
                structures.add(copyStruct);
                setLocation(struct.coords, copyStruct);
            }
        }
        mobileUnits = new LinkedList<MobileUnits>();

        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Coords coords = new Coords(x, y);
                if (MapBounds.inArena(coords) && getLocation(coords) == null)
                    setLocation(coords, new MobileUnitsList());
            }
        }
        
    }

    // Use spawn commands to create finalized map 
    public ActionSimulator(GameState move) {
        config = move.config;
        upgradedUnitInfo = new ArrayList<Config.UnitInformation>();
        for (Config.UnitInformation unitInfo : config.unitInformation) {
            Config.UnitInformation copy = new Config.UnitInformation(unitInfo);
            if (copy.upgrade.isPresent())
                copy.upgrade();
            upgradedUnitInfo.add(copy);
        }

        SortedMap<String, Structure> structById = new TreeMap<String, Structure>();
        // Fill map with existing structures
        map = new Locationable[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Coords coords = new Coords(x, y);
                if (MapBounds.inArena(coords)) {
                    List<Unit> list = move.allUnits[x][y];
                    if (!list.isEmpty()) {
                        Unit unit = list.get(0);
                        Structure struct = new Structure(unit.type, unit.health, coords);
                        struct.upgraded = unit.upgraded;
                        setLocation(coords, struct);
                        structById.put(unit.id, struct);
                    }
                    else {
                        setLocation(coords, new MobileUnitsList());
                    }
                }
            }
        }

        structures = new LinkedList<Structure>();
        for (Structure struct : structById.values()) {
            structures.add(struct);
        }
        // Include newly spawned structures
        for (SpawnCommand command : move.buildStack) {
            spawnUnit(new Coords(command.x, command.y), command.type);
        }
        mobileUnits = new LinkedList<MobileUnits>();
    }

    public Locationable getLocation(Coords coords) {
        if (!MapBounds.inArena(coords))
            return null;
        else
            return map[coords.x][coords.y];
    }

    public void setLocation(Coords coords, Locationable put) {
        if (MapBounds.inArena(coords))
            map[coords.x][coords.y] = put;
    }

    public boolean spawnUnit(Coords coords, UnitType type) {
        return spawnUnits(coords, type, 1);
    }

    // Immediately place or remove units ignoring resourse cost
    public boolean spawnUnits(Coords coords, UnitType type, int quantity) {
        if (quantity <= 0)
            return false;
        
        Locationable location = getLocation(coords);
        Config.UnitInformation unitInfo = config.unitInformation.get(type.ordinal());

        if (location == null)
            return false;
        else if (location.hasStructure()) {
            Structure struct = (Structure) location;
            if (type == UnitType.Remove) {
                structures.remove(getLocation(coords));
                setLocation(coords, new MobileUnitsList()); 
            }
            else if (type == UnitType.Upgrade) {
                if (struct.upgraded) 
                    return false;
                struct.upgraded = true;
                double initialHealth = config.unitInformation.get(struct.type.ordinal()).startHealth.getAsDouble();
                double upgradedHealth = upgradedUnitInfo.get(struct.type.ordinal()).startHealth.getAsDouble();
                struct.takeDamage(initialHealth - upgradedHealth);
            }
            else
                return false;
        }
        else if (unitInfo.unitCategory.orElse(-1) == GameState.TowerUnitCategory) {
            if (location.hasMobileUnit())
                return false;
            Structure struct = new Structure(type, unitInfo.startHealth.getAsDouble(), coords);
            setLocation(coords, struct);
            structures.add(struct);
        }
        else if(unitInfo.unitCategory.orElse(-1) == GameState.WalkerUnitCategory) {
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

            MobileUnits unitGroup = new MobileUnits(type, quantity, unitInfo.startHealth.getAsDouble(), coords, targetEdge);
            location.add(unitGroup);
            mobileUnits.add(unitGroup);
        }
        else {
            return false;
        }
        return true;
    }

    // Simulates action phase until completion
    public void run() {

        // pathOfUnits = new ArrayList<Coords>();
        // unitsHealths = new ArrayList<Double>();

        pathfind();
        for (int frame = 0; !mobileUnits.isEmpty(); frame++) {

            // Move mobile units
            for (Iterator<MobileUnits> itr = mobileUnits.iterator(); itr.hasNext(); ) {
                MobileUnits units = itr.next();
                double speed = config.unitInformation.get(units.type.ordinal()).speed.getAsDouble();
                if ((int) (frame * speed) - (int) ((frame - 1) * speed) > 0) {
                    getLocation(units.coords).remove(units);

                    // pathOfUnits.add(units.coords);
                    // unitsHealths.add(units.healths.get(0));

                    if (units.path.isEmpty()) {
                        if (MapBounds.IS_ON_EDGE[units.targetEdge][units.coords.x][units.coords.y]) {
                            if (units.getPlayer() == PlayerId.Player1)
                                p2LivesLost += units.healths.size();
                            else if (units.getPlayer() == PlayerId.Player2)
                                p1LivesLost += units.healths.size();
                            // System.err.println(units.healths.size(); + " units scored at " + units.cords);
                        }
                        else {
                            selfDestruct(units);
                            // System.err.println(units.healths.size(); + " units self-destructed at " + units.cords);
                        }
                        itr.remove();
                    }
                    else {
                        Coords next = units.path.removeFirst();
                        units.coords = next;
                        getLocation(next).add(units);
                    }
                }
            }

            // Add shielding
            for (Structure struct : structures) {
                Config.UnitInformation unitInfo;
                if (struct.upgraded)
                    unitInfo = upgradedUnitInfo.get(struct.type.ordinal());
                else
                    unitInfo = config.unitInformation.get(struct.type.ordinal());
                if (unitInfo.shieldPerUnit.isPresent()) {
                    int yValue = struct.getPlayer() == PlayerId.Player1 ? struct.coords.y : MapBounds.BOARD_SIZE - 1 - struct.coords.y;
                    double shieldAmount = unitInfo.shieldPerUnit.getAsDouble() + unitInfo.shieldBonusPerY.orElse(0) * yValue;
                    for (MobileUnits units : mobileUnits) {
                        if (struct.coords.distance(units.coords) <= unitInfo.shieldRange.getAsDouble() && units.shieldsFrom.add(struct.coords)) {
                            for (ListIterator<Double> itr = units.healths.listIterator(); itr.hasNext(); ) {
                                double health = itr.next();
                                itr.set(health + shieldAmount);
                            }
                        }
                    }
                }
            }

            // Perform attacks 
            for (Structure struct : structures) {
                Config.UnitInformation unitInfo = struct.upgraded ? upgradedUnitInfo.get(struct.type.ordinal()) : config.unitInformation.get(struct.type.ordinal());
                if (unitInfo.attackDamageWalker.isPresent()) {
                    GameUnit target = getMobileTarget(struct.coords, unitInfo.attackRange.getAsDouble(), struct.getPlayer());
                    if (target != null)
                        target.takeDamage(unitInfo.attackDamageWalker.getAsDouble());
                }
            }
            for (MobileUnits units : mobileUnits) {
                Config.UnitInformation unitInfo = config.unitInformation.get(units.type.ordinal());
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
                            Config.UnitInformation targetInfo = struct.upgraded ? upgradedUnitInfo.get(struct.type.ordinal()) : config.unitInformation.get(struct.type.ordinal());
                            double coreValue = Math.min(damage, target.getTargetHealth()) / targetInfo.startHealth.getAsDouble() * targetInfo.cost1.getAsDouble();
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
            for (Iterator<Structure> itr = structures.iterator(); itr.hasNext(); ) {
                Structure struct = itr.next();
                if (struct.health <= 0) {
                    itr.remove();
                    setLocation(struct.coords, new MobileUnitsList());
                    structureRemoved = true;
                }
            }
            for (Iterator<MobileUnits> itr = mobileUnits.iterator(); itr.hasNext(); ) {
                MobileUnits units = itr.next();
                units.removeDead();
                if (units.healths.isEmpty()) {
                    itr.remove();
                    getLocation(units.coords).remove(units);
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
            units.path = pather.getPath(units.coords, units.targetEdge);
        }
    }

    private void selfDestruct(MobileUnits units) {
        Config.UnitInformation unitInfo = config.unitInformation.get(units.type.ordinal());

        int yMovement = (units.getPlayer() == PlayerId.Player1) ? 1 : -1;
        Coords destructAt = new Coords(units.coords.x, units.coords.y + yMovement);
        // config.meechanics not initializated ?
        // float range = config.mechanics.selfDestructRadius;
        double range = 1.5;
        double damage = unitInfo.startHealth.getAsDouble();

        int window = (int) range;
        for (int x = destructAt.x - window; x <= destructAt.x + window; x++) {
            for (int y = destructAt.y - window; y <= destructAt.y + window; y++) {
                Coords here = new Coords(x, y);
                double distance = destructAt.distance(here);
                Locationable location = getLocation(here);

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
    //  (1) Mobile units over Structures
    //  (2) nearest
    //  (3) lowest remaining health
    //  (4) furthest into/towards your side of the arena
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
                Locationable location = getLocation(here);

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
                            if (!targetMobile || targetDistTo > dist || (targetDistTo == dist && targetHealth > health)) {
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

            if (dist < targetDistTo || (dist == targetDistTo && targetHealth > health) || (dist == targetDistTo && targetHealth == health && fartherFromBottom(units.coords, targetUnit.coords, player))) {
                targetUnit = units;
                targetDistTo = dist;
                targetHealth = health;
            }
        }

        return targetUnit;
    }
}