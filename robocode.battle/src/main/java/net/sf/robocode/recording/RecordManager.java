/*
 * Copyright (c) 2001-2023 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://robocode.sourceforge.io/license/epl-v10.html
 */
package net.sf.robocode.recording;


import net.sf.robocode.battle.events.BattleEventDispatcher;
import net.sf.robocode.battle.snapshot.BulletSnapshot;
import net.sf.robocode.battle.snapshot.RobotSnapshot;
import net.sf.robocode.battle.snapshot.TurnSnapshot;
import net.sf.robocode.io.FileUtil;
import net.sf.robocode.io.Logger;
import static net.sf.robocode.io.Logger.logError;

import net.sf.robocode.serialization.*;
import net.sf.robocode.settings.ISettingsManager;
import net.sf.robocode.version.IVersionManager;
import robocode.BattleResults;
import robocode.BattleRules;
import robocode.control.snapshot.IBulletSnapshot;
import robocode.control.snapshot.IRobotSnapshot;
import robocode.control.snapshot.IScoreSnapshot;
import robocode.control.snapshot.ITurnSnapshot;

import java.io.*;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


/**
 * @author Pavel Savara (original)
 */
public class RecordManager implements IRecordManager {
    protected final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
    protected final Calendar calendar = Calendar.getInstance();
    protected static final Charset utf8 = StandardCharsets.UTF_8;

    protected final ISettingsManager properties;

    protected File tempFile;
    protected BattleRecorder recorder;
    protected final IVersionManager versionManager;

    protected BattleRecordInfo recordInfo;
    private FileOutputStream fileWriteStream;
    private BufferedOutputStream bufferedWriteStream;
    private ObjectOutputStream objectWriteStream;

    private FileInputStream fileReadStream;
    private BufferedInputStream bufferedReadStream;
    private ObjectInputStream objectReadStream;

    public RecordManager(ISettingsManager properties, IVersionManager versionManager) { // NO_UCD (unused code)
        this.properties = properties;
        recorder = new BattleRecorder(this, properties);
        this.versionManager = versionManager;
    }

    @Deprecated
    protected void finalize() throws Throwable {
        try {
            cleanup();
        } finally {
            super.finalize();
        }
    }

    private void cleanup() {
        cleanupStreams();
        if (tempFile != null && tempFile.exists()) {
            deleteTempFile();
            tempFile = null;
        }
        recordInfo = null;
    }

    void cleanupStreams() {
        FileUtil.cleanupStream(objectWriteStream);
        objectWriteStream = null;
        FileUtil.cleanupStream(bufferedWriteStream);
        bufferedWriteStream = null;
        FileUtil.cleanupStream(fileWriteStream);
        fileWriteStream = null;

        FileUtil.cleanupStream(objectReadStream);
        objectReadStream = null;
        FileUtil.cleanupStream(bufferedReadStream);
        bufferedReadStream = null;
        FileUtil.cleanupStream(fileReadStream);
        fileReadStream = null;
    }

    public void attachRecorder(BattleEventDispatcher battleEventDispatcher) {
        recorder.attachRecorder(battleEventDispatcher);
    }

    public void detachRecorder() {
        recorder.detachRecorder();
    }

    private void deleteTempFile(){
        try{
            Files.delete(tempFile.toPath());
        } catch(IOException e){
            Logger.logError("Could not delete temp file");
        }
    }

    private void createTempFile() {
        try {
            if (tempFile == null) {
                tempFile = File.createTempFile("robocode-battle-records", ".tmp");
                tempFile.deleteOnExit();
            } else {
                deleteTempFile();
                if (!tempFile.createNewFile()) {
                    throw new IOException("Temp file creation failed");
                }
            }
        } catch (IOException e) {
            logError(e);
            throw new RuntimeException("Temp file creation failed", e);
        }
    }

    void prepareInputStream() {
        try {
            fileReadStream = new FileInputStream(tempFile);
            bufferedReadStream = new BufferedInputStream(fileReadStream);
            objectReadStream = new ObjectInputStream(bufferedReadStream);
        } catch (IOException e) {
            logError(e);
            fileReadStream = null;
            bufferedReadStream = null;
            objectReadStream = null;
        }
    }

    ITurnSnapshot readSnapshot() {
        if (objectReadStream == null) {
            return null;
        }
        try {
            // TODO implement seek to currentTime, warn you. turns don't have same size in bytes
            return (ITurnSnapshot) objectReadStream.readObject();
        } catch (Exception e) {
            logError(e);
            return null;
        }
    }

    public void loadRecord(String recordFilename, BattleRecordFormat format) {
        createTempFile();

        try (
                FileInputStream fis = new FileInputStream(recordFilename);
                BufferedInputStream bis = new BufferedInputStream(fis, 1024 * 1024);
                ZipInputStream zis = (format == BattleRecordFormat.BINARY_ZIP || format == BattleRecordFormat.XML_ZIP) ? new ZipInputStream(bis) : null;
                FileOutputStream fos = (format == BattleRecordFormat.BINARY || format == BattleRecordFormat.BINARY_ZIP || format == BattleRecordFormat.XML_ZIP || format == BattleRecordFormat.XML) ? new FileOutputStream(tempFile) : null;
                BufferedOutputStream bos = (fos != null) ? new BufferedOutputStream(fos, 1024 * 1024) : null;
                ObjectOutputStream oos = (bos != null && (format == BattleRecordFormat.BINARY || format == BattleRecordFormat.BINARY_ZIP)) ? new ObjectOutputStream(bos) : null
        ) {
            // Extracted ternary operation for ObjectInputStream initialization
            ObjectInputStream ois = null;
            if (format == BattleRecordFormat.BINARY) {
                ois = new ObjectInputStream(bis);
            } else if (format == BattleRecordFormat.BINARY_ZIP) {
                ois = new ObjectInputStream(zis);
            }

            // Extracted ternary operation for InputStream initialization
            InputStream xis = null;
            if (format == BattleRecordFormat.XML_ZIP) {
                xis = zis;
            } else if (format == BattleRecordFormat.XML) {
                xis = bis;
            }

            if (format == BattleRecordFormat.BINARY || format == BattleRecordFormat.BINARY_ZIP) {
                recordInfo = (BattleRecordInfo) ois.readObject();
                if (recordInfo.getTurnsInRounds() != null) {
                    try (ObjectOutputStream finalOos = oos) {
                        for (int i = 0; i < recordInfo.getTurnsInRounds().length; i++) {
                            for (int j = recordInfo.getTurnsInRounds()[i] - 1; j >= 0; j--) {
                                try {
                                    ITurnSnapshot turn = (ITurnSnapshot) ois.readObject();
                                    finalOos.writeObject(turn);
                                } catch (ClassNotFoundException e) {
                                    logError(e);
                                }
                            }
                        }
                    }
                }
            } else if (format == BattleRecordFormat.XML || format == BattleRecordFormat.XML_ZIP) {
                final RecordRoot root = new RecordRoot();
                try (ObjectOutputStream xmlOos = new ObjectOutputStream(bos)) {
                    root.oos = xmlOos;
                    XmlReader.deserialize(xis, root);
                    if (root.lastException != null) {
                        logError(root.lastException);
                    }
                    recordInfo = root.recordInfo;
                }
            }
        } catch (IOException e) {
            logError(e);
            createTempFile();
            recordInfo = null;
        } catch (ClassNotFoundException e) {
            if (e.getMessage().contains("robocode.recording.BattleRecordInfo")) {
                Logger.logError("Sorry, backward compatibility with record from version 1.6 is not provided.");
            } else {
                logError(e);
            }
            createTempFile();
            recordInfo = null;
        }
    }

    private static class RecordRoot implements IXmlSerializable {

        public RecordRoot() {
            me = this;
        }

        private ObjectOutputStream oos;
        private IOException lastException;
        public final RecordRoot me;
        private BattleRecordInfo recordInfo;

        public void writeXml(XmlWriter writer, SerializableOptions options) {
        }

        public XmlReader.Element readXml(XmlReader reader) {
            return reader.expect("record", innerReader -> {

                final XmlReader.Element element = (new BattleRecordInfo()).readXml(innerReader);

                innerReader.expect("recordInfo", new XmlReader.ElementClose() {
                    public IXmlSerializable read(XmlReader reader1) {
                        recordInfo = (BattleRecordInfo) element.read(reader1);
                        return recordInfo;
                    }

                    public void close() {
                        innerReader.getContext().put("robots", recordInfo.getRobotCount());
                    }
                });

                innerReader.expect("turns", new XmlReader.ListElement() {
                    public IXmlSerializable read(XmlReader reader1) {
                        // prototype
                        return new TurnSnapshot();
                    }

                    public void add(IXmlSerializable child) {
                        try {
                            me.oos.writeObject(child);
                        } catch (IOException e) {
                            me.lastException = e;
                        }
                    }

                    public void close() {
                    }
                });

                return me;
            });
        }
    }

    public void saveRecord(String recordFilename, BattleRecordFormat format, SerializableOptions options) {
        if (recordInfo.getTurnsInRounds() == null) {
            return;
        }
        if (format == BattleRecordFormat.BINARY_ZIP || format == BattleRecordFormat.BINARY) {
            saveBinRecord(recordFilename, format, options);
        } else if (format == BattleRecordFormat.XML_ZIP || format == BattleRecordFormat.XML) {
            saveXmlRecord(recordFilename, format, options);
        } else if (format == BattleRecordFormat.CSV) {
            saveCsvRecord(recordFilename, options);
        }
    }

    private void saveBinRecord(String recordFilename, BattleRecordFormat format, SerializableOptions options) {

        try (FileOutputStream fos = new FileOutputStream(recordFilename);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 1024 * 1024);
            ZipOutputStream zos = new ZipOutputStream(bos)) {

            boolean isZip = format == BattleRecordFormat.BINARY_ZIP;
            if (isZip) {
                zos.putNextEntry(new ZipEntry(dateFormat.format(calendar.getTime()) + "-robocode.br"));
            }
            ObjectOutputStream oos = isZip
                    ? new ObjectOutputStream(zos)
                    : new ObjectOutputStream(bos);

            oos.writeObject(recordInfo);

            provideTurns((turn) -> {
                try {
                    TurnSnapshot t = (TurnSnapshot) turn;
                    t.stripDetails(options);
                    oos.writeObject(turn);
                } catch (IOException e) {
                    logError(e);
                }
            });

        } catch (IOException | ClassNotFoundException e) {
            logError(e);
            recorder = new BattleRecorder(this, properties);
            createTempFile();
        }
    }

    private void saveXmlRecord(String recordFilename, BattleRecordFormat format, SerializableOptions options) {
        if (recordInfo.getTurnsInRounds() == null) {
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(recordFilename);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 1024 * 1024);
            ZipOutputStream zos = new ZipOutputStream(bos)) {

            boolean isZip = format == BattleRecordFormat.XML_ZIP;
            if (isZip) {
                zos.putNextEntry(new ZipEntry(dateFormat.format(calendar.getTime()) + "-robocode.xml"));
            }
            OutputStreamWriter osw = isZip
                    ? new OutputStreamWriter(zos, utf8)
                    : new OutputStreamWriter(bos, utf8);
            XmlWriter xwr = isZip
                    ? new XmlWriter(osw, false)
                    : new XmlWriter(osw, true);

            xwr.startDocument();
            xwr.startElement("record");
            xwr.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            if (options.shortAttributes) {
                xwr.writeAttribute("xsi:noNamespaceSchemaLocation", "battleRecordS.xsd");
            } else {
                xwr.writeAttribute("xsi:noNamespaceSchemaLocation", "battleRecord.xsd");
            }
            recordInfo.writeXml(xwr, options);
            xwr.startElement("turns");

            provideTurns((turn) -> {
                try {
                    IXmlSerializable t = (IXmlSerializable) turn;
                    t.writeXml(xwr, options);
                } catch (IOException e) {
                    logError(e);
                }
            });

            xwr.endElement(); // turns
            xwr.endElement(); // record
            osw.flush();
            bos.flush();
            fos.flush();

        } catch (IOException | ClassNotFoundException e) {
            logError(e);
            recorder = new BattleRecorder(this, properties);
            createTempFile();
        }
    }

    protected void saveCsvRecord(String recordFilename, SerializableOptions options) {
        try (
                FileOutputStream fosResults = new FileOutputStream(recordFilename + ".results.csv");
                FileOutputStream fosRounds = new FileOutputStream(recordFilename + ".rounds.csv");
                FileOutputStream fosRobots = new FileOutputStream(recordFilename + ".robots.csv");
                FileOutputStream fosBullets = new FileOutputStream(recordFilename + ".bullets.csv")) {
            generateCsvRecord(fosResults, fosRounds, fosRobots, fosBullets, options, null);
        } catch (IOException | ClassNotFoundException e) {
            logError(e);
            recorder = new BattleRecorder(this, properties);
            createTempFile();
        }
    }

    public void generateCsvRecord(OutputStream fosResults, OutputStream fosRounds, OutputStream fosRobots, OutputStream fosBullets, SerializableOptions options, CheckedConsumer<ITurnSnapshot> extension) throws IOException, ClassNotFoundException {

        String version = versionManager.getVersion();

        try (
                BufferedOutputStream bosResults = new BufferedOutputStream(fosResults, 1024 * 1024);
                OutputStreamWriter oswResults = new OutputStreamWriter(bosResults, utf8);
                BufferedOutputStream bosRounds = new BufferedOutputStream(fosRounds, 1024 * 1024);
                OutputStreamWriter oswRounds = new OutputStreamWriter(bosRounds, utf8);
                BufferedOutputStream bosRobots = new BufferedOutputStream(fosRobots, 1024 * 1024);
                OutputStreamWriter oswRobots = new OutputStreamWriter(bosRobots, utf8);
                BufferedOutputStream bosBullets = new BufferedOutputStream(fosBullets, 1024 * 1024);
                OutputStreamWriter oswBullets = new OutputStreamWriter(bosBullets, utf8)
        ) {
            CsvWriter cwrResults = new CsvWriter(oswResults, false);
            cwrResults.startDocument("version,battleId,roundsCount,robotCount,battlefieldWidth,battlefieldHeight,gunCoolingRate,inactivityTime,teamLeaderName,rank,score,survival,lastSurvivorBonus,bulletDamage,bulletDamageBonus,ramDamage,ramDamageBonus,firsts,seconds,thirds");

            CsvWriter cwrRounds = new CsvWriter(oswRounds, false);
            cwrRounds.startDocument("version,battleId,roundIndex,robotCount,battlefieldWidth,battlefieldHeight,gunCoolingRate,inactivityTime,turnsInRound");

            CsvWriter cwrRobots = new CsvWriter(oswRobots, false);
            cwrRobots.startDocument("version,battleId,roundIndex,turnIndex,robotIndex,robotName,energy,x,y,bodyHeading,gunHeading,radarHeading,gunHeat,velocity,score,survivalScore,bulletDamageScore,bulletKillBonus,rammingDamageScore,rammingKillBonus");

            CsvWriter cwrBullets = new CsvWriter(oswBullets, false);
            cwrBullets.startDocument("version,battleId,roundIndex,turnIndex,bulletId,ownerIndex,ownerName,state,heading,x,y,victimIndex,victimName");
            int roundsCount = recordInfo.getTurnsInRounds().length;


            // loop over robots
            for (BattleResults result : recordInfo.getResults()) {
                BattleRecordInfo.BattleResultsWrapper wrapper = new BattleRecordInfo.BattleResultsWrapper(result);
                cwrResults.writeValue(version);
                cwrResults.writeValue(recordInfo.getBattleId().toString());
                cwrResults.writeValue(roundsCount);
                cwrResults.writeValue(recordInfo.getRobotCount());
                cwrResults.writeValue(recordInfo.getBattleRules().getBattlefieldWidth());
                cwrResults.writeValue(recordInfo.getBattleRules().getBattlefieldHeight());
                cwrResults.writeValue(recordInfo.getBattleRules().getGunCoolingRate(), options.trimPrecision);
                cwrResults.writeValue(recordInfo.getBattleRules().getInactivityTime());
                cwrResults.writeValue(wrapper.getTeamLeaderName());
                cwrResults.writeValue(wrapper.getRank());
                cwrResults.writeValue(wrapper.getScore(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getSurvival(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getLastSurvivorBonus(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getBulletDamage(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getBulletDamageBonus(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getRamDamage(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getRamDamageBonus(), options.trimPrecision);
                cwrResults.writeValue(wrapper.getFirsts());
                cwrResults.writeValue(wrapper.getSeconds());
                cwrResults.writeValue(wrapper.getThirds());

                cwrResults.endLine();
            }

            // loop over rounds
            for (int round = 0; round < roundsCount; round++) {
                int turnsInRound = recordInfo.getTurnsInRounds()[round];
                cwrRounds.writeValue(version);
                cwrRounds.writeValue(recordInfo.getBattleId().toString());
                cwrRounds.writeValue(round);
                cwrRounds.writeValue(recordInfo.getRobotCount());
                cwrRounds.writeValue(recordInfo.getBattleRules().getBattlefieldWidth());
                cwrRounds.writeValue(recordInfo.getBattleRules().getBattlefieldHeight());
                cwrRounds.writeValue(recordInfo.getBattleRules().getGunCoolingRate(), options.trimPrecision);
                cwrRounds.writeValue(recordInfo.getBattleRules().getInactivityTime());
                cwrRounds.writeValue(turnsInRound);
                cwrRounds.endLine();
            }

            provideTurns((turn) -> {
                if (extension != null) {
                    extension.accept(turn);
                }

                IRobotSnapshot[] robots = turn.getRobots();
                for (IRobotSnapshot robot : robots) {
                    RobotSnapshot robotSnapshot = (RobotSnapshot) robot;
                    IScoreSnapshot scoreSnapshot = robotSnapshot.getScoreSnapshot();
                    cwrRobots.writeValue(version);
                    cwrRobots.writeValue(recordInfo.getBattleId().toString());
                    cwrRobots.writeValue(turn.getRound());
                    cwrRobots.writeValue(turn.getTurn());
                    cwrRobots.writeValue(robotSnapshot.getRobotIndex());
                    cwrRobots.writeValue(robotSnapshot.getName());
                    cwrRobots.writeValue(robotSnapshot.getEnergy(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getX(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getY(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getBodyHeading(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getGunHeading(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getRadarHeading(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getGunHeat(), options.trimPrecision);
                    cwrRobots.writeValue(robotSnapshot.getVelocity(), options.trimPrecision);
                    cwrRobots.writeValue(scoreSnapshot.getCurrentScore(), options.trimPrecision);
                    cwrRobots.writeValue(scoreSnapshot.getCurrentSurvivalScore(), options.trimPrecision);
                    cwrRobots.writeValue(scoreSnapshot.getCurrentBulletDamageScore(), options.trimPrecision);
                    cwrRobots.writeValue(scoreSnapshot.getCurrentBulletKillBonus(), options.trimPrecision);
                    cwrRobots.writeValue(scoreSnapshot.getCurrentRammingDamageScore(), options.trimPrecision);
                    cwrRobots.writeValue(scoreSnapshot.getCurrentRammingKillBonus(), options.trimPrecision);
                    cwrRobots.endLine();
                }
                for (IBulletSnapshot bullet : turn.getBullets()) {
                    BulletSnapshot bulletSnapshot = (BulletSnapshot) bullet;
                    IRobotSnapshot owner = robots[bulletSnapshot.getOwnerIndex()];
                    cwrBullets.writeValue(version);
                    cwrBullets.writeValue(recordInfo.getBattleId().toString());
                    cwrBullets.writeValue(turn.getRound());
                    cwrBullets.writeValue(turn.getTurn());
                    cwrBullets.writeValue(bulletSnapshot.getBulletId());
                    cwrBullets.writeValue(bulletSnapshot.getOwnerIndex());
                    cwrBullets.writeValue(owner.getName());
                    cwrBullets.writeValue(bulletSnapshot.getState().toString());
                    cwrBullets.writeValue(bulletSnapshot.getHeading(), options.trimPrecision);
                    cwrBullets.writeValue(bulletSnapshot.getPaintX(), options.trimPrecision);
                    cwrBullets.writeValue(bulletSnapshot.getPaintY(), options.trimPrecision);
                    cwrBullets.writeValue(bulletSnapshot.getVictimIndex());
                    cwrBullets.writeValue(bulletSnapshot.getVictimIndex() != -1 ? robots[bulletSnapshot.getVictimIndex()].getName() : null);
                    cwrBullets.endLine();
                }
            });

        }
    }

    @Override
    public void provideTurns(CheckedConsumer<ITurnSnapshot> writeTurn) throws IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(tempFile);
             BufferedInputStream bis = new BufferedInputStream(fis, 1024 * 1024);
             ObjectInputStream ois = new ObjectInputStream(bis)) {

            for (int i = 0; i < recordInfo.getTurnsInRounds().length; i++) {
                if (recordInfo.getTurnsInRounds()[i] > 0) {
                    for (int j = 0; j <= recordInfo.getTurnsInRounds()[i] - 1; j++) {
                        TurnSnapshot turn = (TurnSnapshot) ois.readObject();

                        if (j != turn.getTurn()) {
                            throw new IllegalStateException("Something rotten");
                        }

                        writeTurn.accept(turn);
                    }
                }
            }

        }
    }

    public boolean hasRecord() {
        return recordInfo != null;
    }

    void createRecordInfo(BattleRules rules, int numRobots, UUID battleId) {
        try {
            createTempFile();

            fileWriteStream = new FileOutputStream(tempFile);
            bufferedWriteStream = new BufferedOutputStream(fileWriteStream, 1024 * 1024);
            objectWriteStream = new ObjectOutputStream(bufferedWriteStream);
        } catch (IOException e) {
            logError(e);
        }

        recordInfo = new BattleRecordInfo();
        recordInfo.setBattleId(battleId);
        recordInfo.setRobotCount(numRobots);
        recordInfo.setBattleRules(rules);
        recordInfo.setTurnsInRounds(new Integer[rules.getNumRounds()]);
        for (int i = 0; i < rules.getNumRounds(); i++) {
            recordInfo.getTurnsInRounds()[i] = 0;
        }
    }

    void updateRecordInfoResults(List<BattleResults> results) {
        recordInfo.setResults(results);
    }

    void writeTurn(ITurnSnapshot turn, int round, int time) {
        try {
            if (time != recordInfo.getTurnsInRounds()[round]) {
                throw new IllegalStateException("Something rotten");
            }
            if (time == 0) {
                objectWriteStream.reset();
            }
            recordInfo.getTurnsInRounds()[round]++;
            recordInfo.setRoundsCount(round + 1);
            objectWriteStream.writeObject(turn);
        } catch (IOException e) {
            logError(e);
        }
    }
}
