package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.PlayerId;

public abstract class GameUnit {

    abstract double getTargetHealth();

    abstract void takeDamage(double damage);

    abstract void takeSplashDamage(double damage);

    abstract PlayerId getPlayer();

    abstract boolean isStructure();
}
