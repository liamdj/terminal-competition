package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;

public interface GameUnit {

    double getTargetHealth();

    Coords getCoords();

    void takeDamage(double damage);

    void takeSplashDamage(double damage);

    PlayerId getPlayer();

    boolean isStructure();
}
