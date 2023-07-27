/*
 * Derived from Hourglass
 * https://github.com/DuckyCrayfish/hourglass
 * Copyright (C) 2021 Nick Iacullo
 *
 * This file is part of Better Days.
 *
 * Better Days is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Better Days is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Better Days.  If not, see <https://www.gnu.org/licenses/>.
 */

package betterdays.wrappers;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;

import betterdays.BetterDays;
import betterdays.time.SleepStatus;
import betterdays.utils.ReflectionUtil;

/**
 * This class acts as a wrapper for {@link ServerLevel} to increase consistency between Minecraft
 * versions.
 *
 * Since the server-level class changes its name and package between different versions of
 * Minecraft, supporting different Minecraft versions would require modifications to any class that
 * imports or references {@link ServerLevel}. This class consolidates these variations into itself,
 * allowing other classes to depend on it instead.
 */
public class ServerLevelWrapper extends Wrapper<ServerLevel> {

    // Store classes at the top to minimize file changes between Minecraft versions.
    private static final Class<ServerLevel> levelClass = ServerLevel.class;
    private static final Class<ServerLevelData> levelDataClass = ServerLevelData.class;
    private static final Class<DerivedLevelData> derivedLevelDataClass = DerivedLevelData.class;

    /** The level-data of this level. */
    public final ServerLevelData levelData;

    /**
     * Instantiates a new object.
     * @param level  the server level to wrap
     */
    public ServerLevelWrapper(LevelAccessor level) {
        super(levelClass.cast(level));
        this.levelData = levelDataClass.cast(this.get().getLevelData());
    }

    /** {@return true if the 'daylight cycle' game rule is enabled in this level} */
    public boolean daylightRuleEnabled() {
        return this.get().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }

    /** {@return true if the 'weather cycle' game rule is enabled in this level} */
    public boolean weatherRuleEnabled() {
        return this.get().getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
    }

    /**
     * Sets the 'random tick speed' game rule for this level.
     * @param speed  the new random tick speed
     */
    public void setRandomTickSpeed(int speed) {
        this.get().getGameRules().getRule(GameRules.RULE_RANDOMTICKING)
                .set(speed, this.get().getServer());
    }

    /**
     * Convenience method that returns true if the weather cycle is progressing in this level.
     * @return true if the weather cycle is progressing in this level
     */
    public boolean weatherCycleEnabled() {
        return weatherRuleEnabled() && this.get().dimensionType().hasSkyLight();
    }

    /**
     * Emulates the vanilla functionality for stopping weather, as access modifiers prevent calls
     * to the methods that do this in vanilla.
     */
    public void stopWeather() {
        levelData.setRainTime(0);
        levelData.setRaining(false);
        levelData.setThunderTime(0);
        levelData.setThundering(false);
    }

    /**
     * Sets the level sleep status using reflection, as access modifiers prevent this.
     * In Minecraft versions lower than 1.17 this method should do nothing.
     *
     * @param newStatus  the new sleep status
     */
    public void setSleepStatus(SleepStatus newStatus) {
        try {
            Field sleepStatus = ReflectionUtil.findField(ServerLevel.class, "sleepStatus");

            sleepStatus.set(this.get(), newStatus);
        }
        catch (IllegalAccessException | NoSuchFieldException e) {
            BetterDays.LOGGER.warn("Error settings sleep status.", e);
        }
    }

    /**
     * Performs vanilla morning wakeup functionality to wake up all sleeping players.
     */
    public void wakeUpAllPlayers() {
        this.get().players().stream()
                .map(player -> new ServerPlayerWrapper(player))
                .filter(ServerPlayerWrapper::isSleeping)
                .forEach(player -> player.get().stopSleepInBed(false, false));
    }

    /** Ticks all loaded block entities in this level. */
    public void tickBlockEntities() {
        try {
            Method tickBlockEntitiesMethod = ReflectionUtil.findMethod(Level.class, "tickBlockEntities");

            tickBlockEntitiesMethod.invoke(get());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            BetterDays.LOGGER.warn("Error ticking block entities.", e);
        }
    }

    /**
     * {@return true if {@code level} is a derived level}
     * @param level  the level to check
     */
    public static boolean isDerived(LevelAccessor level) {
        return level != null && level.getLevelData().getClass() == derivedLevelDataClass;
    }

    /**
     * {@return true if {@code level} is an instance of a server level}
     * @param level  the level to check
     */
    public static boolean isServerLevel(LevelAccessor level) {
        return level != null && level.getClass() == levelClass;
    }

}
