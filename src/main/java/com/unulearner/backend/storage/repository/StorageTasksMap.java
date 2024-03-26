package com.unulearner.backend.storage.repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.exceptions.StorageServiceException;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.tasks.StorageTask;

@Service
public class StorageTasksMap {
    private final Map<UUID, ScheduledFuture<?>> taskRemovalHashMap;
    private final ScheduledExecutorService taskRemovalScheduler;
    private final StorageProperties storageProperties;
    private final Map<UUID, StorageTask> taskHashMap;
    private final Integer taskTimeOutInSeconds;

    public StorageTasksMap(StorageProperties storageProperties) {
        this.taskRemovalScheduler = Executors.newScheduledThreadPool(1);
        this.taskRemovalHashMap = new ConcurrentHashMap<>();
        this.taskHashMap = new ConcurrentHashMap<>();
        this.storageProperties = storageProperties;

        this.taskTimeOutInSeconds = this.storageProperties.getTaskTimeOut();
    }

    public UUID addStorageTask(StorageTask task) {
        UUID taskUUID = null;

        do {
            taskUUID = UUID.randomUUID();
        } while (this.taskHashMap.get(taskUUID) != null || this.taskRemovalHashMap.get(taskUUID) != null);

        this.taskHashMap.put(taskUUID, task);
        return taskUUID;
    }

    public StorageTask getStorageTask(UUID taskUUID) throws Exception {
        String errorMessage = null;
        ScheduledFuture<?> scheduledFuture = this.taskRemovalHashMap.get(taskUUID);

        if (scheduledFuture == null) {
            errorMessage = "Task ID '%s' is invalid!".formatted(taskUUID.toString());
            throw new StorageServiceException(errorMessage);
        }

        scheduledFuture.cancel(false);
        return this.taskHashMap.get(taskUUID);
    }

    public Integer scheduleStorageTaskRemoval(UUID taskUUID) {
        ScheduledFuture<?> scheduledFuture = this.taskRemovalHashMap.get(taskUUID);

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            this.taskRemovalHashMap.remove(taskUUID);
        }

        ScheduledFuture<?> newScheduledFuture = this.taskRemovalScheduler.schedule(() -> {
            this.taskHashMap.remove(taskUUID);
            this.taskRemovalHashMap.remove(taskUUID);
        }, this.taskTimeOutInSeconds, TimeUnit.SECONDS);

        this.taskRemovalHashMap.put(taskUUID, newScheduledFuture);
        return this.taskTimeOutInSeconds;
    }

    public Integer removeStorageTask(UUID taskUUID) {
        ScheduledFuture<?> scheduledFuture = this.taskRemovalHashMap.get(taskUUID);

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        this.taskRemovalHashMap.remove(taskUUID);
        this.taskHashMap.remove(taskUUID);
        return null;
    }
}
