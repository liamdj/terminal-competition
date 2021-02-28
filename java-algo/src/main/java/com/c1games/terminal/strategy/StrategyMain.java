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
import com.c1games.terminal.algo.map.CanSpawn;
import com.c1games.terminal.algo.units.UnitType;
import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.action.Pathfinder;
import com.c1games.terminal.algo.action.Structure;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Scanner;

/**
 * 
 */
public class StrategyMain implements GameLoop {
    public static void main(String[] args) {
        new GameLoopDriver(new StrategyMain()).run();
    }

    private class UnitPlacement {
        public Coords coords;
        public UnitType type;
        public int quantity;

        public UnitPlacement(Coords coords, UnitType type) {
            this.coords = coords;
            this.type = type;
            this.quantity = 1;
        }

        public UnitPlacement(Coords coords, UnitType type, int quantity) {
            this.coords = coords;
            this.type = type;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return type.toString() + ' ' + coords.toString();
        }
    }

    private class Attack {
        public List<UnitPlacement> spawns;
        public int turnsToWait;
        public int supportRegion;
        public int[] pathingIndices;
        public boolean needClearRightCorner;
        public boolean needClearLeftCorner;

        public Attack(int turnsToWait, int supportRegion, int[] pathingIndices) {
            spawns = new ArrayList<UnitPlacement>();
            this.turnsToWait = turnsToWait;
            this.supportRegion = supportRegion;
            this.pathingIndices = pathingIndices;
            needClearRightCorner = false;
            needClearLeftCorner = false;
        }

        public boolean add(UnitPlacement placement) {
            return spawns.add(placement);
        }

        public boolean addAll(List<UnitPlacement> placements) {
            return spawns.addAll(placements);
        }
    }

    public static final int UPPER_LEFT = 0;
    public static final int LOWER_LEFT = 1;
    public static final int LOWER_RIGHT = 2;
    public static final int UPPER_RIGHT = 3;
    public static final int TOP_LEFT = 4;
    public static final int TOP_RIGHT = 5;
    public static final int OTHER = 6;
    private static final int[] REGIONS = new int[] { LOWER_RIGHT, LOWER_LEFT, UPPER_RIGHT, UPPER_LEFT };

    // fraction of health at which turrets are removed (to be replaced)
    private static final double HEALTH_TO_REPLACE_TURRET_RATIO = 0.75;
    // fraced of health at which walls are removed (to be replaced)
    private static final double HEALTH_TO_REPLACE_WALL_RATIO = 0.75;

    private static final double ALLOC_DECAY = 0.03;
    private static final double ALLOC_PER_DEAD_TURRET = 5;
    private static final double ALLOC_PER_SCORE = 4;
    private static final double ALLOC_SCORED_ON = 12;
    private static final double ALLOC_MIN_PER_BIT = 1.25;
    private static final double ALLOC_BASE_MIDDLE = 12;
    private static final double ALLOC_BASE_CORNER = 6;

    // fraction of cores of overdefended area removed per turn
    private static final double OVERDEFENDED_REMOVAL_RATIO = 0.33;
    // fraction of cores used for offensive when attacking this turn
    private static final double OFFENSIVE_CORES_RATIO = 0.75;
    // fraction of reduction of allocated cores when attacking next turn
    private static final double REDUCE_ALLOC_ON_ATTACK_RATIO = 0.25;

    private static List<UnitPlacement> basicStructures;
    // indexed by region, then defense level
    private static List<List<List<UnitPlacement>>> defensePlacements;
    private static List<List<Double>> defenseCosts;
    private static List<List<Coords>> supportCoords;
    private static List<List<Coords>> pathingCoords;
    private static List<Coords> rightCornerPath;
    private static List<Coords> leftCornerPath;

    private double[] buildAllocations = new double[REGIONS.length];
    private double[] coresInRegion = new double[REGIONS.length];

    private List<Coords> scoredOnLocations = new ArrayList<Coords>();
    private List<Coords> deadTurretLocations = new ArrayList<Coords>();

    private List<Structure> p2PrevStructures = new ArrayList<Structure>();
    private List<Structure> p2PrevPrevStructures = new ArrayList<Structure>();

    private int turnLastAttacked = -1;

    private static final List<Coords> leftCornerChannel = List.of(new Coords(2, 13), new Coords(3, 12), new Coords(4, 11));
    private static final List<Coords> rightCornerChannel = List.of(new Coords(25, 13), new Coords(24, 12), new Coords(23, 11));

    private boolean leftCornerClear; 
    private boolean rightCornerClear; 


    private List<List<UnitPlacement>> readBaseFile(String fileName) throws Exception {
        File f = new File(fileName);
        Scanner scan = new Scanner(f);

        List<List<UnitPlacement>> result = new ArrayList<List<UnitPlacement>>();
        List<UnitPlacement> list = new ArrayList<UnitPlacement>();
        result.add(list);
        while (scan.hasNextLine()) {
            String line = scan.nextLine();
            if (line.isEmpty()) {
                list = new ArrayList<UnitPlacement>();
                result.add(list);
            } else {
                UnitType type;
                char c = line.charAt(0);
                if (c == 'T') 
                    type = UnitType.Turret;
                else if (c == 'W') 
                    type = UnitType.Wall;
                else if (c == 'U')
                    type = UnitType.Upgrade;
                else if (c == 'S')
                    type = UnitType.Support;
                else
                    throw new Exception("Error parsing base setup file");

                for (String loc : line.substring(2).split(" ")) {
                    String[] coords = loc.split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    list.add(new UnitPlacement(new Coords(x, y), type));
                }
            }
        }
        return result;
    }

    private List<UnitPlacement> mirroredPlacements(List<UnitPlacement> input) {
        List<UnitPlacement> output = new ArrayList<UnitPlacement>();
        for (UnitPlacement placement : input) {
            Coords coords = new Coords(MapBounds.BOARD_SIZE - 1 - placement.coords.x, placement.coords.y);
            output.add(new UnitPlacement(coords, placement.type, placement.quantity));
        }
        return output;
    }

    private List<Coords> extractCoords(List<UnitPlacement> placements) {
        List<Coords> coordsList = new ArrayList<Coords>();
        for (UnitPlacement place : placements) {
            coordsList.add(place.coords);
        }
        return coordsList;
    }

    private double totalCores(Config config, List<UnitPlacement> placements) {
        double sum = 0.01;
        for (UnitPlacement place : placements) {
            Config.UnitInformation unitInfo = config.unitInformation.get(place.type.ordinal());
            if (unitInfo.cost1.isPresent())
                sum += unitInfo.cost1.getAsDouble();
        }
        return sum;
    }

    @Override
    public void initialize(GameIO io, Config config) {
        // read structure locations from files
        GameIO.debug().println("Beginning Algo");
        List<List<UnitPlacement>> rightBasics;
        List<List<UnitPlacement>> upperRight;
        List<List<UnitPlacement>> lowerRight;
        List<List<UnitPlacement>> rightSupports;
        List<List<UnitPlacement>> rightPathings;
        try {
            rightBasics = readBaseFile("base_setup/right_basic_defenses.txt");
            upperRight = readBaseFile("base_setup/right_corner_defenses.txt");
            lowerRight = readBaseFile("base_setup/right_middle_defenses.txt");
            rightSupports = readBaseFile("base_setup/right_supports.txt");
            rightPathings = readBaseFile("base_setup/right_pathing_walls.txt");
        } catch (Exception e) {
            GameIO.debug().println(e.getMessage());
            rightBasics = new ArrayList<List<UnitPlacement>>();
            upperRight = new ArrayList<List<UnitPlacement>>();
            lowerRight = new ArrayList<List<UnitPlacement>>();
            rightSupports = new ArrayList<List<UnitPlacement>>();
            rightPathings = new ArrayList<List<UnitPlacement>>();
        }
        List<UnitPlacement> leftBasics = mirroredPlacements(rightBasics.get(0));
        basicStructures = rightBasics.get(0);
        basicStructures.addAll(leftBasics);

        List<List<UnitPlacement>> upperLeft = new ArrayList<List<UnitPlacement>>();
        for (List<UnitPlacement> placements : upperRight) 
            upperLeft.add(mirroredPlacements(placements));
        List<List<UnitPlacement>> lowerLeft = new ArrayList<List<UnitPlacement>>();
        for (List<UnitPlacement> placements : lowerRight) 
            lowerLeft.add(mirroredPlacements(placements));
        defensePlacements = new ArrayList<List<List<UnitPlacement>>>();
        defensePlacements.add(upperLeft);
        defensePlacements.add(lowerLeft);
        defensePlacements.add(lowerRight);
        defensePlacements.add(upperRight);

        List<Double> upperCosts = new ArrayList<Double>();
        for (List<UnitPlacement> placements : upperRight)
            upperCosts.add(totalCores(config, placements));
        List<Double> lowerCosts = new ArrayList<Double>();
        for (List<UnitPlacement> placements : lowerRight)
            lowerCosts.add(totalCores(config, placements));
        defenseCosts = new ArrayList<List<Double>>();
        defenseCosts.add(upperCosts);
        defenseCosts.add(lowerCosts);
        defenseCosts.add(lowerCosts);
        defenseCosts.add(upperCosts);

        supportCoords = new ArrayList<List<Coords>>();
        supportCoords.add(new ArrayList<Coords>());
        supportCoords.add(extractCoords(mirroredPlacements(rightSupports.get(0))));
        supportCoords.add(extractCoords(rightSupports.get(0)));
        supportCoords.add(new ArrayList<Coords>());

        pathingCoords = new ArrayList<List<Coords>>();
        pathingCoords.add(extractCoords(mirroredPlacements(rightPathings.get(0))));
        pathingCoords.add(extractCoords(mirroredPlacements(rightPathings.get(1))));
        pathingCoords.add(extractCoords(rightPathings.get(1)));
        pathingCoords.add(extractCoords(rightPathings.get(0)));
        List<Coords> destructorPathingLeft = new ArrayList<Coords>();
        for (int x = 5; x <= 12; x++)
            destructorPathingLeft.add(new Coords(x, 13));
        List<Coords> destructorPathingRight = new ArrayList<Coords>();
        for (int x = 22; x >= 15; x--)
            destructorPathingRight.add(new Coords(x, 13));
        pathingCoords.add(destructorPathingLeft);
        pathingCoords.add(destructorPathingRight);

        rightCornerPath = new ArrayList<Coords>();
        for (int x = 26; x >= 21; x--) {
            rightCornerPath.add(new Coords(x, x - 13));
            rightCornerPath.add(new Coords(x, x - 14));
        }
        leftCornerPath = new ArrayList<Coords>();
        for (int x = 1; x <= 6; x++) {
            leftCornerPath.add(new Coords(x, 14 - x));
            leftCornerPath.add(new Coords(x, 13 - x));
        }
    }

    /**
     * Make a move in the game.
     */
    @Override
    public void onTurn(GameIO io, GameState move) {
        GameIO.debug().println("Performing turn " + move.data.turnInfo.turnNumber);

        // ensures basic defenses are completed first
        buildBasicStructures(move);

        updateCoresInRegion(move);
        updateBuildAllocations();
        updateCornersClear(move);

        // consider budget for attacking next turn
        double[] reductionBudget = defenseBudgeting(move, 1, true);
        // estimate extra cores from removals if attacking next turn
        double coresSaved = 0;
        for (double value : reductionBudget)
            coresSaved -= value;

        Attack attack = null;
        if (move.data.turnInfo.turnNumber != 0)
            attack = testAttacks(move, attacksToConsider(move, 6), move.data.p1Stats.cores * OFFENSIVE_CORES_RATIO, coresSaved);
        // GameIO.debug().println("turns to wait: " + attack.turnsToWait);

        if (attack != null && attack.turnsToWait == 0) {
            double[] constrainedBudget = defenseBudgeting(move, 1 - OFFENSIVE_CORES_RATIO, false);
            updateDefenses(move, constrainedBudget, attack.needClearRightCorner, attack.needClearLeftCorner);
            sendAttack(move, attack);
        }
        // prepare for attack next turn
        else if (attack != null && attack.turnsToWait == 1) {
            updateDefenses(move, reductionBudget);

            if (attack.needClearRightCorner) {
                for (Coords coords : rightCornerPath)
                    move.attemptRemoveStructure(coords);
            }
            if (attack.needClearLeftCorner) {
                for (Coords coords : leftCornerPath)
                    move.attemptRemoveStructure(coords);
            }
        }
        // assume not attacking this turn or next
        else {
            double[] standardBudget = defenseBudgeting(move, 1, false);
            // GameIO.debug().println("budget: ");
            // for (double d : standardBudget)
            //     GameIO.debug().println(d + " ");
            updateDefenses(move, standardBudget);
        }
    }

    /**
     * Save process action frames. Careful there are many action frames per turn!
     */
    @Override
    public void onActionFrame(GameIO io, GameState move) {

        // Save enemy base set up to test attacks
        if (move.data.turnInfo.actionPhaseFrameNumber == 0) {
            p2PrevPrevStructures = p2PrevStructures;
            p2PrevStructures = structuresList(move, PlayerId.Player2);
        }

        // Save locations that the enemy scored on against us to build up defenses
        for (FrameData.Events.BreachEvent breach : move.data.events.breach) {
            if (breach.unitOwner != PlayerId.Player1) {
                scoredOnLocations.add(breach.coords);
            }
        }
        // Save locations where a turrent was destroyed to build up defenses
        for (FrameData.Events.DeathEvent death : move.data.events.death) {
            if (death.unitOwner == PlayerId.Player1 && death.destroyedUnitType == UnitType.Turret) {
                deadTurretLocations.add(death.coords);
            }
        }
    }

    private static boolean listContains(List<Coords> list, Coords coords) {
        for (Coords item : list)
            if (item.equals(coords))
                return true;
        return false;
    }

    // Player.Error does both
    private List<Structure> structuresList(GameState move, PlayerId player) {
        ActionSimulator sim = new ActionSimulator(move);
        List<Structure> list = sim.structures;
        if (player == PlayerId.Player1) {
            for (Iterator<Structure> itr = list.iterator(); itr.hasNext(); ) {
                Structure struct = itr.next();
                if (struct.coords.y >= MapBounds.BOARD_SIZE / 2)
                    itr.remove();
            }
        }
        else if (player == PlayerId.Player2) {
            for (Iterator<Structure> itr = list.iterator(); itr.hasNext(); ) {
                Structure struct = itr.next();
                if (struct.coords.y < MapBounds.BOARD_SIZE / 2)
                    itr.remove();
            }
        }
        return list;
    }

    private void updateCornersClear(GameState move) {
        rightCornerClear = true;
        for (Coords coords : rightCornerPath) {
            if (move.getWallAt(coords) != null) {
                rightCornerClear = false;
                break;
            }
        }

        leftCornerClear = true;
        for (Coords coords : leftCornerPath) {
            if (move.getWallAt(coords) != null) {
                leftCornerClear = false;
                break;
            }
        }
    }

    private boolean buildBasicStructures(GameState move) {
        for (UnitPlacement placement : basicStructures) {
            if (!attemptBuild(move, placement)) {
                return false;
            }
        } 
        return true;
    }

    // Returns false if cannot afford unit and true otherwise
    private boolean attemptBuild(GameState move, UnitPlacement placement) {
        CanSpawn spawnable = move.canSpawn(placement.coords, placement.type, 1);
        if (spawnable.affirmative()) {
            if (placement.type == UnitType.Upgrade) {
                Unit there = move.getWallAt(placement.coords);
                double startHealth = there.unitInformation.startHealth.getAsDouble();
                if ((there.type == UnitType.Turret && there.health / startHealth > HEALTH_TO_REPLACE_TURRET_RATIO)
                        || (there.type == UnitType.Wall && there.health / startHealth > HEALTH_TO_REPLACE_WALL_RATIO)) {
                    move.attemptUpgrade(placement.coords);
                } else {
                    move.attemptRemoveStructure(placement.coords);
                }
            } else {
                move.attemptSpawn(placement.coords, placement.type);
            }
        } else if (spawnable == CanSpawn.NotEnoughResources) {
            return false;
        } else if (spawnable == CanSpawn.UnitAlreadyPresent) {
            Unit there = move.getWallAt(placement.coords);
            double startHealth = there.unitInformation.startHealth.getAsDouble();
            if (there.type != placement.type || 
                    (there.type == UnitType.Turret && there.health / startHealth <= HEALTH_TO_REPLACE_TURRET_RATIO) ||
                    (there.type == UnitType.Wall && there.health / startHealth <= HEALTH_TO_REPLACE_WALL_RATIO) ) {
                move.attemptRemoveStructure(placement.coords);
            }
        }
        return true;
    }

    private int coresToLevel(int region, double cores) {
        List<Double> costs = defenseCosts.get(region);
        int level;
        for (level = 0; level < costs.size() - 1 && cores > costs.get(level); level++)
            ;
        return level;
    }

    private void updateDefenses(GameState move, double[] coreBudget) {
        updateDefenses(move, coreBudget, false, false);
    }

    private void updateDefenses(GameState move, double[] coreBudget, boolean keepRightCornerClear, boolean keepLeftCornerClear) {
        float myCores = move.data.p1Stats.cores;
        for (int r : REGIONS) {
            if (coreBudget[r] > 0) {
                // to avoid overspending budget, change my current cores
                move.data.p1Stats.cores = (float) coreBudget[r];
                int level = coresToLevel(r, coreBudget[r] + coresInRegion[r]);
                for (UnitPlacement placement : defensePlacements.get(r).get(level)) {
                    if ((!keepRightCornerClear || !StrategyMain.listContains(rightCornerPath, placement.coords)) 
                        && (!keepLeftCornerClear || !StrategyMain.listContains(leftCornerPath, placement.coords))) {
                        if (!attemptBuild(move, placement)) 
                            break; // done with building in this region
                    }
                }
                // update my actual cores based on cores spent
                myCores -= (float) coreBudget[r] - move.data.p1Stats.cores;
            }
            // if budget is negative, remove some defenses
            else {
                double coresToRemove = -1 * coreBudget[r] * OVERDEFENDED_REMOVAL_RATIO;
                List<UnitPlacement> list = defensePlacements.get(r).get(defensePlacements.get(r).size() - 1);
                // attempt to remove unupgraded structures first
                for (int i = list.size() - 1; i >= 0 && coresToRemove > 0; i--) {
                    UnitPlacement placement = list.get(i);
                    if (placement.type != UnitType.Upgrade) {
                        if (r == LOWER_LEFT && StrategyMain.listContains(leftCornerChannel, placement.coords))
                            continue;
                        if (r == LOWER_RIGHT && StrategyMain.listContains(rightCornerChannel, placement.coords))
                            continue;
                        Unit unit = move.getWallAt(placement.coords);
                        if (unit != null && !unit.upgraded) {
                            double cost = unit.unitInformation.cost1.getAsDouble();
                            coresToRemove -= cost * move.attemptRemoveStructure(placement.coords);
                        }
                    }
                }
                // remove upgraded structures if there is nothing unupgraded to remove
                for (int i = list.size() - 1; i >= 0 && coresToRemove > 0; i--) {
                    UnitPlacement placement = list.get(i);
                    if (placement.type != UnitType.Upgrade) {
                        if (r == LOWER_LEFT && StrategyMain.listContains(leftCornerChannel, placement.coords))
                            continue;
                        if (r == LOWER_RIGHT && StrategyMain.listContains(rightCornerChannel, placement.coords))
                            continue;
                        Unit unit = move.getWallAt(placement.coords);
                        if (unit != null) {
                            double cost = unit.unitInformation.cost1.getAsDouble();
                            coresToRemove -= cost * move.attemptRemoveStructure(placement.coords);
                        }
                    }
                }
            }
        }
        move.data.p1Stats.cores = myCores;
    }

    // determine number of defense cores on board in each region
    private void updateCoresInRegion(GameState move) {
        for (int r = 0; r < coresInRegion.length; r++)
            coresInRegion[r] = 0;

        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE / 2; y++) {
                Coords coords = new Coords(x, y);
                int reg = coordsToRegion(coords);
                Unit structure = move.getWallAt(coords);
                if (structure != null && reg != OTHER) {
                    Config.UnitInformation info = structure.unitInformation;
                    coresInRegion[reg] += structure.health / info.startHealth.getAsDouble() * info.cost1.getAsDouble();
                }
            }
        }
    }

    // checks if enemy units are directed away from corner
    private boolean cornerChannelBuilt(GameState move, boolean rightSide) {
        if (rightSide) {
            for (Coords coords : rightCornerChannel)
                if (move.getWallAt(coords) == null)
                    return false;
        }
        else {
            for (Coords coords : leftCornerChannel)
                if (move.getWallAt(coords) == null)
                    return false;
        }
        return true;
    }

    private double[] defenseBudgeting(GameState move, double fractionAvailable, boolean reduceAllocation) {
       
        // find goal defense cores per region
        double[] budget = new double[REGIONS.length];
        for (int i = 0; i < budget.length; i++) {
            budget[i] = (i == UPPER_LEFT || i == UPPER_RIGHT) ? ALLOC_BASE_CORNER : ALLOC_BASE_MIDDLE;
            budget[i] += Math.max(buildAllocations[i], move.data.p2Stats.bits * ALLOC_MIN_PER_BIT);
            // severly clamp budget when attacking next turn
            if (reduceAllocation)
                budget[i] *= 1 - REDUCE_ALLOC_ON_ATTACK_RATIO;
            // budget can be no higher than total defenses possible
            budget[i] = Math.min(defenseCosts.get(i).get(defenseCosts.get(i).size() - 1), budget[i]);
        }
        if (!canAttackCorner(move, PlayerId.Player2, true) && cornerChannelBuilt(move, true))
            budget[UPPER_RIGHT] = 0;
        if (!canAttackCorner(move, PlayerId.Player2, false) && cornerChannelBuilt(move, false))
            budget[UPPER_LEFT] = 0;

        for (int i = 0; i < budget.length; i++)
            budget[i] -= coresInRegion[i];

        double coresNeeded = 0;
        for (double cores : budget)
            coresNeeded += Math.max(0, cores);

        // if there is not enough cores to fill allocation, adjust budget
        double ratio = coresNeeded / (move.data.p1Stats.cores * fractionAvailable);
        if (ratio > 1)
            for (int i = 0; i < budget.length; i++)
                if (budget[i] > 0)
                    budget[i] /= ratio;
        
        return budget;
    }

    private int coordsToRegion(Coords coords) {
        if (coords.x + coords.y <= 14 && coords.y >= 11)
            return UPPER_LEFT;
        else if (coords.x <= 9)
            return LOWER_LEFT;
        else if (coords.x - coords.y >= 13 && coords.y >= 11)
            return UPPER_RIGHT;
        else if (coords.x >= 18)
            return LOWER_RIGHT;
        else
            return OTHER;
    }

    // changes allocated cores to each side based on damage recieved last turn
    private void updateBuildAllocations() {
        for (Coords coords : deadTurretLocations) {
            int reg = coordsToRegion(coords);
            if (reg != OTHER)
                buildAllocations[reg] += ALLOC_PER_DEAD_TURRET;
        }

        boolean[] scoredOn = new boolean[REGIONS.length];
        for (Coords coords : scoredOnLocations) {
            if (coords.x <= 1) {
                buildAllocations[UPPER_LEFT] += ALLOC_PER_SCORE;
                scoredOn[UPPER_LEFT] = true;
            }
            else if (coords.x <= 3) {
                buildAllocations[UPPER_LEFT] += 0.5  * ALLOC_PER_SCORE;
                scoredOn[UPPER_LEFT] = true;
                buildAllocations[LOWER_LEFT] += 0.5  * ALLOC_PER_SCORE;
                scoredOn[LOWER_LEFT] = true;
            }
            else if (coords.x <= 9) {
                buildAllocations[LOWER_LEFT] += ALLOC_PER_SCORE;
                scoredOn[LOWER_LEFT] = true;
            }
            else if (coords.x <= 13) {
                buildAllocations[LOWER_RIGHT] += ALLOC_PER_SCORE;
                scoredOn[LOWER_RIGHT] = true;
            }
            else if (coords.x <= 17) {
                buildAllocations[LOWER_LEFT] += ALLOC_PER_SCORE;
                scoredOn[LOWER_LEFT] = true;
            }
            else if (coords.x <= 23) {
                buildAllocations[LOWER_RIGHT] += ALLOC_PER_SCORE;
                scoredOn[LOWER_RIGHT] = true;
            }
            else if (coords.x <= 25) {
                buildAllocations[UPPER_RIGHT] += 0.5  * ALLOC_PER_SCORE;
                scoredOn[UPPER_RIGHT] = true;
                buildAllocations[LOWER_RIGHT] += 0.5  * ALLOC_PER_SCORE;
                scoredOn[LOWER_RIGHT] = true;
            }
            else {
                buildAllocations[UPPER_RIGHT] += ALLOC_PER_SCORE;
                scoredOn[UPPER_RIGHT] = true;
            }
        }

        for (int reg : REGIONS) {
            // increase priority for regions scored on
            if (scoredOn[reg])
                buildAllocations[reg] += ALLOC_SCORED_ON;

            buildAllocations[reg] *= 1 - ALLOC_DECAY;
        }

        scoredOnLocations.clear();
        deadTurretLocations.clear();
    }

    private double nextBits(double currentBits, int turnNumber) {
        return 0.75 * currentBits + 4 + (int) (turnNumber / 10);
    }

    private void sendAttack(GameState move, Attack attack) {

        for (UnitPlacement placement : attack.spawns) {
            for (int n = 0; n < placement.quantity; n++) {
                boolean success = move.attemptSpawn(placement.coords, placement.type);
                if (success && move.isStructure(placement.type)) {
                    move.attemptRemoveStructure(placement.coords);
                }
            }
        }

        turnLastAttacked = move.data.turnInfo.turnNumber;
    }

    private List<Attack> attacksToConsider(GameState move, int lookAhead) {
        // Find all scout and all demolisher attacks with current setup for this turn and next lookAhead
        int turnNumber = move.data.turnInfo.turnNumber;
        double cores = (double) move.data.p1Stats.cores;

        Map<Integer, Integer> scoutsToTurns = new TreeMap<Integer, Integer>();
        double scoutCost = move.config.unitInformation.get(UnitType.Scout.ordinal()).cost2.getAsDouble();
        Map<Integer, Integer> demolishersToTurns = new TreeMap<Integer, Integer>();
        double demolisherCost = move.config.unitInformation.get(UnitType.Demolisher.ordinal()).cost2.getAsDouble();

        double bits = (double) move.data.p1Stats.bits;
        for (int t = 0; t < lookAhead; t++) {
            scoutsToTurns.putIfAbsent((int) (bits / scoutCost), t);
            demolishersToTurns.putIfAbsent((int) (bits / demolisherCost), t);
            bits = nextBits(bits, turnNumber + t);
        }

        List<Attack> attacks = new ArrayList<Attack>();
        // later: additional pathing options
        for (Map.Entry<Integer, Integer> entry : demolishersToTurns.entrySet()) {
            UnitPlacement leftSpawn = new UnitPlacement(new Coords(11, 2), UnitType.Demolisher, entry.getKey());
            UnitPlacement rightSpawn = new UnitPlacement(new Coords(16, 2), UnitType.Demolisher, entry.getKey());
            
            // try each attack with or without additional pathings
            for (boolean topPath : List.of(true, false)) { 
                int[] pathings;

                pathings = topPath ? new int[] { LOWER_RIGHT, TOP_LEFT} : new int[] { LOWER_RIGHT };
                Attack rightUp = new Attack(entry.getValue(), LOWER_LEFT, pathings);
                rightUp.add(leftSpawn);
                attacks.add(rightUp);

                pathings = topPath ? new int[] { UPPER_LEFT, TOP_RIGHT } : new int[] { UPPER_LEFT };             
                Attack rightDown = new Attack(entry.getValue(), LOWER_RIGHT, pathings);
                rightDown.add(leftSpawn);
                attacks.add(rightDown);
                
                pathings = topPath ? new int[] { LOWER_LEFT, TOP_RIGHT } : new int[] { LOWER_LEFT };
                Attack leftUp = new Attack(entry.getValue(), LOWER_RIGHT, pathings); 
                leftUp.add(rightSpawn);
                attacks.add(leftUp);

                pathings = topPath ? new int[] { UPPER_RIGHT, TOP_LEFT } : new int[] { UPPER_RIGHT };
                Attack leftDown = new Attack(entry.getValue(), LOWER_LEFT, pathings);
                leftDown.add(rightSpawn);
                attacks.add(leftDown);
            }
        }

        for (Map.Entry<Integer, Integer> entry : scoutsToTurns.entrySet()) {

            Attack rightOpen = new Attack(entry.getValue(), LOWER_RIGHT, new int[] { LOWER_LEFT });
            rightOpen.add(new UnitPlacement(new Coords(13, 0), UnitType.Scout, entry.getKey()));
            attacks.add(rightOpen);

            Attack leftOpen = new Attack(entry.getValue(), LOWER_LEFT, new int[] { LOWER_RIGHT });
            leftOpen.add(new UnitPlacement(new Coords(14, 0), UnitType.Scout, entry.getKey()));
            attacks.add(leftOpen);

            for (int num = 0; num <= entry.getKey(); num++) {
                UnitPlacement leftSpawn = new UnitPlacement(new Coords(13, 0), UnitType.Scout, num);
                UnitPlacement rightSpawn = new UnitPlacement(new Coords(14, 0), UnitType.Scout, entry.getKey() - num);

                if (entry.getValue() != 0 || rightCornerClear) {
                    Attack rightClosed = new Attack(entry.getValue(), LOWER_RIGHT, new int[] { LOWER_LEFT, UPPER_RIGHT });
                    rightClosed.needClearRightCorner = true;
                    rightClosed.add(leftSpawn);
                    rightClosed.add(rightSpawn);
                    attacks.add(rightClosed);
                }

                if (entry.getValue() != 0 || leftCornerClear) {
                    Attack leftClosed = new Attack(entry.getValue(), LOWER_LEFT, new int[] { LOWER_RIGHT, UPPER_LEFT });
                    leftClosed.needClearLeftCorner = true;
                    leftClosed.add(leftSpawn);
                    leftClosed.add(rightSpawn);
                    attacks.add(leftClosed);
                }
            }
        }

        return attacks;

        // List<Integer>[] scoutBlockersToTry = new List<Integer>[] {
        //     new ArrayList(new int[] { LOWER_LEFT }),
        //     new ArrayList(new int[] { LOWER_LEFT, UPPER_RIGHT }),
        //     new ArrayList(new int[] { LOWER_RIGHT }),
        //     new ArrayList(new int[] { LOWER_RIGHT, UPPER_LEFT })
        // }
    }

    private Attack testAttacks(GameState move, List<Attack> attacksToTry, double coresAvailable, double coresNextTurn) {

        // create base set ups to test against from opponent's previous turns
        List<Structure> currentStructs = structuresList(move, PlayerId.Error);
        List<Structure> mergedStructs1 = new ArrayList();
        mergedStructs1.addAll(currentStructs);
        mergedStructs1.addAll(p2PrevPrevStructures);
        List<Structure> mergedStructs2 = new ArrayList();
        mergedStructs2.addAll(currentStructs);
        mergedStructs2.addAll(p2PrevStructures);

        int sinceLastAttack = move.data.turnInfo.turnNumber - turnLastAttacked;
        Attack bestAttack = null;
        double bestValue = 0;
        for (Attack attack : attacksToTry) {
            if (attack.needClearRightCorner && !rightCornerClear)
                continue;
            if (attack.needClearLeftCorner && !leftCornerClear)
                continue;

            double minValue = Double.MAX_VALUE;
            for (List<Structure> structs : List.of(mergedStructs1, mergedStructs2)) {

                ActionSimulator sim = new ActionSimulator(move.config, structs);
                double cores = coresAvailable;
                if (attack.turnsToWait >= 1)
                    cores += coresNextTurn + move.config.resources.coresPerRound * attack.turnsToWait;

                // first place mobile units
                for (UnitPlacement units : attack.spawns) {
                    sim.spawnUnits(units.coords, units.type, units.quantity);
                }

                // remove corner structures if necessary 
                if (attack.needClearRightCorner) {
                    for (Coords coords : rightCornerPath)
                        sim.spawnUnit(coords, UnitType.Remove);
                }
                if (attack.needClearLeftCorner) {
                    for (Coords coords : leftCornerPath)
                        sim.spawnUnit(coords, UnitType.Remove);
                }

                // next place pathing walls
                double wallCost = move.config.unitInformation.get(UnitType.Wall.ordinal()).cost1.getAsDouble();
                for (int index : attack.pathingIndices) {
                    List<Coords> walls = pathingCoords.get(index);
                    for (int i = 0; i < walls.size() && cores > wallCost; i++) {
                        if (sim.spawnUnit(walls.get(i), UnitType.Wall)) {
                            attack.add(new UnitPlacement(walls.get(i), UnitType.Wall));
                            cores -= wallCost;
                        }
                    }
                }

                // finally place supports until cores are gone
                double supportCost = move.config.unitInformation.get(UnitType.Support.ordinal()).cost1.getAsDouble();
                List<Coords> supports = supportCoords.get(attack.supportRegion);
                for (int i = 0; i < supports.size() && cores > supportCost; i++) {
                    if (sim.spawnUnit(supports.get(i), UnitType.Support)) {
                        attack.add(new UnitPlacement(supports.get(i), UnitType.Support));
                        cores -= supportCost;
                    }
                }

                sim.run();
                // keep track of value versus worst set up
                minValue = Math.min(minValue, attackValue(sim.p2CoresLost, sim.p2LivesLost, attack.turnsToWait + sinceLastAttack, move.data.p2Stats.integrity));
            }
            if (minValue > bestValue) {
                bestAttack = attack;
                bestValue = minValue;
                if (attack.turnsToWait > 1)
                    break;
            }
        }

        // GameIO.debug().println("Attack: unit = " + bestAttack.spawns.get(0).type + " turns to wait = " + bestAttack.turnsToWait);
        // GameIO.debug().println("Expected value: " + bestValue);

        return bestAttack;
    }

    private double attackValue(double coresDestroyed, int scores, int turns, float p2Health) {
        if (scores >= p2Health)
            return 30 - turns + 0.5 * (scores - p2Health);
        else 
            return (coresDestroyed + 4 * scores) / turns;
    }

    public boolean canAttackCorner(GameState move, PlayerId player, boolean rightSide) {
        int xTarget = rightSide ? MapBounds.BOARD_SIZE - 2 : 1;
        int yTarget, yBoundry, startEdge;
        if (player == PlayerId.Player1) {
            yTarget = MapBounds.BOARD_SIZE / 2 - 1;
            yBoundry = MapBounds.BOARD_SIZE / 2;
            startEdge = rightSide ? MapBounds.EDGE_BOTTOM_LEFT : MapBounds.EDGE_BOTTOM_RIGHT;
        }
        else {
            yTarget = MapBounds.BOARD_SIZE / 2;
            yBoundry = MapBounds.BOARD_SIZE / 2 - 1;
            startEdge = rightSide ? MapBounds.EDGE_TOP_LEFT : MapBounds.EDGE_TOP_RIGHT;
        }

        ActionSimulator sim = new ActionSimulator(move);
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            sim.spawnUnit(new Coords(x, yBoundry), UnitType.Wall);
        }

        Pathfinder pather = new Pathfinder(sim.map);
        Deque<Coords> path = pather.getPath(new Coords(xTarget, yTarget), startEdge);
        if (path == null || path.isEmpty())
            return false;
        Coords end = path.getLast();
        return MapBounds.IS_ON_EDGE[startEdge][end.x][end.y];
    }


}
