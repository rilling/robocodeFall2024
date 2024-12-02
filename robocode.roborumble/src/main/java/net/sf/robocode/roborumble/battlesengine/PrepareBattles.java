/*
 * Copyright (c) 2001-2023 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://robocode.sourceforge.io/license/epl-v10.html
 */
package net.sf.robocode.roborumble.battlesengine;

import static net.sf.robocode.roborumble.util.ExcludesUtil.*;
import static net.sf.robocode.roborumble.util.PropertiesUtil.getProperties;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * PrepareBattles is used for preparing battles.
 * Controlled by properties files.
 *
 * @author Albert Perez (original)
 * @author Flemming N. Larsen (contributor)
 * @author Jerome Lavigne (contributor)
 */
public class PrepareBattles {

    private final String botsrepository;
    private final Path participantsfile;
    private final BattlesFile battlesfile;
    private final int numbattles;
    private final CompetitionsSelector size;
    private final String runonly;
    private final Properties generalratings;
    private final Properties miniratings;
    private final Properties microratings;
    private final Properties nanoratings;
    private final Path priority;
    private final int prioritynum;
    private final int meleebots;

    private static final Random RANDOM = new Random();

    public PrepareBattles(String propertiesfile) {
        Properties parameters = getProperties(propertiesfile);

        botsrepository = parameters.getProperty("BOTSREP", "");
        participantsfile = sanitizePath(parameters.getProperty("PARTICIPANTSFILE", ""));
        battlesfile = new BattlesFile(sanitizePath(parameters.getProperty("INPUT", "")).toString());
        numbattles = Integer.parseInt(parameters.getProperty("NUMBATTLES", "100"));
        String sizesfile = sanitizePath(parameters.getProperty("CODESIZEFILE", "")).toString();

        size = new CompetitionsSelector(sizesfile, botsrepository);
        runonly = parameters.getProperty("RUNONLY", "GENERAL");
        prioritynum = Integer.parseInt(parameters.getProperty("BATTLESPERBOT", "500"));
        meleebots = Integer.parseInt(parameters.getProperty("MELEEBOTS", "10"));
        generalratings = getProperties(parameters.getProperty("RATINGS.GENERAL", ""));
        miniratings = getProperties(parameters.getProperty("RATINGS.MINIBOTS", ""));
        microratings = getProperties(parameters.getProperty("RATINGS.MICROBOTS", ""));
        nanoratings = getProperties(parameters.getProperty("RATINGS.NANOBOTS", ""));
        priority = sanitizePath(parameters.getProperty("PRIORITYBATTLESFILE", ""));

        setExcludes(parameters); // Prepare exclude filters
    }

    private Path sanitizePath(String path) {
        try {
            Path basePath = Paths.get(".").toRealPath();
            Path resolvedPath = basePath.resolve(path).normalize();

            if (!resolvedPath.startsWith(basePath)) {
                throw new IOException("Path traversal attempt detected: " + path);
            }
            return resolvedPath;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    public boolean createBattlesList() {
        List<String> names = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(participantsfile)) {
            String participant;

            while ((participant = br.readLine()) != null) {
                if (participant.contains(",")) {
                    String name = participant.substring(0, participant.indexOf(","));

                    if (isExcluded(name)) {
                        continue;
                    }

                    String jar = name.replace(' ', '_') + ".jar";
                    boolean exists = Files.exists(Paths.get(botsrepository, jar));

                    if (exists && isEligibleForRunOnly(name)) {
                        names.add(name);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Participants file not found. Aborting...");
            e.printStackTrace();
            return false;
        }

        if (!battlesfile.openWrite()) {
            return false;
        }

        int count = 0;
        while (count < numbattles && names.size() > 1) {
            int bot1 = RANDOM.nextInt(names.size());
            int bot2 = RANDOM.nextInt(names.size());

            if (bot1 != bot2) {
                battlesfile.writeBattle(new RumbleBattle(new String[]{names.get(bot1), names.get(bot2)}, runonly));
                count++;
            }
        }
        battlesfile.closeWrite();
        return true;
    }

    public boolean createSmartBattlesList() {
        List<String> namesAll = new ArrayList<>();
        List<String> namesMini = new ArrayList<>();
        List<String> namesMicro = new ArrayList<>();
        List<String> namesNano = new ArrayList<>();
        List<String> namesNoRanking = new ArrayList<>();
        List<String> priorityBattles = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(participantsfile)) {
            String participant;

            while ((participant = br.readLine()) != null) {
                if (participant.contains(",")) {
                    String name = participant.substring(0, participant.indexOf(","));

                    if (isExcluded(name)) {
                        continue;
                    }

                    String jar = name.replace(' ', '_') + ".jar";
                    boolean exists = Files.exists(Paths.get(botsrepository, jar));

                    if (exists) {
                        namesAll.add(name);
                        addCompetitorToCategories(name, namesMini, namesMicro, namesNano);
                        if (!isRobotInRatings(name)) {
                            namesNoRanking.add(name);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Participants file not found. Aborting...");
            e.printStackTrace();
            return false;
        }

        if (!battlesfile.openWrite()) {
            return false;
        }

        int count = 0;
        while (count < numbattles && namesAll.size() > 1) {
            String[] bots = getRandomBots(namesAll, namesAll);
            battlesfile.writeBattle(new RumbleBattle(bots, runonly));
            count++;
        }
        battlesfile.closeWrite();
        return true;
    }

    public boolean createMeleeBattlesList() {
        List<String> namesAll = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(participantsfile)) {
            String participant;

            while ((participant = br.readLine()) != null) {
                if (participant.contains(",")) {
                    String name = participant.substring(0, participant.indexOf(","));

                    if (isExcluded(name)) {
                        continue;
                    }

                    String jar = name.replace(' ', '_') + ".jar";
                    boolean exists = Files.exists(Paths.get(botsrepository, jar));

                    if (exists) {
                        namesAll.add(name);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Participants file not found. Aborting...");
            e.printStackTrace();
            return false;
        }

        if (!battlesfile.openWrite()) {
            return false;
        }

        int count = 0;
        while (count < numbattles && namesAll.size() >= meleebots) {
            String[] bots = getRandomMeleeBots(namesAll, meleebots);
            battlesfile.writeBattle(new RumbleBattle(bots, runonly));
            count++;
        }
        battlesfile.closeWrite();
        return true;
    }

    private void addCompetitorToCategories(String name, List<String> mini, List<String> micro, List<String> nano) {
        if (size.checkCompetitorForSize(name, 1500)) {
            mini.add(name);
        }
        if (size.checkCompetitorForSize(name, 750)) {
            micro.add(name);
        }
        if (size.checkCompetitorForSize(name, 250)) {
            nano.add(name);
        }
    }

    private String[] getRandomBots(List<String> list1, List<String> list2) {
        int bot1 = RANDOM.nextInt(list1.size());
        int bot2 = RANDOM.nextInt(list2.size());

        while (list1.get(bot1).equals(list2.get(bot2))) {
            bot1 = RANDOM.nextInt(list1.size());
            bot2 = RANDOM.nextInt(list2.size());
        }

        return new String[]{list1.get(bot1), list2.get(bot2)};
    }

    private String[] getRandomMeleeBots(List<String> names, int count) {
        Set<String> bots = new HashSet<>();
        while (bots.size() < count) {
            bots.add(names.get(RANDOM.nextInt(names.size())));
        }
        return bots.toArray(new String[0]);
    }

    private boolean isEligibleForRunOnly(String name) {
        return (runonly.equals("MINI") && size.checkCompetitorForSize(name, 1500))
                || (runonly.equals("MICRO") && size.checkCompetitorForSize(name, 750))
                || (runonly.equals("NANO") && size.checkCompetitorForSize(name, 250))
                || (!runonly.equals("MINI") && !runonly.equals("MICRO") && !runonly.equals("NANO"));
    }

    private boolean isRobotInRatings(String name) {
        String bot = name.replace(' ', '_');
        return generalratings.containsKey(bot)
                || miniratings.containsKey(bot)
                || microratings.containsKey(bot)
                || nanoratings.containsKey(bot);
    }
}
