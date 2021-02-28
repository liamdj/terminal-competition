package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.map.MapBounds;

import java.util.Deque;
import java.util.PriorityQueue;
import java.util.Deque;
import java.util.LinkedList;
import java.lang.Comparable;

public class Pathfinder {

    private static enum Direction {
        Vertical,
        Horizontal
    }

    private class Node implements Comparable<Node> {
        public final Coords coords;
        public final int dist;
        public final int direct;
        public final int index;
        public final Direction approach; 
        public final Node parent;

        public Node(Coords coords, int minDist, int directionIdealness, int index, Direction approachDirection, Node parent) {
            this.coords = coords;
            this.dist = minDist;
            this.direct = directionIdealness;
            this.index = index;
            this.approach = approachDirection;
            this.parent = parent;
        }

        // earlier in ordering means lower distance, then higher directionIdealness, then higher index
        public int compareTo(Node other) {
            int cmpDist = Integer.compare(this.dist, other.dist);
            int cmpDirect = Integer.compare(other.direct, this.direct);
            int cmpIndex = Integer.compare(other.index, this.index);
            return (cmpDist == 0) ? ((cmpDirect == 0) ? cmpIndex : cmpDirect) : cmpDist;
        }
    }
    
    Locationable[][] map;
    
    public Pathfinder(Locationable[][] map) {
       this.map = map;
    }

    public Deque<Coords> getPath(Coords start, int targetEdge) {
        int[][] distances = new int[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                boolean open = map[x][y] != null && !map[x][y].hasStructure();
                distances[x][y] = open ? Integer.MAX_VALUE : -1;
            }
        }
        if (distances[start.x][start.y] == -1)
            return null;

        int index = 0;
        PriorityQueue<Node> toCheck = new PriorityQueue<Node>();
        Node startNode = new Node(start, manhattan(start, targetEdge), 0, index++, Direction.Horizontal, null);
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
                    int manhat = manhattan(coords, targetEdge);
                    Direction approach = (coords.x == popped.coords.x) ? Direction.Vertical : Direction.Horizontal;
                    int directionIdealness = 0;
                    if (approach != popped.approach) 
                        directionIdealness += 2;
                    if (manhat < manhattan(popped.coords, targetEdge))
                        directionIdealness += 1;

                    // Keep track of nodes reached on target edge
                    Node node = new Node(coords, dist + 1 + manhat, directionIdealness, index++, approach, popped);
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
            // System.err.println("No path to edge. Aiming for " + deepest.coords);
            return backtracePath(deepest);
        }
        else
            return backtracePath(onEdge);
    }

    private Deque<Coords> backtracePath(Node node) {
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