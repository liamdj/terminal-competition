package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.map.MapBounds;

import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Deque;
import java.util.LinkedList;
import java.lang.Comparable;
import java.lang.IllegalArgumentException;

public class Pathfinder {

    private static enum Direction {
        Vertical,
        Horizontal
    }

    private class Node implements Comparable<Node> {
        public final Coords coords;
        public final int dist;
        public final boolean switched;
        public final Direction approach; 
        public final Node parent;

        public Node(Coords coords, int minDist, boolean directionSwitch, Direction approachDirection, Node parent) {
            this.coords = coords;
            this.dist = minDist;
            this.switched = directionSwitch;
            this.approach = approachDirection;
            this.parent = parent;
        }

        public int compareTo(Node other) {
            int cmp = Integer.compare(this.dist, other.dist);
            return (cmp == 0) ? Boolean.compare(other.switched, this.switched) : cmp;
        }
    }
    
    Locationable[][] map;
    
    public Pathfinder(Locationable[][] map) {
       this.map = map;
    }

    public Queue<Coords> getPath(Coords start, int targetEdge) throws IllegalArgumentException {
        int[][] distances = new int[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                boolean open = map[x][y] != null && !map[x][y].hasStructure();
                distances[x][y] = open ? Integer.MAX_VALUE : -1;
            }
        }
        if (distances[start.x][start.y] == -1)
            throw new IllegalArgumentException();

        PriorityQueue<Node> toCheck = new PriorityQueue<Node>();
        Node startNode = new Node(start, manhattan(start, targetEdge), false, Direction.Horizontal, null);
        toCheck.add(startNode);
        distances[start.x][start.y] = 0;

        Node onEdge = null;
        Node deepest = startNode;
        int yBack = (targetEdge == MapBounds.EDGE_TOP_LEFT || targetEdge == MapBounds.EDGE_TOP_RIGHT) ? 0 : MapBounds.BOARD_SIZE - 1;
        int bestDepth = Math.abs(yBack - start.y);

        while (!toCheck.isEmpty() && onEdge == null) {
            Node popped = toCheck.poll();
            int dist = getCoords(popped.coords, distances);

            for (Coords coords : popped.coords.neighbors()) {
                int curDist = getCoords(coords, distances);
                if (curDist != -1 && curDist > dist + 1) {
                    distances[coords.x][coords.y] = dist + 1;
                    Direction approach = (coords.x == popped.coords.x) ? Direction.Vertical : Direction.Horizontal;
                    boolean switched = (approach != popped.approach);

                    // Keep track of nodes reached on target edge
                    int manhat = manhattan(coords, targetEdge);
                    Node node = new Node(coords, dist + 1 + manhat, switched, approach, popped);
                    if (manhat == 0)
                        onEdge = (onEdge == null || node.compareTo(onEdge) < 0) ? node : onEdge;

                    // Keep track of best self-destruct candidate
                    int depth = Math.abs(yBack - coords.y);
                    if (depth > bestDepth || (depth == bestDepth && manhat < manhattan(deepest.coords, targetEdge))) {
                        deepest = node;
                        bestDepth = depth;
                    } 

                    toCheck.add(node);
                }
            }
        }

        // Find path to self-destruct or reach edge
        if (onEdge == null) {
            System.err.println("No path to edge. Aiming for " + deepest.coords);
            return backtracePath(deepest);
        }
        else
            return backtracePath(onEdge);
    }

    private Queue<Coords> backtracePath(Node node) {
        Deque<Coords> path = new LinkedList<Coords>();
        while (node != null) {
            path.addFirst(node.coords);
            node = node.parent;
        }
        // do not including starting location in list
        path.removeFirst();
        return path;
    }

    private int getCoords(Coords coords, int[][] array) {
        if (!MapBounds.inArena(coords))
            return -1;
        else
            return array[coords.x][coords.y];
    }

    private int manhattan(Coords coords, int targetEdge) {
        if (targetEdge == MapBounds.EDGE_BOTTOM_LEFT)
            return coords.x + coords.y - (MapBounds.BOARD_SIZE / 2 - 1);
        if (targetEdge == MapBounds.EDGE_BOTTOM_RIGHT)
            return MapBounds.BOARD_SIZE / 2 - (coords.x - coords.y);
        if (targetEdge == MapBounds.EDGE_TOP_LEFT)
            return MapBounds.BOARD_SIZE / 2 - (coords.y - coords.x);
        if (targetEdge == MapBounds.EDGE_TOP_RIGHT)
            return 3 * MapBounds.BOARD_SIZE / 2 - 1 - (coords.x + coords.y);
        // exception
        return 0;
    }
}