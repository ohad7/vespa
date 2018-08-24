package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.RunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.deployment.RunLog;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stores logs in bite-sized chunks using a {@link CuratorDb}, and flushes to a
 * {@link com.yahoo.vespa.hosted.controller.api.integration.RunDataStore} when the log is final.
 *
 * @author jonmv
 */
public class BufferedLogStore {

    static final int chunkSize = 1 << 17;

    private final CuratorDb buffer;
    private final RunDataStore store;
    private final LogSerializer logSerializer = new LogSerializer();

    public BufferedLogStore(CuratorDb buffer, RunDataStore store) {
        this.buffer = buffer;
        this.store = store;
    }

    /** Appends to the log of the given, active run, reassigning IDs as counted here, and converting to Vespa log levels. */
    public void append(ApplicationId id, JobType type, Step step, List<LogEntry> entries) {
        if (entries.isEmpty())
            return;

        // Start a new chunk if the previous one is full, or if none have been written yet.
        // The id of a chunk is the id of the first entry in it.
        long lastEntryId = buffer.readLastLogEntryId(id, type).orElse(-1L);
        long lastChunkId = buffer.getLogChunkIds(id, type).max().orElse(0);
        byte[] emptyChunk = "[]".getBytes();
        byte[] lastChunk = buffer.readLog(id, type, lastChunkId).orElse(emptyChunk);
        if (lastChunk.length > chunkSize) {
            lastChunkId = lastEntryId + 1;
            lastChunk = emptyChunk;
        }
        Map<Step, List<LogEntry>> log = logSerializer.fromJson(lastChunk, -1);
        List<LogEntry> stepEntries = log.computeIfAbsent(step, __ -> new ArrayList<>());
        for (LogEntry entry : entries)
            stepEntries.add(new LogEntry(++lastEntryId, entry.at(), entry.level(), entry.message()));

        buffer.writeLog(id, type, lastChunkId, logSerializer.toJson(log));
        buffer.writeLastLogEntryId(id, type, lastEntryId);
    }

    /** Reads all log entries after the given threshold, from the buffered log, i.e., for an active run. */
    public RunLog readActive(ApplicationId id, JobType type, long after) {
        return buffer.readLastLogEntryId(id, type).orElse(-1L) <= after
                ? RunLog.empty()
                : RunLog.of(readChunked(id, type, after));
    }

    /** Reads all log entries after the given threshold, from the stored log, i.e., for a finished run. */
    public Optional<RunLog> readFinished(RunId id, long after) {
        return store.get(id).map(json -> RunLog.of(logSerializer.fromJson(json, after)));
    }

    /** Writes the buffered log of the now finished run to the long-term store, and clears the buffer. */
    public void flush(RunId id) {
        store.put(id, logSerializer.toJson(readChunked(id.application(), id.type(), -1)));
        buffer.deleteLog(id.application(), id.type());
    }

    /** Deletes all logs for the given application. */
    public void delete(ApplicationId id) {
        for (JobType type : JobType.values())
            buffer.deleteLog(id, type);
        store.delete(id);
    }

    private Map<Step, List<LogEntry>> readChunked(ApplicationId id, JobType type, long after) {
        long[] chunkIds = buffer.getLogChunkIds(id, type).toArray();
        int firstChunk = chunkIds.length;
        while (firstChunk > 0 && chunkIds[--firstChunk] > after + 1);
        return logSerializer.fromJson(Arrays.stream(chunkIds, firstChunk, chunkIds.length)
                                            .mapToObj(chunkId -> buffer.readLog(id, type, chunkId))
                                            .filter(Optional::isPresent).map(Optional::get)
                                            .collect(Collectors.toList()),
                                      after);
    }

}
