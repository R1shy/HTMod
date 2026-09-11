package net.rishy.hadestown;

import net.minecraft.core.BlockPos;

import java.util.List;

public final class HTSetHandler {
    private final BlockPos firstSetPos;
    private final int setDistance;
    private final int sceneSpacing;
    private final int setWidth;
    private final int setLength;
    private final int setHeight;
    private final String setDirection;
    private final String sceneDirection;
    private final List<Integer> setsPerScene;
    private final BlockPos stageStartPos;
    private final int totalSets;

    private boolean loaded;
    private int sceneNumber = 1;
    private int localSetNumber = 1;
    private int globalSetNumber = 1;

    public HTSetHandler(HadestownConfig.SetSettings settings) {
        this.firstSetPos = settings.firstSetPos();
        this.setDistance = settings.setDistance();
        this.sceneSpacing = settings.sceneSpacing();
        this.setWidth = settings.setWidth();
        this.setLength = settings.setLength();
        this.setHeight = settings.setHeight();
        this.setDirection = settings.setDirection();
        this.sceneDirection = settings.sceneDirection();
        this.setsPerScene = settings.setsPerScene();
        this.stageStartPos = settings.stageStartPos();
        this.totalSets = setsPerScene.stream().mapToInt(Integer::intValue).sum();
    }

    public int getNumScenes() {
        return setsPerScene.size();
    }

    public int getSceneNumber() {
        return sceneNumber;
    }

    public int getLocalSetNumber() {
        return localSetNumber;
    }

    public int getGlobalSetNumber() {
        return globalSetNumber;
    }

    public BlockPos getStageStartPos() {
        return stageStartPos;
    }

    public void reset() {
        loaded = false;
        sceneNumber = 1;
        localSetNumber = 1;
        globalSetNumber = 1;
    }

    public int getMaxSet(int sceneNumber) {
        if (sceneNumber < 1 || sceneNumber > getNumScenes()) {
            throw new IllegalArgumentException("Scene number must be between 1 and " + getNumScenes() + ".");
        }
        return setsPerScene.get(sceneNumber - 1);
    }

    public Scene getScene(int sceneNumber) {
        int sceneStride = setLength + sceneSpacing;
        BlockPos origin = Scene.move(firstSetPos, (sceneNumber - 1) * sceneStride, sceneDirection);
        return new Scene(
                origin,
                setDistance,
                setWidth,
                setLength,
                setHeight,
                getMaxSet(sceneNumber),
                setDirection,
                sceneDirection
        );
    }

    public BlockPos getCurrentSetStartPos() {
        return getScene(sceneNumber).getSetStartPos(localSetNumber);
    }

    public BlockPos getCurrentSetEndPos() {
        return getScene(sceneNumber).getSetEndPos(localSetNumber);
    }

    public BlockPos getSetStartPos(int x, int y) {
        return getSceneAtCoordinates(x, y).getSetStartPos(x + 1);
    }

    public BlockPos getSetEndPos(int x, int y) {
        return getSceneAtCoordinates(x, y).getSetEndPos(x + 1);
    }

    public void setCurrentSet(int x, int y) {
        getSceneAtCoordinates(x, y);

        sceneNumber = y + 1;
        localSetNumber = x + 1;
        globalSetNumber = x + 1;
        for (int scene = 0; scene < y; scene++) {
            globalSetNumber += setsPerScene.get(scene);
        }
        loaded = true;
    }

    public void advance() {
        if (!loaded) {
            loaded = true;
            sceneNumber = 1;
            localSetNumber = 1;
            globalSetNumber = 1;
            return;
        }

        if (localSetNumber < getMaxSet(sceneNumber)) {
            localSetNumber++;
        } else if (sceneNumber < getNumScenes()) {
            sceneNumber++;
            localSetNumber = 1;
        } else {
            sceneNumber = 1;
            localSetNumber = 1;
        }
        globalSetNumber = globalSetNumber == totalSets ? 1 : globalSetNumber + 1;
    }

    public void reverse() {
        if (!loaded) {
            loaded = true;
            sceneNumber = getNumScenes();
            localSetNumber = getMaxSet(sceneNumber);
            globalSetNumber = totalSets;
            return;
        }

        if (localSetNumber > 1) {
            localSetNumber--;
        } else if (sceneNumber > 1) {
            sceneNumber--;
            localSetNumber = getMaxSet(sceneNumber);
        } else {
            sceneNumber = getNumScenes();
            localSetNumber = getMaxSet(sceneNumber);
        }
        globalSetNumber = globalSetNumber == 1 ? totalSets : globalSetNumber - 1;
    }

    private Scene getSceneAtCoordinates(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Set coordinates must be non-negative.");
        }
        if (y >= getNumScenes()) {
            throw new IllegalArgumentException("Scene at y=" + y + " does not exist.");
        }

        Scene scene = getScene(y + 1);
        if (x >= scene.getNumSets()) {
            throw new IllegalArgumentException("Set at x=" + x + ", y=" + y + " does not exist.");
        }
        return scene;
    }
}
