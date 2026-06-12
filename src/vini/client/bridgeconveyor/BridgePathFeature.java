package vini.client.bridgeconveyor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

import arc.Core;
import arc.Events;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.geom.Point2;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.input.Binding;
import mindustry.input.DesktopInput;
import mindustry.input.InputHandler;
import mindustry.input.PlaceMode;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.DirectionBridge;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.type.Category;
import vini.client.ViniFeature;

public class BridgePathFeature implements ViniFeature {
    private static final int SEARCH_MARGIN = 100;
    private static final int MAX_SEARCH_NODES = 100000;

    private static final int NO_DIRECTION = -1;
    private static final int DIR_UP = 0;
    private static final int DIR_RIGHT = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_DOWN = 3;

    private static final int[][] DIRECTION_DELTAS = {
        {0, 1},
        {1, 0},
        {-1, 0},
        {0, -1}
    };

    private static final Comparator<PathNode> pathPriority = (a, b) -> {
        int result = Integer.compare(a.turns, b.turns);
        if(result != 0) return result;

        result = Integer.compare(a.segments, b.segments);
        if(result != 0) return result;

        result = Integer.compare(a.distance, b.distance);
        if(result != 0) return result;

        return Long.compare(a.order, b.order);
    };

    private Block lastBlock;
    private int lastStartX;
    private int lastStartY;
    private int lastEndX;
    private int lastEndY;
    private int lastRotation;
    private int lastTeamId;
    private boolean lastAvoidTransportBlocks;

    private boolean cacheReady;
    private boolean cachedPathValid;
    private final Seq<BuildPlan> cachedPlans = new Seq<>();

    private final InputProcessor releaseProcessor = new InputProcessor(){
        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button) {
            if(button == KeyCode.mouseLeft){
                applySmartBridgePath();
            }

            return false;
        }
    };

    @Override
    public void init() {
        Events.on(EventType.ClientLoadEvent.class, event -> Core.input.addProcessor(releaseProcessor));
        Events.run(EventType.Trigger.update, this::applySmartBridgePath);
    }

    private void applySmartBridgePath() {
        if(!Vars.state.isGame() || Vars.player == null || Vars.player.dead() || Vars.control == null || Vars.control.input == null){
            resetPathCache();
            return;
        }

        InputHandler input = Vars.control.input;

        if(!(input instanceof DesktopInput)){
            resetPathCache();
            return;
        }

        DesktopInput desktop = (DesktopInput)input;
        Block block = input.block;

        if(block == null || desktop.mode != PlaceMode.placing || desktop.selectX < 0 || desktop.selectY < 0){
            resetPathCache();
            return;
        }

        if(!isBridge(block) || !ctrlBridgeDragActive()){
            resetPathCache();
            return;
        }

        int startX = desktop.selectX;
        int startY = desktop.selectY;
        int endX = mouseTileX(block);
        int endY = mouseTileY(block);
        int rotation = input.rotation;
        int teamId = Vars.player.team().id;
        boolean avoidTransportBlocks = transportBlockCheckEnabled();

        if(cacheMatches(block, startX, startY, endX, endY, rotation, teamId, avoidTransportBlocks)){
            applyCachedPlans(input, block);
            return;
        }

        Seq<Point2> path = findPath(block, startX, startY, endX, endY, avoidTransportBlocks);
        if(path == null || path.size == 0){
            rememberRequest(block, startX, startY, endX, endY, rotation, teamId, avoidTransportBlocks, null);
            applyCachedPlans(input, block);
            return;
        }

        Seq<BuildPlan> plans = buildPlans(block, path, rotation, avoidTransportBlocks);
        if(plans == null || plans.size == 0){
            rememberRequest(block, startX, startY, endX, endY, rotation, teamId, avoidTransportBlocks, null);
            applyCachedPlans(input, block);
            return;
        }

        rememberRequest(block, startX, startY, endX, endY, rotation, teamId, avoidTransportBlocks, plans);
        applyCachedPlans(input, block);
    }

    private boolean cacheMatches(Block block, int startX, int startY, int endX, int endY, int rotation, int teamId, boolean avoidTransportBlocks) {
        return cacheReady &&
            lastBlock == block &&
            lastStartX == startX &&
            lastStartY == startY &&
            lastEndX == endX &&
            lastEndY == endY &&
            lastRotation == rotation &&
            lastTeamId == teamId &&
            lastAvoidTransportBlocks == avoidTransportBlocks;
    }

    private void rememberRequest(Block block, int startX, int startY, int endX, int endY, int rotation, int teamId, boolean avoidTransportBlocks, Seq<BuildPlan> plans) {
        lastBlock = block;
        lastStartX = startX;
        lastStartY = startY;
        lastEndX = endX;
        lastEndY = endY;
        lastRotation = rotation;
        lastTeamId = teamId;
        lastAvoidTransportBlocks = avoidTransportBlocks;
        cacheReady = true;
        cachedPathValid = plans != null && plans.size > 0;
        cachedPlans.clear();

        if(cachedPathValid){
            cachedPlans.addAll(plans);
        }
    }

    private void applyCachedPlans(InputHandler input, Block block) {
        if(!cachedPathValid){
            if(input.linePlans.size > 0){
                input.linePlans.clear();
            }
            return;
        }

        if(linePlansMatch(input.linePlans, cachedPlans)){
            return;
        }

        input.linePlans.set(cachedPlans);
        block.handlePlacementLine(input.linePlans);
    }

    private boolean linePlansMatch(Seq<BuildPlan> current, Seq<BuildPlan> expected) {
        if(current.size != expected.size){
            return false;
        }

        for(int i = 0; i < expected.size; i++){
            BuildPlan a = current.get(i);
            BuildPlan b = expected.get(i);

            if(a.x != b.x || a.y != b.y || a.rotation != b.rotation || a.block != b.block){
                return false;
            }
        }

        return true;
    }

    private void resetPathCache() {
        cacheReady = false;
        cachedPathValid = false;
        cachedPlans.clear();
        lastBlock = null;
    }

    private boolean ctrlBridgeDragActive() {
        return Core.input.keyDown(Binding.diagonalPlacement) || Core.input.ctrl();
    }

    private boolean transportBlockCheckEnabled() {
        return !Core.input.alt();
    }

    private boolean isBridge(Block block) {
        return block instanceof ItemBridge || block instanceof DirectionBridge;
    }

    private int bridgeRange(Block block) {
        if(block instanceof ItemBridge){
            return ((ItemBridge)block).range;
        }

        if(block instanceof DirectionBridge){
            return ((DirectionBridge)block).range;
        }

        return 1;
    }

    private int mouseTileX(Block block) {
        return World.toTile(Core.input.mouseWorld().x - block.offset);
    }

    private int mouseTileY(Block block) {
        return World.toTile(Core.input.mouseWorld().y - block.offset);
    }

    private Seq<Point2> findPath(Block block, int startX, int startY, int endX, int endY, boolean avoidTransportBlocks) {
        if(startX == endX && startY == endY){
            Seq<Point2> single = new Seq<>();
            single.add(new Point2(startX, startY));
            return single;
        }

        HashMap<Long, Boolean> passableCache = new HashMap<>();
        if(!canUserEndpoint(block, startX, startY) || !canUserEndpoint(block, endX, endY)){
            return null;
        }

        int minX = Math.min(startX, endX) - SEARCH_MARGIN;
        int maxX = Math.max(startX, endX) + SEARCH_MARGIN;
        int minY = Math.min(startY, endY) - SEARCH_MARGIN;
        int maxY = Math.max(startY, endY) + SEARCH_MARGIN;

        long order = 0L;
        int searchedNodes = 0;
        int range = Math.max(1, bridgeRange(block));
        PriorityQueue<PathNode> open = new PriorityQueue<>(pathPriority);
        HashMap<Long, PathNode> best = new HashMap<>();
        PathNode start = new PathNode(startX, startY, NO_DIRECTION, 0, 0, 0, order++, null);

        open.add(start);
        best.put(stateKey(startX, startY, NO_DIRECTION), start);

        while(!open.isEmpty() && searchedNodes++ < MAX_SEARCH_NODES){
            PathNode current = open.poll();
            PathNode currentBest = best.get(stateKey(current.x, current.y, current.direction));

            if(currentBest != current){
                continue;
            }

            if(current.x == endX && current.y == endY){
                return reconstructPath(current);
            }

            int[] directions = directionOrder(current.x, current.y, endX, endY);
            for(int i = 0; i < directions.length; i++){
                order = addBridgeCandidates(block, current, directions[i], range, minX, maxX, minY, maxY, endX, endY, avoidTransportBlocks, passableCache, open, best, order);
            }
        }

        return null;
    }

    private long addBridgeCandidates(Block block, PathNode current, int direction, int range, int minX, int maxX, int minY, int maxY, int endX, int endY, boolean avoidTransportBlocks, HashMap<Long, Boolean> passableCache, PriorityQueue<PathNode> open, HashMap<Long, PathNode> best, long order) {
        ArrayList<BridgeCandidate> obstacleJumps = new ArrayList<>();
        ArrayList<BridgeCandidate> normalJumps = new ArrayList<>();
        boolean crossedObstacle = false;

        for(int distance = 1; distance <= range; distance++){
            int x = current.x + DIRECTION_DELTAS[direction][0] * distance;
            int y = current.y + DIRECTION_DELTAS[direction][1] * distance;

            if(x < minX || x > maxX || y < minY || y > maxY){
                break;
            }

            if(distance > 1){
                int middleX = current.x + DIRECTION_DELTAS[direction][0] * (distance - 1);
                int middleY = current.y + DIRECTION_DELTAS[direction][1] * (distance - 1);

                if(!canPathThrough(block, middleX, middleY, !avoidTransportBlocks, passableCache)){
                    crossedObstacle = true;
                }
            }

            if(!canPathThrough(block, x, y, !avoidTransportBlocks || x == endX && y == endY, passableCache)){
                continue;
            }

            BridgeCandidate candidate = new BridgeCandidate(x, y, direction, distance);
            if(crossedObstacle){
                obstacleJumps.add(candidate);
            }else{
                normalJumps.add(candidate);
            }
        }

        order = addCandidates(current, obstacleJumps, open, best, order);
        return addCandidates(current, normalJumps, open, best, order);
    }

    private long addCandidates(PathNode current, ArrayList<BridgeCandidate> candidates, PriorityQueue<PathNode> open, HashMap<Long, PathNode> best, long order) {
        for(int i = candidates.size() - 1; i >= 0; i--){
            BridgeCandidate candidate = candidates.get(i);
            int turns = current.direction == NO_DIRECTION || current.direction == candidate.direction ? current.turns : current.turns + 1;
            int segments = current.segments + 1;
            int distance = current.distance + candidate.distance;
            long key = stateKey(candidate.x, candidate.y, candidate.direction);
            PathNode previousBest = best.get(key);

            if(previousBest != null && isBetterOrEqual(previousBest, turns, segments, distance)){
                continue;
            }

            PathNode next = new PathNode(candidate.x, candidate.y, candidate.direction, turns, segments, distance, order++, current);
            best.put(key, next);
            open.add(next);
        }

        return order;
    }

    private int[] directionOrder(int currentX, int currentY, int endX, int endY) {
        int horizontal = Integer.compare(endX, currentX);
        int vertical = Integer.compare(endY, currentY);
        int preferredHorizontal = horizontal > 0 ? DIR_RIGHT : horizontal < 0 ? DIR_LEFT : NO_DIRECTION;
        int preferredVertical = vertical > 0 ? DIR_UP : vertical < 0 ? DIR_DOWN : NO_DIRECTION;
        int[] result = new int[4];
        int count = 0;

        if(preferredVertical == DIR_UP){
            count = addDirection(result, count, DIR_UP);
        }

        count = addDirection(result, count, preferredHorizontal);

        if(preferredVertical == DIR_DOWN){
            count = addDirection(result, count, DIR_DOWN);
        }

        count = addDirection(result, count, DIR_UP);
        count = addDirection(result, count, DIR_RIGHT);
        count = addDirection(result, count, DIR_LEFT);
        addDirection(result, count, DIR_DOWN);

        return result;
    }

    private int addDirection(int[] directions, int count, int direction) {
        if(direction == NO_DIRECTION){
            return count;
        }

        for(int i = 0; i < count; i++){
            if(directions[i] == direction){
                return count;
            }
        }

        directions[count] = direction;
        return count + 1;
    }

    private boolean isBetterOrEqual(PathNode node, int turns, int segments, int distance) {
        return node.turns < turns || node.turns == turns && (node.segments < segments || node.segments == segments && node.distance <= distance);
    }

    private long stateKey(int x, int y, int direction) {
        return ((long)Point2.pack(x, y) << 3) | (direction + 1L);
    }

    private Seq<Point2> reconstructPath(PathNode end) {
        ArrayList<Point2> reversed = new ArrayList<>();
        PathNode current = end;

        while(current != null){
            reversed.add(new Point2(current.x, current.y));
            current = current.parent;
        }

        Collections.reverse(reversed);

        Seq<Point2> result = new Seq<>();
        for(Point2 point : reversed){
            result.add(point);
        }

        return result;
    }

    private boolean canPathThrough(Block block, int x, int y) {
        return canPathThrough(block, x, y, false);
    }

    private boolean canPathThrough(Block block, int x, int y, boolean allowTransportBlock) {
        return (allowTransportBlock || !hasTransportBlock(x, y)) && canPlaceAt(block, x, y, 0);
    }

    private boolean canPathThrough(Block block, int x, int y, HashMap<Long, Boolean> passableCache) {
        return canPathThrough(block, x, y, false, passableCache);
    }

    private boolean canPathThrough(Block block, int x, int y, boolean allowTransportBlock, HashMap<Long, Boolean> passableCache) {
        long key = ((long)Point2.pack(x, y) << 1) | (allowTransportBlock ? 1L : 0L);
        Boolean cached = passableCache.get(key);

        if(cached != null){
            return cached;
        }

        boolean result = canPathThrough(block, x, y, allowTransportBlock);
        passableCache.put(key, result);
        return result;
    }

    private boolean canUserEndpoint(Block block, int x, int y) {
        return canPathThrough(block, x, y, true);
    }

    private boolean canPlaceAt(Block block, int x, int y, int rotation) {
        return Vars.player != null && Vars.player.team() != null && Build.validPlaceIgnoreUnits(block, Vars.player.team(), x, y, rotation, true, true);
    }

    private boolean hasTransportBlock(int x, int y) {
        Tile tile = Vars.world.tile(x, y);
        Building build = tile == null ? null : tile.build;

        return build != null && build.block != null && build.block.category == Category.distribution;
    }

    private Seq<BuildPlan> buildPlans(Block block, Seq<Point2> nodes, int fallbackRotation, boolean avoidTransportBlocks) {
        Seq<BuildPlan> plans = new Seq<>();

        for(int i = 0; i < nodes.size; i++){
            Point2 node = nodes.get(i);
            int rotation = rotationFor(nodes, i, fallbackRotation);

            boolean userEndpoint = i == 0 || i == nodes.size - 1;
            if((avoidTransportBlocks && !userEndpoint && hasTransportBlock(node.x, node.y)) || !canPlaceAt(block, node.x, node.y, rotation)){
                return null;
            }

            BuildPlan plan = new BuildPlan(node.x, node.y, rotation, block, block.nextConfig());
            plan.animScale = 1f;
            plans.add(plan);
        }

        return plans;
    }

    private int rotationFor(Seq<Point2> nodes, int index, int fallbackRotation) {
        if(index < nodes.size - 1){
            int rotation = Tile.relativeTo(nodes.get(index).x, nodes.get(index).y, nodes.get(index + 1).x, nodes.get(index + 1).y);
            return rotation == -1 ? fallbackRotation : rotation;
        }

        if(index > 0){
            int rotation = Tile.relativeTo(nodes.get(index - 1).x, nodes.get(index - 1).y, nodes.get(index).x, nodes.get(index).y);
            return rotation == -1 ? fallbackRotation : rotation;
        }

        return fallbackRotation;
    }

    private static class BridgeCandidate {
        final int x;
        final int y;
        final int direction;
        final int distance;

        BridgeCandidate(int x, int y, int direction, int distance) {
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.distance = distance;
        }
    }

    private static class PathNode {
        final int x;
        final int y;
        final int direction;
        final int turns;
        final int segments;
        final int distance;
        final long order;
        final PathNode parent;

        PathNode(int x, int y, int direction, int turns, int segments, int distance, long order, PathNode parent) {
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.turns = turns;
            this.segments = segments;
            this.distance = distance;
            this.order = order;
            this.parent = parent;
        }
    }
}